package com.ascode.android.activities.webview;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import mod.hey.studios.project.ProjectSettings;
import mod.hey.studios.util.Helper;
import com.ascode.android.R;
import com.ascode.android.databinding.ActivityWebviewSettingsBinding;
import com.ascode.android.utility.UI;
import com.ascode.android.webview.ProjectWebViewSettings;

/**
 * Dedicated, per-project WebView configuration screen.
 *
 * <p>Reached from the design screen's <em>Configuration</em> menu. Every value is stored in the
 * project's {@link ProjectSettings} store (never globally) and read back by the code generators
 * ({@code Jx}/{@code Lx}/{@code yq}) when producing the compiled app's WebView setup.</p>
 *
 * <p>Changes are persisted immediately, so leaving the screen (back / process death) never loses
 * them. Leaving a value untouched means "use the default", which reproduces the behaviour of apps
 * generated before this screen existed.</p>
 */
public class WebViewSettingsActivity extends BaseAppCompatActivity {

    private static final String[] ZOOM_VALUES = {
            ProjectWebViewSettings.ZOOM_OFF,
            ProjectWebViewSettings.ZOOM_ONLY,
            ProjectWebViewSettings.ZOOM_CONTROLS
    };
    private static final String[] CACHE_VALUES = {"-1", "1", "2", "3"};
    private static final String[] FILE_ACCESS_VALUES = {"", "true", "false"};
    private static final String[] MIXED_CONTENT_VALUES = {"", "0", "1", "2"};

    private ActivityWebviewSettingsBinding binding;
    private ProjectSettings settings;
    private String sc_id;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWebviewSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.webview_settings_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
        UI.addSystemWindowInsetToPadding(binding.scroll, false, false, false, true);

        sc_id = savedInstanceState == null
                ? getIntent().getStringExtra("sc_id")
                : savedInstanceState.getString("sc_id");
        if (sc_id == null || sc_id.isEmpty()) {
            finish();
            return;
        }
        settings = new ProjectSettings(sc_id);

        setupZoom();
        setupCache();
        setupFileAccess();
        setupMixedContent();
        setupSwitch(binding.swJavascript, ProjectSettings.SETTING_WEBVIEW_JAVASCRIPT, true);
        setupSwitch(binding.swDomStorage, ProjectSettings.SETTING_WEBVIEW_DOM_STORAGE, true);
        setupSwitch(binding.swMediaNoGesture, ProjectSettings.SETTING_WEBVIEW_MEDIA_NO_GESTURE, true);
        setupSwitch(binding.swTtsBridge, ProjectSettings.SETTING_WEBVIEW_TTS_BRIDGE, true);
        setupSwitch(binding.swDesktopMode, ProjectSettings.SETTING_WEBVIEW_DESKTOP_MODE, false);
        setupText(binding.etTtsRate, ProjectSettings.SETTING_WEBVIEW_TTS_RATE,
                ProjectWebViewSettings.DEFAULT_TTS_RATE);
        setupText(binding.etTtsLang, ProjectSettings.SETTING_WEBVIEW_TTS_LANG,
                ProjectWebViewSettings.DEFAULT_TTS_LANG);
        setupText(binding.etTextZoom, ProjectSettings.SETTING_WEBVIEW_TEXT_ZOOM,
                String.valueOf(ProjectWebViewSettings.DEFAULT_TEXT_ZOOM));
        setupReset();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("sc_id", sc_id);
    }

    private void setupSwitch(com.google.android.material.materialswitch.MaterialSwitch view,
                             String key, boolean defaultValue) {
        view.setChecked(settings.getBoolean(key, defaultValue));
        view.setOnCheckedChangeListener((buttonView, isChecked) ->
                settings.setValue(key, Boolean.toString(isChecked)));
    }

    private void setupText(EditText editText, String key, String defaultValue) {
        String stored = settings.getValue(key, defaultValue);
        editText.setText(stored);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                settings.setValue(key, s.toString().trim());
            }
        });
    }

    private void setupZoom() {
        String[] labels = {
                Helper.getResString(R.string.webview_settings_zoom_off),
                Helper.getResString(R.string.webview_settings_zoom_only),
                Helper.getResString(R.string.webview_settings_zoom_controls)
        };
        binding.actZoom.setSimpleItems(labels);
        bindChoice(binding.actZoom, labels, ZOOM_VALUES,
                ProjectSettings.SETTING_WEBVIEW_ZOOM, ProjectWebViewSettings.ZOOM_ONLY);
    }

    private void setupCache() {
        String[] labels = {
                Helper.getResString(R.string.webview_settings_cache_default),
                Helper.getResString(R.string.webview_settings_cache_else_network),
                Helper.getResString(R.string.webview_settings_cache_no_cache),
                Helper.getResString(R.string.webview_settings_cache_only)
        };
        binding.actCache.setSimpleItems(labels);
        bindChoice(binding.actCache, labels, CACHE_VALUES,
                ProjectSettings.SETTING_WEBVIEW_CACHE_MODE, "-1");
    }

    private void setupFileAccess() {
        String[] labels = {
                Helper.getResString(R.string.webview_settings_option_default),
                Helper.getResString(R.string.webview_settings_option_on),
                Helper.getResString(R.string.webview_settings_option_off)
        };
        binding.actFileAccess.setSimpleItems(labels);
        bindChoice(binding.actFileAccess, labels, FILE_ACCESS_VALUES,
                ProjectSettings.SETTING_WEBVIEW_ALLOW_FILE_ACCESS, "");
    }

    private void setupMixedContent() {
        String[] labels = {
                Helper.getResString(R.string.webview_settings_option_default),
                Helper.getResString(R.string.webview_settings_mixed_always),
                Helper.getResString(R.string.webview_settings_mixed_never),
                Helper.getResString(R.string.webview_settings_mixed_compat)
        };
        binding.actMixedContent.setSimpleItems(labels);
        bindChoice(binding.actMixedContent, labels, MIXED_CONTENT_VALUES,
                ProjectSettings.SETTING_WEBVIEW_MIXED_CONTENT, "");
    }

    private void bindChoice(com.google.android.material.textfield.MaterialAutoCompleteTextView view,
                            String[] labels, String[] values, String key, String defaultValue) {
        String stored = settings.getValue(key, defaultValue);
        int index = indexOf(values, stored);
        if (index < 0) {
            index = indexOf(values, defaultValue);
        }
        if (index >= 0) {
            view.setText(labels[index], false);
        }
        view.setOnItemClickListener((parent, v, position, id) -> {
            if (position >= 0 && position < values.length) {
                settings.setValue(key, values[position]);
            }
        });
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return -1;
    }

    private void setupReset() {
        binding.btnReset.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.webview_settings_reset)
                .setMessage(R.string.webview_settings_reset_confirm)
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.common_word_save, (dialog, which) -> resetToDefaults())
                .show());
    }

    private void resetToDefaults() {
        String[] keys = {
                ProjectSettings.SETTING_WEBVIEW_JAVASCRIPT,
                ProjectSettings.SETTING_WEBVIEW_DOM_STORAGE,
                ProjectSettings.SETTING_WEBVIEW_ZOOM,
                ProjectSettings.SETTING_WEBVIEW_CACHE_MODE,
                ProjectSettings.SETTING_WEBVIEW_MEDIA_NO_GESTURE,
                ProjectSettings.SETTING_WEBVIEW_TTS_BRIDGE,
                ProjectSettings.SETTING_WEBVIEW_TTS_RATE,
                ProjectSettings.SETTING_WEBVIEW_TTS_LANG,
                ProjectSettings.SETTING_WEBVIEW_TEXT_ZOOM,
                ProjectSettings.SETTING_WEBVIEW_DESKTOP_MODE,
                ProjectSettings.SETTING_WEBVIEW_ALLOW_FILE_ACCESS,
                ProjectSettings.SETTING_WEBVIEW_MIXED_CONTENT
        };
        for (String key : keys) {
            settings.setValue(key, "");
        }
        // Re-apply the defaults to the UI.
        binding.swJavascript.setChecked(true);
        binding.swDomStorage.setChecked(true);
        binding.swMediaNoGesture.setChecked(true);
        binding.swTtsBridge.setChecked(true);
        binding.swDesktopMode.setChecked(false);
        binding.etTtsRate.setText(ProjectWebViewSettings.DEFAULT_TTS_RATE);
        binding.etTtsLang.setText(ProjectWebViewSettings.DEFAULT_TTS_LANG);
        binding.etTextZoom.setText(String.valueOf(ProjectWebViewSettings.DEFAULT_TEXT_ZOOM));
        binding.actZoom.setText(Helper.getResString(R.string.webview_settings_zoom_only), false);
        binding.actCache.setText(Helper.getResString(R.string.webview_settings_cache_default), false);
        binding.actFileAccess.setText(Helper.getResString(R.string.webview_settings_option_default), false);
        binding.actMixedContent.setText(Helper.getResString(R.string.webview_settings_option_default), false);
    }
}
