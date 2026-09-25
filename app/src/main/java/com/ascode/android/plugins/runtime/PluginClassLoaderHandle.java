package com.ascode.android.plugins.runtime;

public interface PluginClassLoaderHandle extends AutoCloseable {

    ClassLoader classLoader();

    String description();

    @Override
    void close();
}
