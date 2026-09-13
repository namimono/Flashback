import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/** Temporary, read-only probe for the named development client. No game-thread work is queued. */
public final class ExportHangProbe {
    private static final AtomicBoolean running = new AtomicBoolean();

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Class<?> type = target instanceof Class<?> cls ? cls : target.getClass();
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target instanceof Class<?> ? null : target);
    }

    public static void agentmain(String destination, Instrumentation instrumentation) throws Exception {
        Class<?> minecraft = null;
        Class<?> replayUI = null;
        Class<?> flashback = null;
        for (Class<?> type : instrumentation.getAllLoadedClasses()) {
            switch (type.getName()) {
                case "net.minecraft.client.Minecraft" -> minecraft = type;
                case "com.moulberry.flashback.editor.ui.ReplayUI" -> replayUI = type;
                case "com.moulberry.flashback.Flashback" -> flashback = type;
            }
        }
        if (minecraft == null || replayUI == null || flashback == null) {
            throw new IllegalStateException("Open a replay in the named development client first");
        }
        Thread renderThread = Thread.getAllStackTraces().keySet().stream()
            .filter(thread -> thread.getName().equals("Render thread")).findFirst().orElseThrow();
        Object game = minecraft.getMethod("getInstance").invoke(null);
        final Class<?> ui = replayUI;
        final Class<?> fb = flashback;
        Path output = Path.of(destination);
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("An export probe is already running");
        }
        var writer = Files.newBufferedWriter(output);
        Thread sampler = new Thread(() -> {
            try (var out = writer) {
                out.write("READY " + Instant.now() + " interval=100ms lifetime=15min\n");
                out.flush();
                long deadline = System.nanoTime() + 900_000_000_000L;
                long captureUntil = 0;
                String previous = "";
                while (System.nanoTime() < deadline && renderThread.isAlive()) {
                    long now = System.nanoTime();
                    boolean exporting = field(fb, "EXPORT_JOB") != null;
                    long finished = (long) field(ui, "exportFinishedMillis");
                    if (exporting || finished > 0) captureUntil = now + 2_000_000_000L;
                    Object screen = field(game, "screen");
                    Object overlay = field(game, "overlay");
                    String state = "job=" + exporting + " active=" + field(ui, "activeLastFrame")
                        + " finishedMillis=" + finished + " noRender=" + field(game, "noRender")
                        + " hideGui=" + field(field(game, "options"), "hideGui")
                        + " screen=" + (screen == null ? "null" : screen.getClass().getName())
                        + " overlay=" + (overlay == null ? "null" : overlay.getClass().getName());
                    if (!state.equals(previous) || now < captureUntil) {
                        out.write(Instant.now() + " " + state + "\n");
                        if (now < captureUntil) {
                            for (StackTraceElement frame : renderThread.getStackTrace()) {
                                out.write("  at " + frame + "\n");
                            }
                        }
                        out.flush();
                        previous = state;
                    }
                    Thread.sleep(100);
                }
                out.write("STOPPED " + Instant.now() + "\n");
            } catch (Throwable error) {
                try {
                    Files.writeString(output.resolveSibling(output.getFileName() + ".error"), error.toString());
                } catch (Exception ignored) {}
            } finally {
                running.set(false);
            }
        }, "Flashback export diagnostic sampler");
        sampler.setDaemon(true);
        sampler.start();
    }
}
