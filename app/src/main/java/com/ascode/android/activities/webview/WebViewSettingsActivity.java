package com.ascode.android.activities.webview;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import mod.hey.studios.project.ProjectSettings;
import mod.hey.studios.util.Helper;
import com.ascode.android.R;
import com.ascode.android.databinding.ActivityWebviewSettingsBinding;
import com.ascode.android.piper.PiperCatalog;
import com.ascode.android.piper.PiperInstaller;
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

    private final ExecutorService ttsExecutor = Executors.newSingleThreadExecutor();
    private final Handler ttsHandler = new Handler(Looper.getMainLooper());
    private boolean ttsBusy;

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
        setupSwitch(binding.swTtsDiagnostics, ProjectSettings.SETTING_WEBVIEW_TTS_DIAGNOSTICS, false);
        setupSwitch(binding.swTtsPiper, ProjectSettings.SETTING_WEBVIEW_TTS_ENGINE_PIPER, false);
        setupSwitch(binding.swDesktopMode, ProjectSettings.SETTING_WEBVIEW_DESKTOP_MODE, false);
        setupText(binding.etTtsRate, ProjectSettings.SETTING_WEBVIEW_TTS_RATE,
                ProjectWebViewSettings.DEFAULT_TTS_RATE);
        setupText(binding.etTtsLang, ProjectSettings.SETTING_WEBVIEW_TTS_LANG,
                ProjectWebViewSettings.DEFAULT_TTS_LANG);
        setupText(binding.etTextZoom, ProjectSettings.SETTING_WEBVIEW_TEXT_ZOOM,
                String.valueOf(ProjectWebViewSettings.DEFAULT_TEXT_ZOOM));
        setupPiper();
        hidePiperSection();
        setupReset();
    }

    /**
     * The built-in offline Piper engine was retired at the owner's request: its whole section is
     * hidden from this screen and {@link ProjectWebViewSettings#isTtsPiperEnabled()} always returns
     * false, so no project can enable it and nothing extra is ever packaged into compiled apps.
     */
    private void hidePiperSection() {
        int[] piperViews = new int[]{
                R.id.sw_tts_piper,
                R.id.til_tts_voice,
                R.id.tv_tts_license,
                R.id.pb_tts,
                R.id.tv_tts_status,
                R.id.btn_tts_download,
                R.id.btn_tts_import,
        };
        for (int id : piperViews) {
            View view = findViewById(id);
            if (view != null) {
                view.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ttsExecutor.shutdownNow();
    }

    // ------------------------------------------------------------------
    // Built-in offline Piper engine
    // ------------------------------------------------------------------

    private void setupPiper() {
        List<PiperCatalog.Voice> voices = PiperCatalog.voices();
        String[] labels = new String[voices.size()];
        for (int i = 0; i < voices.size(); i++) {
            PiperCatalog.Voice voice = voices.get(i);
            labels[i] = voice.lang + " · " + voice.id + " (" + PiperCatalog.humanSize(voice.sizeBytes) + ")";
        }
        binding.actTtsVoice.setSimpleItems(labels);

        String stored = settings.getValue(ProjectSettings.SETTING_WEBVIEW_TTS_PIPER_VOICE,
                PiperCatalog.DEFAULT_VOICE_ID);
        int index = indexOfVoiceId(voices, stored);
        if (index < 0) {
            index = indexOfVoiceId(voices, PiperCatalog.DEFAULT_VOICE_ID);
        }
        if (index >= 0) {
            binding.actTtsVoice.setText(labels[index], false);
        }
        binding.actTtsVoice.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < voices.size()) {
                settings.setValue(ProjectSettings.SETTING_WEBVIEW_TTS_PIPER_VOICE, voices.get(position).id);
                updateTtsVoiceUi();
            }
        });

        binding.btnTtsDownload.setOnClickListener(v -> startTtsDownload());
        binding.btnTtsImport.setOnClickListener(v -> startTtsImport());
        updateTtsVoiceUi();
    }

    private int indexOfVoiceId(List<PiperCatalog.Voice> voices, String id) {
        for (int i = 0; i < voices.size(); i++) {
            if (voices.get(i).id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private PiperCatalog.Voice selectedVoice() {
        String id = settings.getValue(ProjectSettings.SETTING_WEBVIEW_TTS_PIPER_VOICE,
                PiperCatalog.DEFAULT_VOICE_ID);
        PiperCatalog.Voice voice = PiperCatalog.findById(id);
        return voice != null ? voice : PiperCatalog.defaultVoice();
    }

    private void updateTtsVoiceUi() {
        PiperCatalog.Voice voice = selectedVoice();
        binding.tvTtsLicense.setText(getString(R.string.webview_settings_tts_license, voice.license));

        boolean runtimeInstalled = PiperInstaller.isRuntimeInstalled(sc_id);
        boolean voiceInstalled = PiperInstaller.isVoiceInstalled(sc_id)
                && voice.id.equals(PiperInstaller.installedVoiceId(sc_id));

        if (!runtimeInstalled) {
            long download = PiperCatalog.RUNTIME_AAR_SIZE + PiperCatalog.KOTLIN_STDLIB_SIZE;
            binding.tvTtsStatus.setText(getString(R.string.webview_settings_tts_status_runtime_missing,
                    PiperCatalog.humanSize(download)));
        } else if (voiceInstalled) {
            binding.tvTtsStatus.setText(getString(R.string.webview_settings_tts_status_installed,
                    PiperCatalog.humanSize(PiperInstaller.installedVoiceSize(sc_id))));
        } else {
            binding.tvTtsStatus.setText(getString(R.string.webview_settings_tts_status_not_installed,
                    PiperCatalog.humanSize(voice.sizeBytes)));
        }
    }

    private void setTtsBusy(boolean busy) {
        ttsBusy = busy;
        binding.btnTtsDownload.setEnabled(!busy);
        binding.btnTtsImport.setEnabled(!busy);
        binding.actTtsVoice.setEnabled(!busy);
        binding.swTtsPiper.setEnabled(!busy);
        if (!busy) {
            binding.pbTts.setVisibility(View.GONE);
        }
    }

    private void startTtsDownload() {
        if (ttsBusy) {
            return;
        }
        final PiperCatalog.Voice voice = selectedVoice();
        setTtsBusy(true);
        binding.pbTts.setVisibility(View.VISIBLE);
        binding.pbTts.setIndeterminate(true);
        ttsExecutor.execute(() -> {
            try {
                PiperInstaller.installRuntime(sc_id, this::onTtsProgress, false);
                PiperInstaller.installVoice(sc_id, voice, null, this::onTtsProgress);
                ttsHandler.post(() -> {
                    setTtsBusy(false);
                    Toast.makeText(this, R.string.webview_settings_tts_done, Toast.LENGTH_SHORT).show();
                    updateTtsVoiceUi();
                });
            } catch (Exception e) {
                ttsHandler.post(() -> {
                    setTtsBusy(false);
                    Toast.makeText(this, getString(R.string.webview_settings_tts_error, String.valueOf(e.getMessage())),
                            Toast.LENGTH_LONG).show();
                    updateTtsVoiceUi();
                });
            }
        });
    }

    private void startTtsImport() {
        if (ttsBusy) {
            return;
        }
        FilePickerOptions options = new FilePickerOptions();
        options.setTitle(getString(R.string.webview_settings_tts_import_title));
        options.setExtensions(new String[]{"bz2"});
        FilePickerCallback callback = new FilePickerCallback() {
            @Override
            public void onFileSelected(File file) {
                PiperCatalog.Voice voice = PiperCatalog.findByFileName(file.getName());
                if (voice == null) {
                    Toast.makeText(WebViewSettingsActivity.this,
                            R.string.webview_settings_tts_import_mismatch, Toast.LENGTH_LONG).show();
                    return;
                }
                settings.setValue(ProjectSettings.SETTING_WEBVIEW_TTS_PIPER_VOICE, voice.id);
                int index = indexOfVoiceId(PiperCatalog.voices(), voice.id);
                if (index >= 0) {
                    PiperCatalog.Voice v = PiperCatalog.voices().get(index);
                    binding.actTtsVoice.setText(v.lang + " · " + v.id + " ("
                            + PiperCatalog.humanSize(v.sizeBytes) + ")", false);
                }
                installImportedVoice(voice, file);
            }
        };
        FilePickerDialogFragment dialog = new FilePickerDialogFragment(options, callback);
        dialog.show(getSupportFragmentManager(), "piperVoicePicker");
    }

    private void installImportedVoice(PiperCatalog.Voice voice, File archive) {
        setTtsBusy(true);
        binding.pbTts.setVisibility(View.VISIBLE);
        binding.pbTts.setIndeterminate(true);
        ttsExecutor.execute(() -> {
            try {
                PiperInstaller.installRuntime(sc_id, this::onTtsProgress, false);
                PiperInstaller.installVoice(sc_id, voice, archive, this::onTtsProgress);
                ttsHandler.post(() -> {
                    setTtsBusy(false);
                    Toast.makeText(this, R.string.webview_settings_tts_done, Toast.LENGTH_SHORT).show();
                    updateTtsVoiceUi();
                });
            } catch (Exception e) {
                ttsHandler.post(() -> {
                    setTtsBusy(false);
                    Toast.makeText(this, getString(R.string.webview_settings_tts_error, String.valueOf(e.getMessage())),
                            Toast.LENGTH_LONG).show();
                    updateTtsVoiceUi();
                });
            }
        });
    }

    private void onTtsProgress(String stage, long done, long total) {
        ttsHandler.post(() -> {
            int pct = total > 0 ? (int) Math.min(100, Math.max(0, done * 100 / total)) : -1;
            switch (stage) {
                case "runtime-aar":
                case "runtime-stdlib":
                case "voice-download": {
                    binding.pbTts.setVisibility(View.VISIBLE);
                    binding.pbTts.setIndeterminate(false);
                    if (pct >= 0) {
                        binding.pbTts.setProgressCompat(pct, true);
                    }
                    binding.tvTtsStatus.setText(stage.startsWith("voice")
                            ? getString(R.string.webview_settings_tts_progress_download, Math.max(pct, 0))
                            : getString(R.string.webview_settings_tts_progress_runtime, Math.max(pct, 0)));
                    break;
                }
                case "runtime-dex":
                    showIndeterminateTts(R.string.webview_settings_tts_progress_dex);
                    break;
                case "voice-verify":
                case "voice-extract":
                    showIndeterminateTts(R.string.webview_settings_tts_progress_extract);
                    break;
                case "voice-prune":
                    showIndeterminateTts(R.string.webview_settings_tts_progress_prune);
                    break;
                default:
                    break;
            }
        });
    }

    private void showIndeterminateTts(int stringRes) {
        binding.pbTts.setVisibility(View.VISIBLE);
        binding.pbTts.setIndeterminate(true);
        binding.tvTtsStatus.setText(stringRes);
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
                ProjectSettings.SETTING_WEBVIEW_TTS_DIAGNOSTICS,
                ProjectSettings.SETTING_WEBVIEW_TTS_ENGINE_PIPER,
                ProjectSettings.SETTING_WEBVIEW_TTS_PIPER_VOICE,
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
        binding.swTtsDiagnostics.setChecked(false);
        binding.swTtsPiper.setChecked(false);
        binding.actTtsVoice.setText(PiperCatalog.voices().get(0).lang + " · "
                + PiperCatalog.voices().get(0).id + " ("
                + PiperCatalog.humanSize(PiperCatalog.voices().get(0).sizeBytes) + ")", false);
        updateTtsVoiceUi();
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
