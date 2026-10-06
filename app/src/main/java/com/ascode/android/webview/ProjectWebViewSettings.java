package com.ascode.android.webview;

import mod.hey.studios.project.ProjectSettings;

/**
 * Typed, per-project WebView configuration.
 *
 * <p>Wraps the project's {@link ProjectSettings} store and exposes every value with the default
 * that reproduces the code generated before these settings existed. A {@code null}
 * {@link ProjectSettings} (see {@link #defaults()}) yields the pure defaults, which is what the
 * code generator uses for projects that never opened the WebView screen.</p>
 *
 * <p>Adding a new key is a three-step change: declare the constant in {@link ProjectSettings},
 * add a getter here, and emit it from {@link #buildSettingsCode(String, String)} (or from the
 * dedicated TTS helpers in {@code Jx}).</p>
 */
public final class ProjectWebViewSettings {

    public static final String DEFAULT_TTS_RATE = "0.95";
    public static final String DEFAULT_TTS_LANG = "es-ES";
    public static final int DEFAULT_TEXT_ZOOM = 100;
    /** {@code WebSettings.LOAD_DEFAULT}. */
    public static final int CACHE_MODE_DEFAULT = -1;

    public static final String ZOOM_OFF = "off";
    public static final String ZOOM_ONLY = "only";
    public static final String ZOOM_CONTROLS = "controls";

    /** Doesn't emit the platform-specific user agent override. */
    public static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final ProjectSettings settings;

    public ProjectWebViewSettings(String scId) {
        this(new ProjectSettings(scId));
    }

    public ProjectWebViewSettings(ProjectSettings settings) {
        this.settings = settings;
    }

    /** An instance that only returns defaults, used when no project is available. */
    public static ProjectWebViewSettings defaults() {
        return new ProjectWebViewSettings((ProjectSettings) null);
    }

    private String value(String key, String defaultValue) {
        return settings == null ? defaultValue : settings.getValue(key, defaultValue);
    }

    private boolean bool(String key, boolean defaultValue) {
        return settings == null ? defaultValue : settings.getBoolean(key, defaultValue);
    }

    private int integer(String key, int defaultValue) {
        return settings == null ? defaultValue : settings.getInt(key, defaultValue);
    }

    public boolean isJavaScriptEnabled() {
        return bool(ProjectSettings.SETTING_WEBVIEW_JAVASCRIPT, true);
    }

    public boolean isDomStorageEnabled() {
        return bool(ProjectSettings.SETTING_WEBVIEW_DOM_STORAGE, true);
    }

    public String getZoomMode() {
        String mode = value(ProjectSettings.SETTING_WEBVIEW_ZOOM, ZOOM_ONLY);
        if (ZOOM_OFF.equals(mode) || ZOOM_CONTROLS.equals(mode)) {
            return mode;
        }
        return ZOOM_ONLY;
    }

    public boolean isZoomEnabled() {
        return !ZOOM_OFF.equals(getZoomMode());
    }

    public boolean isMediaGestureRequired() {
        // Stored as "media may play without a gesture"; the generated call expects the inverse.
        return !bool(ProjectSettings.SETTING_WEBVIEW_MEDIA_NO_GESTURE, true);
    }

    public int getCacheMode() {
        return integer(ProjectSettings.SETTING_WEBVIEW_CACHE_MODE, CACHE_MODE_DEFAULT);
    }

    public boolean isTtsBridgeEnabled() {
        return bool(ProjectSettings.SETTING_WEBVIEW_TTS_BRIDGE, true);
    }

    public String getTtsRate() {
        String rate = value(ProjectSettings.SETTING_WEBVIEW_TTS_RATE, DEFAULT_TTS_RATE);
        try {
            float parsed = Float.parseFloat(rate.trim());
            if (parsed <= 0f) {
                return DEFAULT_TTS_RATE;
            }
        } catch (NumberFormatException e) {
            return DEFAULT_TTS_RATE;
        }
        return rate.trim();
    }

    public String getTtsLang() {
        String lang = value(ProjectSettings.SETTING_WEBVIEW_TTS_LANG, DEFAULT_TTS_LANG).trim();
        return lang.isEmpty() ? DEFAULT_TTS_LANG : lang;
    }

    public int getTextZoom() {
        int zoom = integer(ProjectSettings.SETTING_WEBVIEW_TEXT_ZOOM, DEFAULT_TEXT_ZOOM);
        return zoom <= 0 ? DEFAULT_TEXT_ZOOM : zoom;
    }

    public boolean isDesktopModeEnabled() {
        return bool(ProjectSettings.SETTING_WEBVIEW_DESKTOP_MODE, false);
    }

    /**
     * @return The explicit file access value, or {@code null} when the platform default should be
     * kept (nothing emitted).
     */
    public Boolean getAllowFileAccess() {
        String value = value(ProjectSettings.SETTING_WEBVIEW_ALLOW_FILE_ACCESS, null);
        if (value == null || value.isEmpty()) {
            return null;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * @return The explicit mixed content mode, or {@code null} when the platform default should be
     * kept (nothing emitted).
     */
    public Integer getMixedContentMode() {
        String value = value(ProjectSettings.SETTING_WEBVIEW_MIXED_CONTENT, null);
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Builds the {@code <webView>.getSettings()...} statements for one WebView variable.
     *
     * <p>With no stored settings the result is exactly the four lines the generator emitted
     * before this feature existed.</p>
     *
     * @param viewName the Java expression referencing the WebView (never null)
     * @param EOL      line separator used by the surrounding generator
     */
    public String buildSettingsCode(String viewName, String EOL) {
        StringBuilder sb = new StringBuilder(320);
        String prefix = viewName + ".getSettings().";
        sb.append(prefix).append("setJavaScriptEnabled(").append(isJavaScriptEnabled()).append(");").append(EOL);
        sb.append(prefix).append("setDomStorageEnabled(").append(isDomStorageEnabled()).append(");").append(EOL);
        sb.append(prefix).append("setMediaPlaybackRequiresUserGesture(").append(isMediaGestureRequired()).append(");").append(EOL);

        String zoomMode = getZoomMode();
        if (ZOOM_OFF.equals(zoomMode)) {
            sb.append(prefix).append("setSupportZoom(false);");
        } else {
            sb.append(prefix).append("setSupportZoom(true);");
            if (ZOOM_CONTROLS.equals(zoomMode)) {
                sb.append(EOL).append(prefix).append("setBuiltInZoomControls(true);");
                sb.append(EOL).append(prefix).append("setDisplayZoomControls(false);");
            }
        }

        int cacheMode = getCacheMode();
        if (cacheMode != CACHE_MODE_DEFAULT) {
            String constant = switch (cacheMode) {
                case 1 -> "LOAD_CACHE_ELSE_NETWORK";
                case 2 -> "LOAD_NO_CACHE";
                case 3 -> "LOAD_CACHE_ONLY";
                default -> null;
            };
            if (constant != null) {
                sb.append(EOL).append(prefix).append("setCacheMode(WebSettings.").append(constant).append(");");
            }
        }

        if (getTextZoom() != DEFAULT_TEXT_ZOOM) {
            sb.append(EOL).append(prefix).append("setTextZoom(").append(getTextZoom()).append(");");
        }

        if (isDesktopModeEnabled()) {
            sb.append(EOL).append(prefix).append("setUserAgentString(\"").append(DESKTOP_UA).append("\");");
            sb.append(EOL).append(prefix).append("setUseWideViewPort(true);");
            sb.append(EOL).append(prefix).append("setLoadWithOverviewMode(true);");
        }

        Boolean allowFileAccess = getAllowFileAccess();
        if (allowFileAccess != null) {
            sb.append(EOL).append(prefix).append("setAllowFileAccess(").append(allowFileAccess).append(");");
        }

        Integer mixedContentMode = getMixedContentMode();
        if (mixedContentMode != null) {
            String constant = switch (mixedContentMode) {
                case 0 -> "MIXED_CONTENT_ALWAYS_ALLOW";
                case 1 -> "MIXED_CONTENT_NEVER_ALLOW";
                case 2 -> "MIXED_CONTENT_COMPATIBILITY_MODE";
                default -> null;
            };
            if (constant != null) {
                sb.append(EOL).append(prefix).append("setMixedContentMode(WebSettings.").append(constant).append(");");
            }
        }

        return sb.toString();
    }
}
