package com.ascode.android.activities.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import mod.hey.studios.util.Helper;
import com.ascode.android.R;
import com.ascode.android.databinding.PreferenceActivityBinding;
import com.ascode.android.featureflags.FeatureFlags;
import com.ascode.android.metrics.AccessibilityIssueReportStore;
import com.ascode.android.metrics.BuildMetricsStore;
import com.ascode.android.metrics.EditorPerformanceMetricsStore;
import com.ascode.android.metrics.KmpBuildPerformanceMetricsStore;
import com.ascode.android.metrics.KpiDashboardReleaseGatesResult;
import com.ascode.android.metrics.KpiDashboardStore;
import com.ascode.android.metrics.StartupPerformanceMetricsStore;
import com.ascode.android.ui.layout.AdaptiveLayoutPolicy;
import com.ascode.android.ui.layout.AdaptiveLayoutSnapshot;

public class FeatureFlagsActivity extends BaseAppCompatActivity {

    private PreferenceActivityBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = PreferenceActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.topAppBar.setTitle(R.string.app_settings_feature_flags);
        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));

        onAdaptiveLayoutChanged(getAdaptiveLayoutSnapshot());

        getSupportFragmentManager().beginTransaction()
                .replace(binding.fragmentContainer.getId(), new FeatureFlagsFragment())
                .commit();
    }

    @Override
    protected void onAdaptiveLayoutChanged(@androidx.annotation.NonNull AdaptiveLayoutSnapshot snapshot) {
        if (binding == null) {
            return;
        }

        int horizontalPaddingPx = AdaptiveLayoutPolicy.dpToPx(this, snapshot.contentHorizontalPaddingDp);
        binding.contentLayout.setPaddingRelative(
                horizontalPaddingPx,
                binding.contentLayout.getPaddingTop(),
                horizontalPaddingPx,
                binding.contentLayout.getPaddingBottom()
        );
    }

    public static class FeatureFlagsFragment extends PreferenceFragmentCompat {

        private static final String KEY_BUILD_METRICS_SUMMARY = "build_metrics_summary";
        private static final String KEY_BUILD_METRICS_LAST = "build_metrics_last";
        private static final String KEY_BUILD_METRICS_STAGES = "build_metrics_stages";
        private static final String KEY_BUILD_METRICS_RESET = "build_metrics_reset";
        private static final String KEY_KMP_BUILD_METRICS_SUMMARY = "kmp_build_metrics_summary";
        private static final String KEY_KMP_BUILD_METRICS_TREND = "kmp_build_metrics_trend";
        private static final String KEY_KMP_BUILD_METRICS_STAGES = "kmp_build_metrics_stages";
        private static final String KEY_KMP_BUILD_METRICS_RESET = "kmp_build_metrics_reset";
        private static final String KEY_KMP_DEVELOPER_DOCS_ENTRY = "kmp_developer_docs_entry";
        private static final String KEY_EDITOR_METRICS_SUMMARY = "editor_metrics_summary";
        private static final String KEY_EDITOR_METRICS_LSP = "editor_metrics_lsp";
        private static final String KEY_EDITOR_METRICS_DIAGNOSTICS = "editor_metrics_diagnostics";
        private static final String KEY_EDITOR_METRICS_RESET = "editor_metrics_reset";
        private static final String KEY_STARTUP_METRICS_SUMMARY = "startup_metrics_summary";
        private static final String KEY_STARTUP_METRICS_LAST = "startup_metrics_last";
        private static final String KEY_STARTUP_METRICS_THRESHOLD = "startup_metrics_threshold";
        private static final String KEY_STARTUP_METRICS_RESET = "startup_metrics_reset";
        private static final String KEY_ACCESSIBILITY_METRICS_SUMMARY = "accessibility_metrics_summary";
        private static final String KEY_ACCESSIBILITY_METRICS_LAST = "accessibility_metrics_last";
        private static final String KEY_ACCESSIBILITY_METRICS_RESET = "accessibility_metrics_reset";
        private static final String KEY_KPI_DASHBOARD_SUMMARY = "kpi_dashboard_summary";
        private static final String KEY_KPI_RELEASE_GATES = "kpi_release_gates";
        private static final String KEY_KPI_DASHBOARD_RESET = "kpi_dashboard_reset";

        private Preference buildSummaryPreference;
        private Preference buildLastPreference;
        private Preference buildStagesPreference;
        private Preference kmpBuildSummaryPreference;
        private Preference kmpBuildTrendPreference;
        private Preference kmpBuildStagesPreference;
        private Preference editorSummaryPreference;
        private Preference editorLspPreference;
        private Preference editorDiagnosticsPreference;
        private Preference startupSummaryPreference;
        private Preference startupLastPreference;
        private Preference startupThresholdPreference;
        private Preference accessibilitySummaryPreference;
        private Preference accessibilityLastPreference;
        private Preference kpiSummaryPreference;
        private Preference kpiReleaseGatesPreference;

        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            getPreferenceManager().setPreferenceDataStore(new FeatureFlagsPreferenceDataStore(requireContext().getApplicationContext()));
            setPreferencesFromResource(R.xml.preferences_feature_flags, rootKey);

            buildSummaryPreference = findPreference(KEY_BUILD_METRICS_SUMMARY);
            buildLastPreference = findPreference(KEY_BUILD_METRICS_LAST);
            buildStagesPreference = findPreference(KEY_BUILD_METRICS_STAGES);
            kmpBuildSummaryPreference = findPreference(KEY_KMP_BUILD_METRICS_SUMMARY);
            kmpBuildTrendPreference = findPreference(KEY_KMP_BUILD_METRICS_TREND);
            kmpBuildStagesPreference = findPreference(KEY_KMP_BUILD_METRICS_STAGES);
            editorSummaryPreference = findPreference(KEY_EDITOR_METRICS_SUMMARY);
            editorLspPreference = findPreference(KEY_EDITOR_METRICS_LSP);
            editorDiagnosticsPreference = findPreference(KEY_EDITOR_METRICS_DIAGNOSTICS);
            startupSummaryPreference = findPreference(KEY_STARTUP_METRICS_SUMMARY);
            startupLastPreference = findPreference(KEY_STARTUP_METRICS_LAST);
            startupThresholdPreference = findPreference(KEY_STARTUP_METRICS_THRESHOLD);
            accessibilitySummaryPreference = findPreference(KEY_ACCESSIBILITY_METRICS_SUMMARY);
            accessibilityLastPreference = findPreference(KEY_ACCESSIBILITY_METRICS_LAST);
            kpiSummaryPreference = findPreference(KEY_KPI_DASHBOARD_SUMMARY);
            kpiReleaseGatesPreference = findPreference(KEY_KPI_RELEASE_GATES);

            Preference resetPreference = findPreference(KEY_BUILD_METRICS_RESET);
            if (resetPreference != null) {
                resetPreference.setOnPreferenceClickListener(preference -> {
                    BuildMetricsStore.clear(requireContext().getApplicationContext());
                    refreshBuildMetrics();
                    return true;
                });
            }

            Preference resetEditorPreference = findPreference(KEY_EDITOR_METRICS_RESET);
            if (resetEditorPreference != null) {
                resetEditorPreference.setOnPreferenceClickListener(preference -> {
                    EditorPerformanceMetricsStore.clear(requireContext().getApplicationContext());
                    refreshEditorMetrics();
                    return true;
                });
            }

            Preference resetKmpBuildPreference = findPreference(KEY_KMP_BUILD_METRICS_RESET);
            if (resetKmpBuildPreference != null) {
                resetKmpBuildPreference.setOnPreferenceClickListener(preference -> {
                    KmpBuildPerformanceMetricsStore.clear(requireContext().getApplicationContext());
                    refreshKmpBuildMetrics();
                    refreshKpiDashboardMetrics();
                    return true;
                });
            }

            Preference kmpDeveloperDocsPreference = findPreference(KEY_KMP_DEVELOPER_DOCS_ENTRY);
            if (kmpDeveloperDocsPreference != null) {
                kmpDeveloperDocsPreference.setOnPreferenceClickListener(preference -> {
                    showKmpDeveloperDocsDialog();
                    return true;
                });
            }

            Preference resetStartupPreference = findPreference(KEY_STARTUP_METRICS_RESET);
            if (resetStartupPreference != null) {
                resetStartupPreference.setOnPreferenceClickListener(preference -> {
                    StartupPerformanceMetricsStore.clear(requireContext().getApplicationContext());
                    refreshStartupMetrics();
                    return true;
                });
            }

            Preference resetAccessibilityPreference = findPreference(KEY_ACCESSIBILITY_METRICS_RESET);
            if (resetAccessibilityPreference != null) {
                resetAccessibilityPreference.setOnPreferenceClickListener(preference -> {
                    AccessibilityIssueReportStore.clear(requireContext().getApplicationContext());
                    refreshAccessibilityMetrics();
                    refreshKpiDashboardMetrics();
                    return true;
                });
            }

            Preference resetKpiPreference = findPreference(KEY_KPI_DASHBOARD_RESET);
            if (resetKpiPreference != null) {
                resetKpiPreference.setOnPreferenceClickListener(preference -> {
                    KpiDashboardStore.clearAllMetrics(requireContext().getApplicationContext());
                    refreshBuildMetrics();
                    refreshKmpBuildMetrics();
                    refreshEditorMetrics();
                    refreshStartupMetrics();
                    refreshAccessibilityMetrics();
                    refreshKpiDashboardMetrics();
                    return true;
                });
            }

            refreshBuildMetrics();
            refreshKmpBuildMetrics();
            refreshEditorMetrics();
            refreshStartupMetrics();
            refreshAccessibilityMetrics();
            refreshKpiDashboardMetrics();
        }

        @Override
        public void onResume() {
            super.onResume();
            refreshBuildMetrics();
            refreshKmpBuildMetrics();
            refreshEditorMetrics();
            refreshStartupMetrics();
            refreshAccessibilityMetrics();
            refreshKpiDashboardMetrics();
        }

        private void refreshBuildMetrics() {
            BuildMetricsStore.Snapshot snapshot = BuildMetricsStore.snapshot(requireContext().getApplicationContext());

            if (snapshot.totalCount == 0) {
                if (buildSummaryPreference != null) {
                    buildSummaryPreference.setSummary(R.string.auto_java_ff_build_no_data);
                }
                if (buildLastPreference != null) {
                    buildLastPreference.setSummary(R.string.auto_java_ff_no_build);
                }
                if (buildStagesPreference != null) {
                    buildStagesPreference.setSummary(R.string.auto_java_ff_no_stage_data);
                }
                return;
            }

            if (buildSummaryPreference != null) {
                buildSummaryPreference.setSummary(getString(R.string.auto_java_ff_build_summary, snapshot.totalCount, snapshot.successCount, snapshot.failureCount, snapshot.canceledCount, snapshot.coldCount, snapshot.incrementalCount, BuildMetricsStore.formatPercent(snapshot.cacheHitEstimateRate), snapshot.cacheHitEstimateCount, snapshot.cacheHitEstimateCount + snapshot.cacheMissEstimateCount, BuildMetricsStore.formatDuration(snapshot.averageSuccessDurationMs)));
            }

            if (buildLastPreference != null) {
                String status = snapshot.lastCanceled ? getString(R.string.auto_java_ff_status_canceled) : (snapshot.lastSuccess ? getString(R.string.auto_java_ff_status_success) : getString(R.string.auto_java_ff_status_failed));
                buildLastPreference.setSummary(getString(R.string.auto_java_ff_build_last, snapshot.lastType, snapshot.lastProfile, status, BuildMetricsStore.formatDuration(snapshot.lastDurationMs)));
            }

            if (buildStagesPreference != null) {
                buildStagesPreference.setSummary(
                        BuildMetricsStore.formatStageSummary(snapshot.lastStageDurations, 4)
                );
            }
        }

        private void refreshEditorMetrics() {
            EditorPerformanceMetricsStore.Snapshot snapshot =
                    EditorPerformanceMetricsStore.snapshot(requireContext().getApplicationContext());

            if (snapshot.totalSamples == 0) {
                if (editorSummaryPreference != null) {
                    editorSummaryPreference.setSummary(R.string.auto_java_ff_editor_no_data);
                }
                if (editorLspPreference != null) {
                    editorLspPreference.setSummary(R.string.auto_java_ff_no_lsp);
                }
                if (editorDiagnosticsPreference != null) {
                    editorDiagnosticsPreference.setSummary(R.string.auto_java_ff_no_diagnostics);
                }
                return;
            }

            if (editorSummaryPreference != null) {
                int lspSamples = snapshot.completion.count + snapshot.definition.count + snapshot.references.count;
                int diagnosticsSamples = snapshot.diagnosticsAnalyze.count
                        + snapshot.diagnosticsRender.count
                        + snapshot.diagnosticsPublishToRender.count;
                editorSummaryPreference.setSummary(getString(R.string.auto_java_ff_editor_summary, snapshot.totalSamples, lspSamples, diagnosticsSamples));
            }

            if (editorLspPreference != null) {
                String lspSummary = EditorPerformanceMetricsStore.formatOperation("completion", snapshot.completion)
                        + "\n"
                        + EditorPerformanceMetricsStore.formatOperation("definition", snapshot.definition)
                        + "\n"
                        + EditorPerformanceMetricsStore.formatOperation("references", snapshot.references);
                editorLspPreference.setSummary(lspSummary);
            }

            if (editorDiagnosticsPreference != null) {
                String diagnosticsSummary =
                        EditorPerformanceMetricsStore.formatOperation("analyze", snapshot.diagnosticsAnalyze)
                                + "\n"
                                + EditorPerformanceMetricsStore.formatOperation("render", snapshot.diagnosticsRender)
                                + "\n"
                                + EditorPerformanceMetricsStore.formatOperation("publish->render", snapshot.diagnosticsPublishToRender);
                editorDiagnosticsPreference.setSummary(diagnosticsSummary);
            }
        }

        private void refreshKmpBuildMetrics() {
            KmpBuildPerformanceMetricsStore.Snapshot snapshot =
                    KmpBuildPerformanceMetricsStore.snapshot(requireContext().getApplicationContext());

            if (snapshot.totalRuns == 0) {
                if (kmpBuildSummaryPreference != null) {
                    kmpBuildSummaryPreference.setSummary(R.string.auto_java_ff_kmp_no_data);
                }
                if (kmpBuildTrendPreference != null) {
                    kmpBuildTrendPreference.setSummary(R.string.auto_java_ff_no_trend);
                }
                if (kmpBuildStagesPreference != null) {
                    kmpBuildStagesPreference.setSummary(R.string.auto_java_ff_no_stage_data);
                }
                return;
            }

            if (kmpBuildSummaryPreference != null) {
                kmpBuildSummaryPreference.setSummary(getString(R.string.auto_java_ff_kmp_summary, snapshot.totalRuns, snapshot.coldRuns, snapshot.incrementalRuns, snapshot.lastProfile, KmpBuildPerformanceMetricsStore.formatDuration(snapshot.lastDurationMs)));
            }

            if (kmpBuildTrendPreference != null) {
                String trendSummary = KmpBuildPerformanceMetricsStore.formatOperation("total", snapshot.totalDurationTrend)
                        + "\n"
                        + KmpBuildPerformanceMetricsStore.formatTargetTrend(snapshot.targetDurationTrends, 3);
                kmpBuildTrendPreference.setSummary(trendSummary);
            }

            if (kmpBuildStagesPreference != null) {
                kmpBuildStagesPreference.setSummary(
                        KmpBuildPerformanceMetricsStore.formatStageSummary(snapshot.lastTargetStages, 4)
                );
            }
        }

        private void refreshStartupMetrics() {
            StartupPerformanceMetricsStore.Snapshot snapshot =
                    StartupPerformanceMetricsStore.snapshot(requireContext().getApplicationContext());

            if (snapshot.totalCount == 0) {
                if (startupSummaryPreference != null) {
                    startupSummaryPreference.setSummary(R.string.auto_java_ff_startup_no_data);
                }
                if (startupLastPreference != null) {
                    startupLastPreference.setSummary(R.string.auto_java_ff_no_startup);
                }
                if (startupThresholdPreference != null) {
                    startupThresholdPreference.setSummary(getString(R.string.auto_java_ff_threshold, StartupPerformanceMetricsStore.formatDuration(snapshot.activeThresholdMs)));
                }
                return;
            }

            if (startupSummaryPreference != null) {
                startupSummaryPreference.setSummary(getString(R.string.auto_java_ff_startup_summary, snapshot.totalCount, snapshot.regressionCount, StartupPerformanceMetricsStore.formatPercent(snapshot.regressionRate), StartupPerformanceMetricsStore.formatOperation("startup.total", snapshot.total)));
            }

            if (startupLastPreference != null) {
                startupLastPreference.setSummary(getString(R.string.auto_java_ff_startup_last, StartupPerformanceMetricsStore.formatDuration(snapshot.lastTotalDurationMs), StartupPerformanceMetricsStore.formatDuration(snapshot.lastApplicationInitDurationMs), StartupPerformanceMetricsStore.formatDuration(snapshot.lastMainActivityDurationMs)));
            }

            if (startupThresholdPreference != null) {
                String status = snapshot.lastRegression ? getString(R.string.auto_java_ff_regression) : getString(R.string.auto_java_ff_ok);
                startupThresholdPreference.setSummary(getString(R.string.auto_java_ff_startup_status, status, StartupPerformanceMetricsStore.formatDuration(snapshot.lastThresholdMs), StartupPerformanceMetricsStore.formatDuration(snapshot.activeThresholdMs), StartupPerformanceMetricsStore.formatDuration(snapshot.lastBaselineP50Ms)));
            }
        }

        private void refreshAccessibilityMetrics() {
            AccessibilityIssueReportStore.Snapshot snapshot =
                    AccessibilityIssueReportStore.snapshot(requireContext().getApplicationContext());

            if (snapshot.totalScans == 0) {
                if (accessibilitySummaryPreference != null) {
                    accessibilitySummaryPreference.setSummary(R.string.auto_java_ff_a11y_no_data);
                }
                if (accessibilityLastPreference != null) {
                    accessibilityLastPreference.setSummary(R.string.auto_java_ff_no_a11y);
                }
                return;
            }

            if (accessibilitySummaryPreference != null) {
                accessibilitySummaryPreference.setSummary(getString(R.string.auto_java_ff_a11y_summary, snapshot.totalScans, snapshot.totalIssues, AccessibilityIssueReportStore.formatPercent(snapshot.issueRate), snapshot.totalWarnings, snapshot.totalErrors));
            }

            if (accessibilityLastPreference != null) {
                accessibilityLastPreference.setSummary(getString(R.string.auto_java_ff_a11y_last, snapshot.lastScreenId, snapshot.lastIssueCount, snapshot.lastWarningCount, snapshot.lastErrorCount, AccessibilityIssueReportStore.formatDuration(snapshot.lastDurationMs)));
            }
        }

        private void refreshKpiDashboardMetrics() {
            if (!FeatureFlags.isEnabled(requireContext().getApplicationContext(), FeatureFlags.Key.KPI_DASHBOARD_RELEASE_GATES)) {
                if (kpiSummaryPreference != null) {
                    kpiSummaryPreference.setSummary(R.string.auto_java_ff_kpi_disabled);
                }
                if (kpiReleaseGatesPreference != null) {
                    kpiReleaseGatesPreference.setSummary(R.string.auto_java_ff_disabled);
                }
                return;
            }

            var snapshot = KpiDashboardStore.snapshot(requireContext().getApplicationContext());
            KpiDashboardReleaseGatesResult gates = KpiDashboardStore.evaluateReleaseGates(requireContext().getApplicationContext());

            if (kpiSummaryPreference != null) {
                kpiSummaryPreference.setSummary(getString(R.string.auto_java_ff_kpi_summary, snapshot.build.totalCount, snapshot.kmpBuild.totalRuns, snapshot.editor.totalSamples, snapshot.startup.totalCount, snapshot.accessibility.totalScans));
            }

            if (kpiReleaseGatesPreference != null) {
                kpiReleaseGatesPreference.setSummary(getString(R.string.auto_java_ff_kpi_gates_summary, gates.overallStatus().name(), gates.passCount, gates.warnCount, gates.failCount));
            }
        }

        private void showKmpDeveloperDocsDialog() {
            String message = getString(R.string.auto_java_kmp_dev_docs_msg);

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.auto_java_kmp_dev_docs)
                    .setMessage(message)
                    .setPositiveButton(R.string.auto_java_open_docs_site, (dialog, which) -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.ascode.pro"));
                        startActivity(intent);
                    })
                    .setNegativeButton(R.string.common_word_close, null)
                    .show();
        }
    }

    private static class FeatureFlagsPreferenceDataStore extends PreferenceDataStore {
        private final android.content.Context context;

        FeatureFlagsPreferenceDataStore(android.content.Context context) {
            this.context = context;
        }

        @Override
        public void putBoolean(String key, boolean value) {
            FeatureFlags.setEnabled(context, FeatureFlags.Key.valueOf(key), value);
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            try {
                return FeatureFlags.isEnabled(context, FeatureFlags.Key.valueOf(key));
            } catch (IllegalArgumentException ignored) {
                return defValue;
            }
        }
    }
}