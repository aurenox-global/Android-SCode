package com.ascode.android.debugger.jdwp;

public enum JdwpSessionState {
    CREATED,
    STARTING,
    ATTACHED,
    STOPPING,
    TERMINATED,
    FAILED
}
