package com.moulberry.flashback.editor.ui;

import imgui.moulberry90.ImFont;
import imgui.moulberry90.ImFontAtlas;
import imgui.moulberry90.ImFontConfig;
import imgui.moulberry90.ImGui;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

/** Owns the glyph ranges retained by ImGui until the atlas is cleared. */
final class FontAtlasMemory implements AutoCloseable {
    // ABI of the bundled imgui-moulberry90 native library (64-bit ImFontConfig).
    // Its array setters release JNI critical arrays before ImGui finishes using them.
    private static final int FONT_DATA_OFFSET = 0;
    private static final int GLYPH_RANGES_OFFSET = 56;
    private final List<ShortBuffer> ranges = new ArrayList<>();

    ImFont addFont(ImFontAtlas atlas, byte[] data, float size, ImFontConfig config, short[] glyphRanges) {
        if (Pointer.POINTER_SIZE != 8 || !ImGui.getVersion().startsWith("1.90")) {
            throw new IllegalStateException("FontAtlasMemory requires the bundled 64-bit ImGui 1.90 ABI");
        }
        ShortBuffer nativeRanges = MemoryUtil.memAllocShort(glyphRanges.length);
        ranges.add(nativeRanges);
        nativeRanges.put(glyphRanges).flip();
        ByteBuffer nativeData = MemoryUtil.memAlloc(data.length);
        try {
            nativeData.put(data).flip();
            config.setFontDataSize(data.length);
            config.setSizePixels(size);
            // AddFont copies unowned font bytes into atlas-owned storage synchronously.
            config.setFontDataOwnedByAtlas(false);
            MemoryUtil.memPutAddress(config.ptr + FONT_DATA_OFFSET, MemoryUtil.memAddress(nativeData));
            MemoryUtil.memPutAddress(config.ptr + GLYPH_RANGES_OFFSET, MemoryUtil.memAddress(nativeRanges));
            return atlas.addFont(config);
        } finally {
            MemoryUtil.memPutAddress(config.ptr + FONT_DATA_OFFSET, 0);
            MemoryUtil.memPutAddress(config.ptr + GLYPH_RANGES_OFFSET, 0);
            MemoryUtil.memFree(nativeData);
        }
    }

    /** Call only after clearing/destroying the atlas, which retains these pointers. */
    @Override
    public void close() {
        ranges.forEach(MemoryUtil::memFree);
        ranges.clear();
    }
}
