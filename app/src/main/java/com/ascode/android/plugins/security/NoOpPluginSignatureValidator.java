package com.ascode.android.plugins.security;

import com.ascode.android.plugins.manifest.PluginManifest;

public final class NoOpPluginSignatureValidator implements PluginSignatureValidator {

    @Override
    public PluginSignatureValidationResult validate(PluginManifest manifest, byte[] pluginPayload) {
        return PluginSignatureValidationResult.skipped(
                "No signature validator configured"
        );
    }
}
