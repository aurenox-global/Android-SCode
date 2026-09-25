package com.ascode.android.plugins.security.analysis;

public interface PluginStaticAnalyzer {

    String id();

    PluginStaticAnalyzerResult analyze(PluginStaticAnalysisRequest request);
}
