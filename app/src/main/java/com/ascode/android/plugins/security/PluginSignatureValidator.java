package com.ascode.android.plugins.security;

import com.ascode.android.plugins.manifest.PluginManifest;

public interface PluginSignatureValidator {

    PluginSignatureValidationResult validate(PluginManifest manifest, byte[] pluginPayload);
}
