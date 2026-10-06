package mod.hey.studios.project;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Checkable;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.google.gson.Gson;

import java.io.File;
import java.util.HashMap;

import mod.hey.studios.util.Helper;
import mod.jbk.util.LogUtil;
import com.ascode.android.utility.FileUtil;

public class ProjectSettings {

    /**
     * Setting for the final app's {@code minSdkVersion}
     *
     * @see ApplicationInfo#minSdkVersion
     */
    public static final String SETTING_MINIMUM_SDK_VERSION = "min_sdk";

    /**
     * Setting to make the app's main theme inherit from fully material-styled themes, and not *.Bridge ones
     */
    public static final String SETTING_ENABLE_BRIDGELESS_THEMES = "enable_bridgeless_themes";

    /**
     * Setting to enable view binding in the project
     */
    public static final String SETTING_ENABLE_VIEWBINDING = "enable_viewbinding";

    /**
     * Setting for the final app's {@link Application} class
     *
     * @see Application
     */
    public static final String SETTING_APPLICATION_CLASS = "app_class";

    /**
     * Setting for the final app's {@code targetSdkVersion}
     *
     * @see ApplicationInfo#targetSdkVersion
     */
    public static final String SETTING_TARGET_SDK_VERSION = "target_sdk";

    /**
     * Setting to disable showing deprecated methods included in every generated class, e.g. showMessage(String)
     */
    public static final String SETTING_DISABLE_OLD_METHODS = "disable_old_methods";
    /**
     * Setting to use new xml command
     */
    public static final String SETTING_NEW_XML_COMMAND = "xml_command";
    /**
     * Setting for Logic Editor persisted KMP target context
     */
    public static final String SETTING_KMP_EDITOR_TARGET = "kmp_editor_target";

    // ---------------------------------------------------------------------------------------
    // WebView configuration (per project).
    //
    // Every key documented below keeps a default that reproduces the code generated before
    // these settings existed, so projects without a stored value compile byte-for-byte the
    // same as always. New keys can simply be added here (plus the screen / code emitter).
    // ---------------------------------------------------------------------------------------

    /** Whether JavaScript is enabled in the WebView. Default: true. */
    public static final String SETTING_WEBVIEW_JAVASCRIPT = "webview_javascript";
    /** Whether DOM storage (localStorage / sessionStorage) is enabled. Default: true. */
    public static final String SETTING_WEBVIEW_DOM_STORAGE = "webview_dom_storage";
    /**
     * Zoom mode. One of {@code off}, {@code only} (integrated zoom, default) or
     * {@code controls} (integrated zoom + on-screen zoom controls).
     */
    public static final String SETTING_WEBVIEW_ZOOM = "webview_zoom";
    /**
     * Cache mode. Empty/absent = platform default ({@code WebSettings.LOAD_DEFAULT}). Stored as the
     * numeric {@code WebSettings} constant (1 = cache else network, 2 = no cache, 3 = cache only).
     */
    public static final String SETTING_WEBVIEW_CACHE_MODE = "webview_cache_mode";
    /**
     * Whether media may start without a user gesture. Default: true (generated code calls
     * {@code setMediaPlaybackRequiresUserGesture(false)}).
     */
    public static final String SETTING_WEBVIEW_MEDIA_NO_GESTURE = "webview_media_no_gesture";
    /** Whether the JavaScript Text-to-Speech bridge ({@code AndroidBridge}) is added. Default: true. */
    public static final String SETTING_WEBVIEW_TTS_BRIDGE = "webview_tts_bridge";
    /** Default speech rate used by the TTS bridge. Default: 0.95. */
    public static final String SETTING_WEBVIEW_TTS_RATE = "webview_tts_rate";
    /** Default language used by the TTS bridge, e.g. {@code es-ES}. Default: es-ES. */
    public static final String SETTING_WEBVIEW_TTS_LANG = "webview_tts_lang";
    /** Text zoom percentage. Default: 100 (not emitted, matches the platform default). */
    public static final String SETTING_WEBVIEW_TEXT_ZOOM = "webview_text_zoom";
    /** Desktop mode (forces a desktop user agent). Default: false. */
    public static final String SETTING_WEBVIEW_DESKTOP_MODE = "webview_desktop_mode";
    /**
     * File access. Empty/absent = platform default (nothing is emitted). Otherwise {@code true} or
     * {@code false} to call {@code setAllowFileAccess(...)} explicitly.
     */
    public static final String SETTING_WEBVIEW_ALLOW_FILE_ACCESS = "webview_allow_file_access";
    /**
     * Mixed content mode. Empty/absent = platform default (nothing is emitted). Otherwise the
     * numeric {@code WebSettings} constant (0 = always allow, 1 = never allow, 2 = compatibility).
     */
    public static final String SETTING_WEBVIEW_MIXED_CONTENT = "webview_mixed_content";

    public static final String SETTING_GENERIC_VALUE_TRUE = "true";
    public static final String SETTING_GENERIC_VALUE_FALSE = "false";
    private static final String TAG = "ProjectSettings";
    private final String path;
    public String sc_id;
    private HashMap<String, String> hashmap;

    public ProjectSettings(String scId) {
        sc_id = scId;

        path = getPath();

        if (FileUtil.isExistFile(path)) {
            try {
                hashmap = new Gson().fromJson(FileUtil.readFile(path).trim(), Helper.TYPE_STRING_MAP);
            } catch (Exception e) {
                Log.e("ProjectSettings", "Failed to read project settings for project " + sc_id + "!", e);
                hashmap = new HashMap<>();
                save();
            }
        } else {
            hashmap = new HashMap<>();
        }
    }

    /**
     * @return The configured minimum SDK version. Returns 21 if none or an invalid value was set.
     * @see #SETTING_MINIMUM_SDK_VERSION
     */
    public int getMinSdkVersion() {
        if (hashmap.containsKey(SETTING_MINIMUM_SDK_VERSION)) {
            try {
                //noinspection ConstantConditions because we catch that already
                return Integer.parseInt(hashmap.get(SETTING_MINIMUM_SDK_VERSION));
            } catch (NumberFormatException | NullPointerException e) {
                LogUtil.e(TAG, "Failed to parse the project's minimum SDK version! Defaulting to 21", e);
                return 21;
            }
        } else {
            return 21;
        }
    }

    public String getPath() {
        return new File(Environment.getExternalStorageDirectory(), ".AndroidSCode/data/" + sc_id + "/project_config").getAbsolutePath();
    }

    public String getValue(String key, String defaultValue) {
        if (hashmap != null && hashmap.containsKey(key)) {
            if (!hashmap.get(key).isEmpty()) {
                return hashmap.get(key);
            } else {
                return defaultValue;
            }
        } else {
            return defaultValue;
        }
    }

    /**
     * @return The stored value parsed as a boolean, or {@code defaultValue} when absent/empty.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getValue(key, null);
        if (value == null) {
            return defaultValue;
        }
        return SETTING_GENERIC_VALUE_TRUE.equalsIgnoreCase(value.trim());
    }

    /**
     * @return The stored value parsed as an int, or {@code defaultValue} when absent/invalid.
     */
    public int getInt(String key, int defaultValue) {
        String value = getValue(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void processView(View v) {
        if (v.getTag() != null) {
            String key = (String) v.getTag();
            String value;

            if (v instanceof EditText editText) {
                value = Helper.getText(editText);
            } else if (v instanceof Checkable checkable) {
                value = Boolean.toString(checkable.isChecked());
            } else if (v instanceof RadioGroup radioGroup) {
                value = getCheckedRbValue(radioGroup);
            } else {
                return;
            }

            hashmap.put(key, value);
        }
    }

    public void setValues(View... views) {
        for (View v : views) {
            processView(v);
        }
        save();
    }

    public void setValue(String key, String value) {
        hashmap.put(key, value);
        save();
    }

    private String getCheckedRbValue(RadioGroup rg) {
        for (int i = 0; i < rg.getChildCount(); i++) {
            RadioButton rb = (RadioButton) rg.getChildAt(i);

            if (rb.isChecked()) {
                return Helper.getText(rb);
            }
        }

        return "";
    }

    private void save() {
        FileUtil.writeFile(path, new Gson().toJson(hashmap));
    }
}
