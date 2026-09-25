package com.ascode.android.debugger.jdwp;

import android.util.Log;

import com.ascode.android.debugger.profiler.JdwpProfilerEventBuffer;
import com.ascode.android.debugger.symbolication.CrashSymbolicationWorkflow;
import com.ascode.android.debugger.symbolication.NoOpCrashSymbolicationWorkflow;
import com.ascode.android.debugger.variables.JdwpVariableInspectorTransport;
import com.ascode.android.debugger.variables.NoOpJdwpVariableInspectorTransport;

public final class JdwpRuntimeFactory {

    private static final String TAG = "JdwpRuntimeFactory";

    private static final String[] BRIDGE_CANDIDATES = new String[] {
            "com.ascode.android.debugger.jdwp.runtime.RealJdwpBridge",
            "com.ascode.android.debugger.jdwp.RealJdwpBridge"
    };

    private static final String[] VARIABLE_INSPECTOR_CANDIDATES = new String[] {
            "com.ascode.android.debugger.variables.runtime.RealJdwpVariableInspectorTransport",
            "com.ascode.android.debugger.variables.RealJdwpVariableInspectorTransport"
    };

    private static final String[] SYMBOLICATION_CANDIDATES = new String[] {
            "com.ascode.android.debugger.symbolication.runtime.RealCrashSymbolicationWorkflow",
            "com.ascode.android.debugger.symbolication.RealCrashSymbolicationWorkflow"
    };

        private static final String[] PROFILER_CANDIDATES = new String[] {
            "com.ascode.android.debugger.profiler.runtime.RealJdwpProfilerEventBuffer",
            "com.ascode.android.debugger.profiler.RealJdwpProfilerEventBuffer"
        };

    private JdwpRuntimeFactory() {
    }

    public static JdwpBridge bridgeOrFallback(JdwpBridge bridge) {
        if (bridge != null) {
            return bridge;
        }

        JdwpBridge realBridge = instantiateFirst(JdwpBridge.class, BRIDGE_CANDIDATES);
        return realBridge == null ? new NoOpJdwpBridge() : realBridge;
    }

    public static JdwpVariableInspectorTransport variableInspectorOrFallback(
            JdwpVariableInspectorTransport transport) {
        if (transport != null) {
            return transport;
        }

        JdwpVariableInspectorTransport realTransport = instantiateFirst(
                JdwpVariableInspectorTransport.class,
                VARIABLE_INSPECTOR_CANDIDATES
        );
        return realTransport == null ? new NoOpJdwpVariableInspectorTransport() : realTransport;
    }

    public static CrashSymbolicationWorkflow symbolicationOrFallback(
            CrashSymbolicationWorkflow workflow) {
        if (workflow != null) {
            return workflow;
        }

        CrashSymbolicationWorkflow realWorkflow = instantiateFirst(
                CrashSymbolicationWorkflow.class,
                SYMBOLICATION_CANDIDATES
        );
        return realWorkflow == null ? new NoOpCrashSymbolicationWorkflow() : realWorkflow;
    }

    public static JdwpProfilerEventBuffer profilerBufferOrFallback(JdwpProfilerEventBuffer buffer) {
        if (buffer != null) {
            return buffer;
        }

        JdwpProfilerEventBuffer realBuffer = instantiateFirst(
                JdwpProfilerEventBuffer.class,
                PROFILER_CANDIDATES
        );
        return realBuffer == null ? new JdwpProfilerEventBuffer() : realBuffer;
    }

    private static <T> T instantiateFirst(Class<T> expectedType, String[] candidates) {
        for (String className : candidates) {
            T instance = instantiate(expectedType, className);
            if (instance != null) {
                return instance;
            }
        }
        return null;
    }

    private static <T> T instantiate(Class<T> expectedType, String className) {
        try {
            Class<?> rawClass = Class.forName(className);
            if (!expectedType.isAssignableFrom(rawClass)) {
                Log.w(TAG, "Ignoring incompatible candidate: " + className);
                return null;
            }
            Object instance = rawClass.getConstructor().newInstance();
            return expectedType.cast(instance);
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to initialize candidate: " + className, throwable);
            return null;
        }
    }
}