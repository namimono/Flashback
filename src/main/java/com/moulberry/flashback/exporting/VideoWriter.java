package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.platform.NativeImage;
import org.jetbrains.annotations.Nullable;

import java.nio.FloatBuffer;
import java.util.function.Consumer;

public interface VideoWriter extends AutoCloseable {

    void encode(NativeImage src, @Nullable FloatBuffer audioBuffer);
    void finish(Consumer<String> wait);

    /**
     * Called repeatedly while {@link #encode} has to wait for the encoder to catch up. Without it
     * the caller blocks inside the bounded queue without ever getting a chance to redraw.
     */
    default void setWaitCallback(@Nullable Consumer<String> callback) {
    }

    /** Number of frames that have been submitted but not written out yet. */
    default int pendingFrameCount() {
        return 0;
    }

    default void close() {
    }

}
