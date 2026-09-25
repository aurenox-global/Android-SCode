package com.ascode.android.debugger.variables;

public interface JdwpVariableInspectorTransport {

    boolean isAvailable(String sessionId);

    JdwpVariableInspectResult inspect(JdwpVariableInspectRequest request);
}
