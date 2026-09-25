package com.ascode.android.plugins.runtime;

public interface PluginClassLoaderFactory {

    PluginClassLoaderHandle create(PluginClassLoaderConfig config, ClassLoader parentClassLoader);
}
