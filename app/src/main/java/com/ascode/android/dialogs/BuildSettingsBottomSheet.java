package com.ascode.android.dialogs;

import static mod.hey.studios.build.BuildSettings.SETTING_ANDROID_JAR_PATH;
import static mod.hey.studios.build.BuildSettings.SETTING_CLASSPATH;
import static mod.hey.studios.build.BuildSettings.SETTING_DEXER;
import static mod.hey.studios.build.BuildSettings.SETTING_DEXER_D8;
import static mod.hey.studios.build.BuildSettings.SETTING_DEXER_DX;
import static mod.hey.studios.build.BuildSettings.SETTING_ENABLE_LOGCAT;
import static mod.hey.studios.build.BuildSettings.SETTING_ENABLE_KMP_GRADLE_BRIDGE;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION_10;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION_11;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION_1_7;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION_1_8;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION_1_9;
import static mod.hey.studios.build.BuildSettings.SETTING_KMP_GRADLE_TIMEOUT_MS;
import static mod.hey.studios.build.BuildSettings.SETTING_NO_HTTP_LEGACY;
import static mod.hey.studios.build.BuildSettings.SETTING_NO_WARNINGS;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.util.Helper;
import com.ascode.android.R;
import com.ascode.android.databinding.ProjectConfigLayoutBinding;
import com.ascode.android.utility.AscodeUtil;

public class BuildSettingsBottomSheet extends BottomSheetDialogFragment {
    public static final String TAG = BuildSettingsBottomSheet.class.getSimpleName();
    private static int totalViews = 0;

    private static final int VIEW_ANDROIR_JAR_PATH = totalViews++;
    private static final int VIEW_CLASS_PATH = totalViews++;
    private static final int VIEW_DEXER = totalViews++;
    private static final int VIEW_JAVA_VERSION = totalViews++;
    private static final int VIEW_NO_WARNINGS = totalViews++;
    private static final int VIEW_NO_HTTP_LEGACY = totalViews++;
    private static final int VIEW_ENABLE_LOGCAT = totalViews++;
    private static final int VIEW_ENABLE_KMP_GRADLE_BRIDGE = totalViews++;
    private static final int VIEW_KMP_GRADLE_TIMEOUT = totalViews++;
    private View[] views;

    private ProjectConfigLayoutBinding binding;
    private BuildSettings projectSettings;

    public static BuildSettingsBottomSheet newInstance(String sc_id) {
        BuildSettingsBottomSheet sheet = new BuildSettingsBottomSheet();
        Bundle arguments = new Bundle();
        arguments.putString("sc_id", sc_id);
        sheet.setArguments(arguments);
        return sheet;
    }

    public static String[] getAvailableJavaVersions() {
        return new String[]{SETTING_JAVA_VERSION_1_7, SETTING_JAVA_VERSION_1_8, SETTING_JAVA_VERSION_1_9, SETTING_JAVA_VERSION_10, SETTING_JAVA_VERSION_11,};
    }

    public static void handleJavaVersionChange(String choice) {
        if (!choice.equals(SETTING_JAVA_VERSION_1_7)) {
            AscodeUtil.toast(Helper.getResString(R.string.auto4_d8_hint));
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle arguments = getArguments();
        projectSettings = new BuildSettings(arguments.getString("sc_id"));
        views = new View[totalViews];
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = ProjectConfigLayoutBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews();

        binding.noWarnings.setOnClickListener(v -> binding.cbNoWarnings.performClick());
        binding.noHttpLegacy.setOnClickListener(v -> binding.cbNoHttpLegacy.performClick());
        binding.enableLogcat.setOnClickListener(v -> binding.cbEnableLogcat.performClick());
        binding.enableKmpGradleBridge.setOnClickListener(v -> binding.cbEnableKmpGradleBridge.performClick());

        binding.tilAndroidJar.getEditText().setText(projectSettings.getValue(SETTING_ANDROID_JAR_PATH, ""));
        binding.tilClasspath.getEditText().setText(projectSettings.getValue(SETTING_CLASSPATH, ""));
        binding.tilKmpGradleTimeout.getEditText().setText(projectSettings.getValue(SETTING_KMP_GRADLE_TIMEOUT_MS, "30000"));

        setRadioGroupOptions(binding.rgDexer, new String[]{"Dx", "D8"}, SETTING_DEXER, SETTING_DEXER_DX);
        setRadioGroupOptions(binding.rgJavaVersion, getAvailableJavaVersions(), SETTING_JAVA_VERSION, SETTING_JAVA_VERSION_1_7);

        // The dexer and the Java level are mutually dependent (Java 8+ can only be compiled by
        // D8). Normalize any legacy/invalid pair that was stored before this fix so the UI and
        // the persisted model only ever hold a compilable combination.
        normalizeDexerJavaCombination();

        // Persist the free-form fields as they are edited as well, so that no change is lost
        // when the sheet is dismissed without pressing Save (tap outside / back / drag down).
        persistOnTextChange(binding.tilAndroidJar.getEditText(), SETTING_ANDROID_JAR_PATH);
        persistOnTextChange(binding.tilClasspath.getEditText(), SETTING_CLASSPATH);
        persistOnTextChange(binding.tilKmpGradleTimeout.getEditText(), SETTING_KMP_GRADLE_TIMEOUT_MS);

        setCheckboxValue(binding.cbNoWarnings, SETTING_NO_WARNINGS, true);
        setCheckboxValue(binding.cbNoHttpLegacy, SETTING_NO_HTTP_LEGACY, false);
        setCheckboxValue(binding.cbEnableLogcat, SETTING_ENABLE_LOGCAT, true);
        setCheckboxValue(binding.cbEnableKmpGradleBridge, SETTING_ENABLE_KMP_GRADLE_BRIDGE, false);

        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.btnSave.setOnClickListener(v -> {
            projectSettings.setValues(views);
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void initializeViews() {
        binding.tilAndroidJar.getEditText().setTag(SETTING_ANDROID_JAR_PATH);
        binding.tilClasspath.getEditText().setTag(SETTING_CLASSPATH);
        binding.rgDexer.setTag(SETTING_DEXER);
        binding.rgJavaVersion.setTag(SETTING_JAVA_VERSION);
        binding.cbNoWarnings.setTag(SETTING_NO_WARNINGS);
        binding.cbNoHttpLegacy.setTag(SETTING_NO_HTTP_LEGACY);
        binding.cbEnableLogcat.setTag(SETTING_ENABLE_LOGCAT);
        binding.cbEnableKmpGradleBridge.setTag(SETTING_ENABLE_KMP_GRADLE_BRIDGE);
        binding.tilKmpGradleTimeout.getEditText().setTag(SETTING_KMP_GRADLE_TIMEOUT_MS);

        views[VIEW_ANDROIR_JAR_PATH] = binding.tilAndroidJar.getEditText();
        views[VIEW_CLASS_PATH] = binding.tilClasspath.getEditText();
        views[VIEW_DEXER] = binding.rgDexer;
        views[VIEW_ENABLE_LOGCAT] = binding.cbEnableLogcat;
        views[VIEW_ENABLE_KMP_GRADLE_BRIDGE] = binding.cbEnableKmpGradleBridge;
        views[VIEW_KMP_GRADLE_TIMEOUT] = binding.tilKmpGradleTimeout.getEditText();
        views[VIEW_JAVA_VERSION] = binding.rgJavaVersion;
        views[VIEW_NO_HTTP_LEGACY] = binding.cbNoHttpLegacy;
        views[VIEW_NO_WARNINGS] = binding.cbNoWarnings;
    }

    private void setRadioGroupOptions(RadioGroup radioGroup, String[] options, String key, String defaultValue) {
        radioGroup.removeAllViews();
        String value = projectSettings.getValue(key, defaultValue);
        for (String option : options) {
            RadioButton radioButton = new RadioButton(radioGroup.getContext());
            radioButton.setText(option);
            radioButton.setId(View.generateViewId());
            radioButton.setLayoutParams(new RadioGroup.LayoutParams(0, -2, 1f));
            if (value.equals(option)) {
                radioButton.setChecked(true);
            }
            radioButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!isChecked) return;
                // Persist the choice immediately so it survives dismissing the sheet
                // (tapping outside, back, or dragging it down), not only the Save button.
                projectSettings.setValue(key, option);
                if (key.equals(SETTING_JAVA_VERSION)) {
                    handleJavaVersionChange(option);
                    if (!option.equals(SETTING_JAVA_VERSION_1_7)) {
                        // Java 8+ cannot be compiled by Dx: keep the pair valid.
                        selectRadioGroupValue(binding.rgDexer, SETTING_DEXER_D8);
                    }
                } else if (key.equals(SETTING_DEXER)
                        && option.equals(SETTING_DEXER_DX)
                        && !projectSettings.getValue(SETTING_JAVA_VERSION, SETTING_JAVA_VERSION_1_7)
                                .equals(SETTING_JAVA_VERSION_1_7)) {
                    // Dx cannot compile Java 8+ code: fall back to Java 1.7.
                    selectRadioGroupValue(binding.rgJavaVersion, SETTING_JAVA_VERSION_1_7);
                }
            });
            radioGroup.addView(radioButton);
        }
    }

    /**
     * Checks the radio button of {@code radioGroup} whose label matches {@code value}, which in
     * turn persists the corresponding setting through its own change listener.
     */
    private void selectRadioGroupValue(RadioGroup radioGroup, String value) {
        for (int i = 0; i < radioGroup.getChildCount(); i++) {
            View child = radioGroup.getChildAt(i);
            if (child instanceof RadioButton radioButton && value.contentEquals(radioButton.getText())) {
                if (!radioButton.isChecked()) {
                    radioButton.setChecked(true);
                }
                return;
            }
        }
    }

    /**
     * Keeps the mutually dependent dexer/Java pair consistent: Java 8+ requires D8, so an invalid
     * stored combination (Dx with a Java level above 1.7) is repaired to use D8.
     */
    private void normalizeDexerJavaCombination() {
        String javaVersion = projectSettings.getValue(SETTING_JAVA_VERSION, SETTING_JAVA_VERSION_1_7);
        String dexer = projectSettings.getValue(SETTING_DEXER, SETTING_DEXER_DX);
        if (!javaVersion.equals(SETTING_JAVA_VERSION_1_7) && !dexer.equals(SETTING_DEXER_D8)) {
            projectSettings.setValue(SETTING_DEXER, SETTING_DEXER_D8);
            selectRadioGroupValue(binding.rgDexer, SETTING_DEXER_D8);
        }
    }

    /**
     * Persists an {@link EditText}'s content on every change, so the value is never lost even if
     * the sheet is dismissed without pressing Save.
     */
    private void persistOnTextChange(EditText editText, String key) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                projectSettings.setValue(key, s.toString());
            }
        });
    }

    private void setCheckboxValue(CheckBox checkBox, String key, boolean defaultValue) {
        String value = projectSettings.getValue(key, defaultValue ? "true" : "false");
        checkBox.setChecked(value.equals("true"));

        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Persist immediately so the state is not lost when the sheet is dismissed
            // without pressing Save.
            projectSettings.setValue(key, Boolean.toString(isChecked));
            if (isChecked) {
                if (key.equals(SETTING_NO_HTTP_LEGACY)) {
                    AscodeUtil.toast(Helper.getResString(R.string.auto4_requestnetwork_hint));
                } else if (key.equals(SETTING_ENABLE_KMP_GRADLE_BRIDGE)) {
                    AscodeUtil.toast(Helper.getResString(R.string.auto4_kmp_bridge_enabled));
                }
            }
        });
    }
}
