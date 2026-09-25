package com.ascode.android.plugins.manifest;

public enum PluginManifestIssueSeverity {

    WARNING,
    ERROR;

    public boolean isError() {
        return this == ERROR;
    }
}
