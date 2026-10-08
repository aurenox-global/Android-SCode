package a.a.a;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.github.javaparser.StaticJavaParser;

import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Verifies that a WebView activity carrying an <em>older</em> TTS helper is upgraded surgically to
 * the current helper, leaving the rest of the file untouched, and that the current helper carries
 * the language-aware engine selection + JS diagnostics introduced in helper version 3.
 *
 * <p>The sample in {@code src/test/resources/legacy/MainActivity.java.txt} was generated with the
 * {@code Jx} from commit {@code d9e5423} (v1.0.41), i.e. the legacy helper (pre-v2) found in
 * projects patched by the old migration. A v2-shaped helper is also exercised (the current output
 * with only its version marker rewritten to v2), which is exactly what the v1.0.42/43 releases
 * shipped.</p>
 */
public class WebViewTtsMigrationTest {

    private String readLegacySample() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("legacy/MainActivity.java.txt")) {
            assertNotNull("legacy/MainActivity.java.txt test resource must exist", in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Full, compilable-shape activity carrying the current helper (used by the javac check). */
    private String buildFullActivity() {
        StringBuilder sb = new StringBuilder();
        Jx.appendWebViewTtsHelpers(sb, "\n");
        return "package com.ascode.check;\n"
                + "import android.app.Activity;\n"
                + "import android.webkit.*;\n"
                + "import android.os.*;\n"
                + "import android.media.*;\n"
                + "import android.speech.tts.TextToSpeech;\n"
                + "import java.util.*;\n"
                + "public class MainActivity extends Activity {\n"
                + "private WebView webview1;\n"
                + sb
                + "}\n";
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
    public void currentHelperSelectsEngineByLanguageAndWarnsHonestly() {
        StringBuilder sb = new StringBuilder();
        Jx.appendWebViewTtsHelpers(sb, "\n");
        String current = sb.toString();

        // Language-aware engine selection.
        assertTrue(current.contains("getEngines"));
        assertTrue(current.contains("queryIntentServices"));
        assertTrue(current.contains("isLanguageAvailable"));
        assertTrue(current.contains("LANG_COUNTRY_VAR_AVAILABLE"));
        assertTrue(current.contains("com.google.android.tts"));
        assertTrue(current.contains("_ttsEngineRejected"));
        assertTrue(current.contains("_ttsStartFinalFallback"));
        assertTrue(current.contains("_acceptLanguageFallback"));

        // Honest, one-shot user warning when the language has no voice data.
        assertTrue(current.contains("_ttsWarnNoVoice"));
        assertTrue(current.contains("_ttsNoVoiceWarned"));
        assertTrue(current.contains("android.widget.Toast.makeText"));
        assertTrue(current.contains("Instalar datos de voz"));

        // JS diagnostics: pull API + pushed events, guarded so a page without the hook is safe.
        assertTrue(current.contains("getDiagnostics"));
        assertTrue(current.contains("window.__ascodeTtsDiag"));
        assertTrue(current.contains("__ascodeTtsDiag&&window.__ascodeTtsDiag"));
        assertTrue(current.contains("evaluateJavascript"));
        assertTrue(current.contains("_ttsEmitDiag(\"onStart\")"));
        assertTrue(current.contains("_ttsEmitDiag(\"onDone\")"));
        assertTrue(current.contains("_ttsEmitDiag(\"onError\")"));
    }

    @Test
    public void versionTwoHelperIsLegacyAndUpgradedToCurrent() {
        StringBuilder sb = new StringBuilder();
        Jx.appendWebViewTtsHelpers(sb, "\n");
        String v3 = sb.toString();
        // Simulate a project patched by v1.0.42/43: identical helper body, v2 version marker.
        String v2 = v3.replace(Jx.WEBVIEW_TTS_HELPER_BEGIN_MARKER, "// <ascode-tts v2>");
        assertFalse("a v2 helper must not look current", Jx.hasCurrentWebViewTtsHelper(v2));
        assertTrue("a v2 helper must be flagged as legacy", Jx.hasLegacyWebViewTtsHelper(v2));

        String file = "package x;\npublic class MainActivity {\n" + v2 + "}\n";
        String migrated = Jx.replaceLegacyWebViewTtsHelper(file, "\n");
        assertNotNull(migrated);
        assertTrue(migrated.contains(Jx.WEBVIEW_TTS_HELPER_BEGIN_MARKER));
        assertTrue(Jx.hasCurrentWebViewTtsHelper(migrated));
        assertFalse(Jx.hasLegacyWebViewTtsHelper(migrated));
        assertEquals(1, countOccurrences(migrated, "private class _TtsBridge"));
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
        assertEquals(4, Jx.WEBVIEW_TTS_HELPER_VERSION);
    }

    @Test
    public void currentHelperCarriesAnOffByDefaultDiagnosticsFlag() {
        StringBuilder sb = new StringBuilder();
        Jx.appendWebViewTtsHelpers(sb, "\n");
        String current = sb.toString();

        // The flag exists, defaults to OFF, and records its own value for the migration check.
        assertTrue(current.contains("private boolean _ttsShowDiagToasts = false;"));
        assertTrue(current.contains(Jx.WEBVIEW_TTS_DIAG_MARKER_PREFIX + "false"));
        assertTrue(Jx.hasWebViewTtsDiagnosticsSetting(current, false));
        assertFalse(Jx.hasWebViewTtsDiagnosticsSetting(current, true));

        // The toast path reuses the diagnostics summary and is throttled on the main thread.
        assertTrue(current.contains("_ttsMaybeDiagToast"));
        assertTrue(current.contains("_ttsDiagnostics()"));
        assertTrue(current.contains("android.widget.Toast.LENGTH_SHORT"));
        assertTrue(current.contains("android.os.SystemClock.elapsedRealtime()"));
        assertTrue(current.contains("800L"));
        assertTrue(current.contains("Looper.getMainLooper()"));

        // The persistent, logcat-visible summary line (tag AscodeTTS).
        assertTrue(current.contains("DIAG event="));
        assertTrue(current.contains("\"AscodeTTS\""));

        // The honest "no voice installed" toast must still be emitted alongside the diagnostics.
        assertTrue(current.contains("_ttsWarnNoVoice"));
        assertTrue(current.contains("Instalar datos de voz"));
    }

    @Test
    public void diagnosticsFlagCanBeBakedOn() {
        StringBuilder sb = new StringBuilder();
        Jx.appendWebViewTtsHelpers(sb, "\n", "1.25", "pt-BR", true);
        String current = sb.toString();

        assertTrue(current.contains("private boolean _ttsShowDiagToasts = true;"));
        assertTrue(current.contains(Jx.WEBVIEW_TTS_DIAG_MARKER_PREFIX + "true"));
        assertTrue(Jx.hasWebViewTtsDiagnosticsSetting(current, true));
        assertFalse(Jx.hasWebViewTtsDiagnosticsSetting(current, false));
        assertTrue(current.contains("1.25f"));
        assertTrue(current.contains("new Locale(\"pt\", \"BR\")"));
        assertTrue(Jx.hasCurrentWebViewTtsHelper(current));
    }

    @Test
    public void replacementPreservesDiagnosticsSettingAndRate() {
        // A v3-shaped helper (current body with the version marker rewritten) must be upgraded to
        // the v4 helper while keeping the requested rate/language/diagnostics values.
        StringBuilder sb = new StringBuilder();
        Jx.appendWebViewTtsHelpers(sb, "\n", "1.1", "en-US", false);
        String v3 = sb.toString().replace(Jx.WEBVIEW_TTS_HELPER_BEGIN_MARKER, "// <ascode-tts v3>");
        String file = "package x;\npublic class MainActivity {\n" + v3 + "}\n";

        String migrated = Jx.replaceLegacyWebViewTtsHelper(file, "\n", "1.1", "en-US", true);
        assertNotNull(migrated);
        assertTrue(Jx.hasCurrentWebViewTtsHelper(migrated));
        assertTrue(Jx.hasWebViewTtsDiagnosticsSetting(migrated, true));
        assertTrue(migrated.contains("private boolean _ttsShowDiagToasts = true;"));
        assertTrue(migrated.contains("1.1f"));
        assertTrue(migrated.contains("new Locale(\"en\", \"US\")"));
    }

    @Test
    public void ttsDiagnosticsSettingDefaultsOff() {
        // The pure defaults (no project / never opened the screen) must reproduce the previous
        // behaviour: diagnostics toasts off.
        assertFalse(com.ascode.android.webview.ProjectWebViewSettings.defaults()
                .isTtsDiagnosticsEnabled());
    }

    @Test
    public void projectWithoutHelperIsNotFlaggedAsLegacy() {
        String plain = "public class MainActivity extends AppCompatActivity {\n"
                + "    private WebView webview1;\n"
                + "}\n";
        assertFalse(Jx.hasCurrentWebViewTtsHelper(plain));
        assertFalse(Jx.hasLegacyWebViewTtsHelper(plain));
    }

    /**
     * Writes a full activity carrying the current helper so the build/verification step can run
     * {@code javac} against {@code android.jar} and {@code javap} the resulting bytecode.
     */
    @Test
    public void emitsGeneratedActivityForBytecodeCheck() throws Exception {
        String activity = buildFullActivity();
        StaticJavaParser.parse(activity);
        File out = new File("build/tts-generated-check/com/ascode/check/MainActivity.java");
        assertTrue(out.getParentFile().mkdirs() || out.getParentFile().isDirectory());
        Files.write(out.toPath(), activity.getBytes(StandardCharsets.UTF_8));
        assertTrue(out.isFile());

        // Also emit the diagnostics-enabled variant so the javac check covers the new Toast path.
        StringBuilder diag = new StringBuilder();
        Jx.appendWebViewTtsHelpers(diag, "\n", "0.95", "es-ES", true);
        String diagActivity = "package com.ascode.check;\n"
                + "import android.app.Activity;\n"
                + "import android.webkit.*;\n"
                + "import android.os.*;\n"
                + "import android.media.*;\n"
                + "import android.speech.tts.TextToSpeech;\n"
                + "import java.util.*;\n"
                + "public class DiagActivity extends Activity {\n"
                + "private WebView webview1;\n"
                + diag
                + "}\n";
        StaticJavaParser.parse(diagActivity);
        File diagOut = new File("build/tts-generated-check-diag/com/ascode/check/DiagActivity.java");
        assertTrue(diagOut.getParentFile().mkdirs() || diagOut.getParentFile().isDirectory());
        Files.write(diagOut.toPath(), diagActivity.getBytes(StandardCharsets.UTF_8));
        assertTrue(diagOut.isFile());
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
