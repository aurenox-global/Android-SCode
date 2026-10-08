package a.a.a;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * A project with a user-customized AndroidManifest.xml (Code Viewer) used to replace the generated
 * manifest verbatim, losing the TTS_SERVICE {@code <queries>} declaration that Android 11+ needs so
 * that {@code TextToSpeech} can see any engine at all. {@link yq#ensureTtsVisibility(String)} re-adds
 * it without touching anything else.
 */
public class CustomManifestTtsVisibilityTest {

    private static final String MANIFEST_WITHOUT_TTS =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    package=\"com.example.app\" android:versionCode=\"1\" android:versionName=\"1.0\">\n"
            + "    <uses-permission android:name=\"android.permission.INTERNET\" />\n"
            + "    <application android:label=\"App\">\n"
            + "        <activity android:name=\".MainActivity\" android:exported=\"true\" />\n"
            + "    </application>\n"
            + "</manifest>\n";

    @Test
    public void addsTtsVisibilityBeforeApplication() {
        String out = yq.ensureTtsVisibility(MANIFEST_WITHOUT_TTS);
        assertTrue(out.contains("android.intent.action.TTS_SERVICE"));
        assertTrue(out.contains("<queries>"));
        // The declaration must land before <application ...>.
        assertTrue(out.indexOf("<queries>") < out.indexOf("<application"));
        // Everything else stays byte-identical around the insertion point.
        assertTrue(out.contains("<uses-permission android:name=\"android.permission.INTERNET\" />"));
        assertTrue(out.contains("<activity android:name=\".MainActivity\" android:exported=\"true\" />"));
    }

    @Test
    public void isIdempotent() {
        String once = yq.ensureTtsVisibility(MANIFEST_WITHOUT_TTS);
        String twice = yq.ensureTtsVisibility(once);
        assertEquals(once, twice);
        assertEquals(1, countOccurrences(twice, "android.intent.action.TTS_SERVICE"));
    }

    @Test
    public void leavesManifestsThatAlreadyDeclareTtsUntouched() {
        String already = yq.ensureTtsVisibility(MANIFEST_WITHOUT_TTS);
        assertEquals(already, yq.ensureTtsVisibility(already));
    }

    @Test
    public void fallsBackToClosingManifestWhenThereIsNoApplicationTag() {
        String noApp = "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <uses-permission android:name=\"android.permission.INTERNET\" />\n"
                + "</manifest>\n";
        String out = yq.ensureTtsVisibility(noApp);
        assertTrue(out.contains("android.intent.action.TTS_SERVICE"));
        assertTrue(out.indexOf("<queries>") < out.indexOf("</manifest>"));
    }

    @Test
    public void nullStaysNull() {
        assertNull(yq.ensureTtsVisibility(null));
    }

    @Test
    public void insertedBlockIsWellFormed() {
        String out = yq.ensureTtsVisibility(MANIFEST_WITHOUT_TTS);
        assertFalse(out.contains("<queries></queries>"));
        assertTrue(out.contains("<action android:name=\"android.intent.action.TTS_SERVICE\" />"));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
