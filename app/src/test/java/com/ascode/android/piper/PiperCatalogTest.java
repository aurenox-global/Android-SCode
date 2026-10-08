package com.ascode.android.piper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/**
 * Verifies the curated catalog and the espeak-ng-data pruning that keeps a project's voice small.
 */
public class PiperCatalogTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File write(File file, int size) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[size]);
        }
        return file;
    }

    @Test
    public void catalogOnlyContainsSpanishAndEnglishVoices() {
        List<PiperCatalog.Voice> voices = PiperCatalog.voices();
        assertTrue(voices.size() >= 5);
        for (PiperCatalog.Voice voice : voices) {
            assertTrue(voice.lang.startsWith("es") || voice.lang.startsWith("en"));
            assertTrue(voice.url.startsWith(PiperCatalog.VOICE_ASSET_BASE));
            assertTrue(voice.url.endsWith(voice.id + ".tar.bz2"));
            assertTrue(voice.sha256.matches("[0-9a-f]{64}"));
            assertTrue(voice.sizeBytes > 0);
            assertNotNull(voice.license);
        }
    }

    @Test
    public void defaultVoiceIsTheMexicanSpanishOne() {
        assertEquals("es_MX-ald-medium-int8", PiperCatalog.DEFAULT_VOICE_ID);
        assertEquals(PiperCatalog.DEFAULT_VOICE_ID, PiperCatalog.defaultVoice().id);
        assertNotNull(PiperCatalog.findById("es_MX-ald-medium-int8"));
        assertNotNull(PiperCatalog.findByFileName("vits-piper-es_MX-ald-medium-int8.tar.bz2"));
        assertNull(PiperCatalog.findById("de_DE-thorsten-medium-int8"));
    }

    @Test
    public void prunesEveryDictionaryButSpanishAndEnglish() throws Exception {
        File espeak = tmp.newFolder("espeak-ng-data");
        write(new File(espeak, "ru_dict"), 8_000_000);
        write(new File(espeak, "cmn_dict"), 1_500_000);
        write(new File(espeak, "de_dict"), 68_000);
        write(new File(espeak, "en_dict"), 166_000);
        write(new File(espeak, "es_dict"), 49_000);
        write(new File(espeak, "phondata"), 540_000);
        write(new File(espeak, "phontab"), 1_000);
        write(new File(espeak, "lang/roa/es"), 1_000);
        write(new File(espeak, "lang/gmw/en"), 1_000);
        write(new File(espeak, "lang/zle/ru"), 1_000);

        long saved = PiperInstaller.pruneEspeakData(espeak);

        assertTrue("expected a large saving, got " + saved, saved >= 9_500_000);
        assertTrue(new File(espeak, "en_dict").isFile());
        assertTrue(new File(espeak, "es_dict").isFile());
        assertTrue(new File(espeak, "phondata").isFile());
        assertTrue(new File(espeak, "phontab").isFile());
        assertTrue(new File(espeak, "lang/roa/es").isFile());
        assertTrue(new File(espeak, "lang/gmw/en").isFile());
        assertTrue(!new File(espeak, "ru_dict").exists());
        assertTrue(!new File(espeak, "cmn_dict").exists());
        assertTrue(!new File(espeak, "lang/zle").exists());
    }

    @Test
    public void humanSizeIsReadable() {
        assertEquals("20.3 MB", PiperCatalog.humanSize(21_283_187L));
        assertEquals("500 B", PiperCatalog.humanSize(500));
    }
}
