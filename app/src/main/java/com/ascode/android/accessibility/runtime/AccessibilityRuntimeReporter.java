package com.ascode.android.accessibility.runtime;

import android.content.Context;
import android.view.View;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.ascode.android.accessibility.AccessibilityCheckRequest;
import com.ascode.android.accessibility.AccessibilityChecksEngine;
import com.ascode.android.accessibility.AccessibilityIssueReport;
import com.ascode.android.featureflags.FeatureFlags;
import com.ascode.android.metrics.AccessibilityIssueReportStore;

public final class AccessibilityRuntimeReporter {

    private static final AccessibilityChecksEngine ENGINE = AccessibilityChecksEngine.createDefault();
    private static final Set<String> REPORTED_SCREEN_IDS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private AccessibilityRuntimeReporter() {
    }

    public static void reportViewHierarchy(Context context, String screenId, View rootView) {
        if (context == null || rootView == null) {
            return;
        }

        Context appContext = context.getApplicationContext();
        if (!FeatureFlags.isEnabled(appContext, FeatureFlags.Key.ACCESSIBILITY_CHECKS_REPORTING)) {
            return;
        }

        String safeScreenId = screenId == null ? "" : screenId.trim();
        if (safeScreenId.isEmpty()) {
            safeScreenId = rootView.getClass().getSimpleName();
        }

        if (!REPORTED_SCREEN_IDS.add(safeScreenId)) {
            return;
        }

        AccessibilityIssueReport report = ENGINE.run(
                new AccessibilityCheckRequest(
                        safeScreenId,
                        AccessibilityViewSnapshotCollector.collect(rootView)
                )
        );
        AccessibilityIssueReportStore.recordReport(appContext, report);
    }
}
