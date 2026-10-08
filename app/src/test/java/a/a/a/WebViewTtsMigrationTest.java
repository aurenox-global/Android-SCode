package a.a.a;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.github.javaparser.StaticJavaParser;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Verifies that a WebView activity carrying the <em>legacy</em> TTS helper (the one injected by
 * older releases, without audio attributes / audio focus / explicit stream+volume) is upgraded
 * surgically to the current helper, leaving the rest of the file untouched.
 *
 * <p>The sample in {@code src/test/resources/legacy/MainActivity.java} was generated with the
 * {@code Jx} from commit {@code d9e5423} (v1.0.41), i.e. the exact helper shape found in projects
 * patched by the old migration.</p>
 */
public class WebViewTtsMigrationTest {

    private String readLegacySample() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("legacy/MainActivity.java.txt")) {
            assertNotNull("legacy/MainActivity.java.txt test resource must exist", in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void legacySampleIsDetectedAsOutdated() throws Exception {
        String legacy = readLegacySample();
        assertTrue("sample must contain the legacy helper field",
                legacy.contains("private TextToSpeech _tts;"));
        assertFalse("legacy sample must not look current", Jx.hasCurrentWebViewTtsHelper(legacy));
        assertTrue("legacy sample must be flagged as outdated", Jx.hasLegacyWebViewTtsHelper(legacy));
    }

    @Test
    public void migrationUpgradesHelperAndPreservesEverythingElse() throws Exception {
        String legacy = readLegacySample();

        String migrated = Jx.replaceLegacyWebViewTtsHelper(legacy, "\n");
        assertNotNull("migration must locate the legacy helper block", migrated);

        // Required audio routing from the v2 helper.
        assertTrue(migrated.contains("setAudioAttributes"));
        assertTrue(migrated.contains("USAGE_MEDIA"));
        assertTrue(migrated.contains("CONTENT_TYPE_SPEECH"));
        assertTrue(migrated.contains("STREAM_MUSIC"));
        assertTrue(migrated.contains("KEY_PARAM_VOLUME"));
        assertTrue(migrated.contains("requestAudioFocus"));

        // Version markers are present.
        assertTrue(migrated.contains(Jx.WEBVIEW_TTS_HELPER_BEGIN_MARKER));
        assertTrue(migrated.contains(Jx.WEBVIEW_TTS_HELPER_END_MARKER));

        // The new shim (installs speechSynthesis only when the native voices are unusable).
        assertTrue(migrated.contains("getVoices"));
        assertTrue(migrated.contains("_shimNeeded"));
        // The old shim installed only when speechSynthesis was undefined: it must be gone.
        assertFalse(migrated.contains("window.speechSynthesis==='undefined'"));

        // After migration the file is detected as current and no longer as legacy.
        assertTrue(Jx.hasCurrentWebViewTtsHelper(migrated));
        assertFalse(Jx.hasLegacyWebViewTtsHelper(migrated));

        // The header before the helper is byte-identical.
        String header = legacy.substring(0, legacy.indexOf("private TextToSpeech _tts;"));
        assertTrue("header before the helper must be untouched", migrated.startsWith(header));

        // The user code that follows the helper survives, including a final class brace.
        assertTrue(migrated.contains("private void initializeLogic() {"));
        assertTrue(migrated.contains("// USER CODE THAT MUST SURVIVE THE MIGRATION"));
        assertTrue("file must still end with the original class closing brace",
                migrated.endsWith("// USER CODE THAT MUST SURVIVE THE MIGRATION\n    }\n}\n"));

        // Exactly one helper block: no duplicated _TtsBridge.
        assertEquals(1, countOccurrences(migrated, "private class _TtsBridge"));
        assertEquals(1, countOccurrences(migrated, "private TextToSpeech _tts;"));
    }

    @Test
    public void migratedSourceStillParsesAsJava() throws Exception {
        String legacy = readLegacySample();
        String migrated = Jx.replaceLegacyWebViewTtsHelper(legacy, "\n");
        assertNotNull(migrated);
        // Throws ParseProblemException when the result is not syntactically valid Java.
        StaticJavaParser.parse(migrated);
    }

    @Test
    public void brandNewHelperIsCurrentAndNotTouched() {
        StringBuilder sb = new StringBuilder();
        Jx.appendWebViewTtsHelpers(sb, "\n");
        String current = sb.toString();

        assertTrue(Jx.hasCurrentWebViewTtsHelper(current));
        assertFalse(Jx.hasLegacyWebViewTtsHelper(current));
        // A current helper starts with the version marker (after the leading EOL).
        assertTrue(current.trim().startsWith(Jx.WEBVIEW_TTS_HELPER_BEGIN_MARKER));
    }

    @Test
    public void projectWithoutHelperIsNotFlaggedAsLegacy() {
        String plain = "public class MainActivity extends AppCompatActivity {\n"
                + "    private WebView webview1;\n"
                + "}\n";
        assertFalse(Jx.hasCurrentWebViewTtsHelper(plain));
        assertFalse(Jx.hasLegacyWebViewTtsHelper(plain));
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
