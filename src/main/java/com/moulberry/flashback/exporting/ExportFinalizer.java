package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.platform.NativeImage;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.combo_options.VideoContainer;
import com.moulberry.flashback.editor.ui.windows.ExportDoneWindow;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * Runs the tail end of an export on a background thread.
 * <p>
 * When the last frame has been rendered the encoder is usually still several dozen frames behind,
 * because the bounded queues in {@link VideoWriter} buffer whatever the encoder couldn't keep up
 * with. Waiting for that backlog on the client thread froze the whole game (including the export
 * progress overlay) until FFmpeg was done, which could take many seconds on slower disks.
 */
public final class ExportFinalizer {

    private ExportFinalizer() {
    }

    private static volatile @Nullable Task activeTask = null;
    private static boolean registeredShutdownHook = false;

    private static final class Task {
        private final VideoWriter writer;
        private final ExportSettings settings;
        private final @Nullable NativeImage thumbnail;
        private final double duration;
        private final long startMillis = System.currentTimeMillis();

        private Task(VideoWriter writer, ExportSettings settings, @Nullable NativeImage thumbnail, double duration) {
            this.writer = writer;
            this.settings = settings;
            this.thumbnail = thumbnail;
            this.duration = duration;
        }
    }

    /**
     * Takes ownership of the writer: it will be finished, closed and turned into an
     * {@link ExportDoneWindow} entry once the encoder has caught up.
     */
    public static void submit(VideoWriter writer, ExportSettings settings, @Nullable NativeImage thumbnail, double duration) {
        await(null, 0L);

        Task task = new Task(writer, settings, thumbnail, duration);
        activeTask = task;
        registerShutdownHook();

        Thread thread = new Thread(() -> runTask(task));
        thread.setName("Flashback Export Finalizer");
        thread.start();
    }

    public static boolean isFinalizing() {
        return activeTask != null;
    }

    /** Human readable progress, or null when nothing is being finalized. */
    public static @Nullable String getStatus() {
        Task task = activeTask;
        if (task == null) {
            return null;
        }

        String name = task.settings.name();
        String prefix = name == null ? "" : name + ": ";
        long seconds = Math.max(0, System.currentTimeMillis() - task.startMillis) / 1000;
        return prefix + "Saving video... " + task.writer.pendingFrameCount() + " frames left, " + seconds + "s";
    }

    /**
     * Blocks until the pending finalize (if any) is done.
     *
     * @param progress called every millisecond with {@link #getStatus}, so a caller on the client
     *                 thread can keep redrawing
     * @param timeoutMillis maximum time to wait, or 0 to wait forever
     */
    public static void await(@Nullable Consumer<String> progress, long timeoutMillis) {
        long start = System.currentTimeMillis();

        while (activeTask != null) {
            if (timeoutMillis > 0 && System.currentTimeMillis() - start > timeoutMillis) {
                Flashback.LOGGER.warn("Timed out after {} ms waiting for the export to finish encoding", timeoutMillis);
                return;
            }

            LockSupport.parkNanos("waiting for export to finish encoding", 1000000L);

            if (progress != null) {
                String status = getStatus();
                if (status != null) {
                    progress.accept(status);
                }
            }
        }
    }

    private static void runTask(Task task) {
        Path output = task.settings.output();
        boolean succeeded = false;

        try {
            long start = System.currentTimeMillis();
            task.writer.finish(reason -> {});
            Flashback.LOGGER.info("Export finalize: encoder finished in {} ms", System.currentTimeMillis() - start);
            succeeded = true;
        } catch (Throwable t) {
            Flashback.LOGGER.error("Failed to finish writing {}", output, t);
        } finally {
            try {
                task.writer.close();
            } catch (Throwable t) {
                Flashback.LOGGER.error("Failed to release the encoder for {}", output, t);
            }

            try {
                if (succeeded) {
                    publishFinishedExport(task);
                } else {
                    discardFailedExport(task);
                }
            } finally {
                Flashback.LOGGER.info("Export finalize: total {} ms", System.currentTimeMillis() - task.startMillis);
                activeTask = null;
            }
        }
    }

    private static void publishFinishedExport(Task task) {
        ExportDoneWindow.FinishedExportEntry entry;
        try {
            Path output = task.settings.output();

            long size = 0;
            if (task.settings.container() != VideoContainer.PNG_SEQUENCE && Files.exists(output) && Files.isRegularFile(output)) {
                size = Files.size(output);
            }

            entry = new ExportDoneWindow.FinishedExportEntry(task.settings, task.thumbnail, task.duration, size);
        } catch (IOException e) {
            Flashback.LOGGER.error("Failed to inspect the finished export", e);
            return;
        }

        Minecraft.getInstance().execute(() -> ExportDoneWindow.addFinishedExportEntry(entry));
    }

    private static void discardFailedExport(Task task) {
        if (task.thumbnail != null) {
            Minecraft.getInstance().execute(task.thumbnail::close);
        }

        if (task.settings.container() == VideoContainer.PNG_SEQUENCE) {
            return;
        }

        try {
            Files.deleteIfExists(task.settings.output());
        } catch (IOException ignored) {}
    }

    private static void registerShutdownHook() {
        if (registeredShutdownHook) {
            return;
        }
        registeredShutdownHook = true;

        // The encode thread isn't a daemon, but Minecraft can still exit through System.exit();
        // without this the container trailer would never be written and the file stays broken.
        Thread hook = new Thread(() -> await(null, 120000L));
        hook.setName("Flashback Export Finalizer Shutdown");
        Runtime.getRuntime().addShutdownHook(hook);
    }

}
