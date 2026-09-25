package com.ascode.android.plugins.security.analysis.analyzers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.ascode.android.plugins.manifest.PluginManifestIssue;
import com.ascode.android.plugins.manifest.PluginManifestIssueSeverity;
import com.ascode.android.plugins.manifest.PluginManifestVerificationResult;
import com.ascode.android.plugins.manifest.PluginManifestVerifier;
import com.ascode.android.plugins.security.PluginSignatureValidationStatus;
import com.ascode.android.plugins.security.analysis.PluginStaticAnalysisFinding;
import com.ascode.android.plugins.security.analysis.PluginStaticAnalysisRequest;
import com.ascode.android.plugins.security.analysis.PluginStaticAnalysisSeverity;
import com.ascode.android.plugins.security.analysis.PluginStaticAnalyzer;
import com.ascode.android.plugins.security.analysis.PluginStaticAnalyzerResult;

public final class PluginManifestStaticAnalyzer implements PluginStaticAnalyzer {

    private static final String ANALYZER_ID = "plugin.manifest";

    private final PluginManifestVerifier verifier;

    public PluginManifestStaticAnalyzer() {
        this(new PluginManifestVerifier());
    }

    public PluginManifestStaticAnalyzer(PluginManifestVerifier verifier) {
        this.verifier = verifier == null ? new PluginManifestVerifier() : verifier;
    }

    @Override
    public String id() {
        return ANALYZER_ID;
    }

    @Override
    public PluginStaticAnalyzerResult analyze(PluginStaticAnalysisRequest request) {
        long startedAt = System.currentTimeMillis();
        PluginStaticAnalysisRequest safeRequest = request == null
                ? new PluginStaticAnalysisRequest("", "", null, null, null)
                : request;

        PluginManifestVerificationResult verification = verifier.verify(
                safeRequest.manifestJson,
                safeRequest.pluginPayload
        );

        List<PluginStaticAnalysisFinding> findings = new ArrayList<>();
        for (PluginManifestIssue issue : verification.manifestValidation.issues) {
            findings.add(new PluginStaticAnalysisFinding(
                    ANALYZER_ID,
                    "manifest." + issue.code.name().toLowerCase(Locale.ROOT),
                    toSeverity(issue.severity),
                    issue.fieldPath,
                    issue.message
            ));
        }

        if (verification.signatureValidation.status == PluginSignatureValidationStatus.INVALID) {
            findings.add(new PluginStaticAnalysisFinding(
                    ANALYZER_ID,
                    "signature.invalid",
                    PluginStaticAnalysisSeverity.ERROR,
                    "signature",
                    verification.signatureValidation.message
            ));
        } else if (verification.signatureValidation.status == PluginSignatureValidationStatus.SKIPPED) {
            findings.add(new PluginStaticAnalysisFinding(
                    ANALYZER_ID,
                    "signature.skipped",
                    PluginStaticAnalysisSeverity.INFO,
                    "signature",
                    verification.signatureValidation.message
            ));
        }

        return PluginStaticAnalyzerResult.success(
                ANALYZER_ID,
                System.currentTimeMillis() - startedAt,
                findings
        );
    }

    private static PluginStaticAnalysisSeverity toSeverity(PluginManifestIssueSeverity severity) {
        if (severity == null) {
            return PluginStaticAnalysisSeverity.WARNING;
        }
        return severity.isError()
                ? PluginStaticAnalysisSeverity.ERROR
                : PluginStaticAnalysisSeverity.WARNING;
    }
}
