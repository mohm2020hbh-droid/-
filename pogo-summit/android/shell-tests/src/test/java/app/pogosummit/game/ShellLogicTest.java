package app.pogosummit.game;

import static org.junit.Assert.*;

import org.junit.Test;

public class ShellLogicTest {
    @Test public void onlyTheVirtualOriginIsReachable() {
        assertTrue(ShellLogic.isGameHost("appassets.pogosummit.app"));
        for (String h : new String[]{null, "", "example.com", "appassets.pogosummit.app.evil.com", "evil.com/appassets.pogosummit.app", "192.168.1.2", "localhost", "APPASSETS.POGOSUMMIT.APP"})
            assertFalse(String.valueOf(h), ShellLogic.isGameHost(h));
    }
    @Test public void startUrlIsHttps() { assertTrue(ShellLogic.START_URL.startsWith("https://appassets.pogosummit.app/")); }
    @Test public void rootMapsToIndex() {
        assertEquals("/index.html", ShellLogic.normalizePath("/"));
        assertEquals("/index.html", ShellLogic.normalizePath(""));
        assertEquals("/index.html", ShellLogic.normalizePath(null));
    }
    @Test public void normalPathsPassThrough() {
        assertEquals("/game.js", ShellLogic.normalizePath("/game.js"));
        assertEquals("/a/b.png", ShellLogic.normalizePath("/a/b.png"));
    }
    @Test public void traversalAndOddPathsAreRefused() {
        for (String p : new String[]{"/../secret", "/a/../../b", "/..", "\\windows", "/a\\b", "game.js", "/a\0b"})
            assertNull(p, ShellLogic.normalizePath(p));
    }
    @Test public void mimeTypes() {
        assertEquals("text/html", ShellLogic.mime("/index.html"));
        assertEquals("text/javascript", ShellLogic.mime("/game.js"));
        assertEquals("text/javascript", ShellLogic.mime("/GAME.JS"));
        assertEquals("image/png", ShellLogic.mime("/i.png"));
        assertEquals("font/woff2", ShellLogic.mime("/fonts/baloo-2-latin-800.woff2"));
        assertEquals("application/octet-stream", ShellLogic.mime("/x.bin"));
    }
    @Test public void amplitudeIsClampedToAndroidRange() {
        assertEquals(1, ShellLogic.clampAmplitude(-50));
        assertEquals(1, ShellLogic.clampAmplitude(0));
        assertEquals(128, ShellLogic.clampAmplitude(128));
        assertEquals(255, ShellLogic.clampAmplitude(9999));
    }
    @Test public void patternsParse() {
        assertArrayEquals(new long[]{0, 40, 60, 40, 60, 120}, ShellLogic.parsePattern("40,60,40,60,120"));
        assertArrayEquals(new long[]{0, 10}, ShellLogic.parsePattern(" 10 "));
    }
    @Test public void badPatternsAreRejectedNotThrown() {
        for (String p : new String[]{null, "", "  ", "a,b", "10,,20", "-5", "99999", "1,2,x"}) assertNull(String.valueOf(p), ShellLogic.parsePattern(p));
    }
    @Test public void insetsJsonIsDensityScaledAndParsable() {
        String j = ShellLogic.insetsJson(60, 0, 30, 90, 3f);
        assertEquals("{\"top\":20.0,\"right\":0.0,\"bottom\":10.0,\"left\":30.0}", j);
        assertEquals("{\"top\":60.0,\"right\":0.0,\"bottom\":0.0,\"left\":0.0}", ShellLogic.insetsJson(60, 0, 0, 0, 0f)); // bad density ⇒ 1
    }
}
