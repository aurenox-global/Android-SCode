package com.ascode.android.accessibility.checkers;

import java.util.ArrayList;
import java.util.List;

import com.ascode.android.accessibility.AccessibilityCheckRequest;
import com.ascode.android.accessibility.AccessibilityChecker;
import com.ascode.android.accessibility.AccessibilityIssue;
import com.ascode.android.accessibility.AccessibilityIssueSeverity;
import com.ascode.android.accessibility.AccessibilityNodeSnapshot;

public final class MissingLabelAccessibilityChecker implements AccessibilityChecker {

    @Override
    public String id() {
        return "missing_label";
    }

    @Override
    public List<AccessibilityIssue> check(AccessibilityCheckRequest request) {
        ArrayList<AccessibilityIssue> issues = new ArrayList<>();
        if (request == null || request.nodes.isEmpty()) {
            return issues;
        }

        for (AccessibilityNodeSnapshot node : request.nodes) {
            if (node == null) {
                continue;
            }

            if (!node.importantForAccessibility || !node.enabled) {
                continue;
            }

            boolean missingLabel = node.label.isEmpty() && node.contentDescription.isEmpty();
            if (missingLabel) {
                issues.add(new AccessibilityIssue(
                        id(),
                        "node.missing_label",
                        AccessibilityIssueSeverity.WARNING,
                        node.id,
                        "Important accessibility node is missing visible label and content description"
                ));
            }
        }

        return issues;
    }
}
