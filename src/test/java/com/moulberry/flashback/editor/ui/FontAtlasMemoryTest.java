package com.moulberry.flashback.editor.ui;

import imgui.moulberry90.ImFontConfig;
import imgui.moulberry90.ImFontGlyphRangesBuilder;
import imgui.moulberry90.ImGui;

import java.nio.file.Files;
import java.nio.file.Path;

public final class FontAtlasMemoryTest {
    public static void main(String[] args) throws Exception {
        ImGui.createContext();
        var atlas = ImGui.getIO().getFonts();
        var memory = new FontAtlasMemory();
        try {
            for (int reload = 0; reload < 30; reload++) {
                atlas.clear();
                memory.close();
                var config = new ImFontConfig();
                try {
                    var builder = new ImFontGlyphRangesBuilder();
                    builder.addText("Hello 中文测试");
                    short[] ranges = builder.buildRanges();
                    var font = memory.addFont(atlas, load("inter-medium.ttf"), 16, config, ranges);
                    config.setMergeMode(true);
                    memory.addFont(atlas, load("materialiconsround-regular.otf"), 20, config,
                        new short[]{(short) 0xe04b, (short) 0xe04b, 0});
                    // The old JNI array overload leaves stale pointers across these collections.
                    System.gc();
                    memory.addFont(atlas, load("notosanssc-medium.ttf"), 20, config, ranges);
                    System.gc();
                    if (!atlas.build()) throw new AssertionError("Font build failed");
                    for (int codePoint : new int[]{'H', '中', 0xe04b}) {
                        var glyph = font.findGlyphNoFallback(codePoint);
                        if (glyph == null || glyph.ptr == 0) {
                            throw new AssertionError("Missing glyph: " + Integer.toHexString(codePoint));
                        }
                    }
                } finally {
                    config.destroy();
                }
            }
            System.out.println("PASS: 30 font reloads with GC preserve Latin, Chinese and icon glyphs");
        } finally {
            atlas.clear();
            memory.close();
            ImGui.destroyContext();
        }
    }

    private static byte[] load(String name) throws Exception {
        return Files.readAllBytes(Path.of("src/main/resources/assets/flashback", name));
    }
}
