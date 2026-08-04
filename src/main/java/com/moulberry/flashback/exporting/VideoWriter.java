package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.platform.NativeImage;
import org.jetbrains.annotations.Nullable;

import java.nio.FloatBuffer;
import java.util.function.Consumer;

public interface VideoWriter extends AutoCloseable {

    void encode(NativeImage src, @Nullable FloatBuffer audioBuffer);
    void finish(Consumer<String> wait);

    /**
     * Called repeatedly while {@link #encode} has to wait for the encoder to catch up.
     * Without this the client thread blocks silently inside the bounded queue, which looks
     * like a frozen game at the end of an export.
     */
    default void setWaitCallback(@Nullable Consumer<String> callback) {
    }

    /** Number of frames still buffered and waiting to be encoded. */
    default int pendingFrameCount() {
        return 0;
    }

    default void close() {
    }

}
