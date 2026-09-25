package com.ascode.android.debugger.jdwp;

public interface JdwpBridge {

    JdwpDebugSession createSession(JdwpSessionConfig config);
}
