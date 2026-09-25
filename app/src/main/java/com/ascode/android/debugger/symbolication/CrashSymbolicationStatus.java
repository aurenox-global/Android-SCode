package com.ascode.android.debugger.symbolication;

public enum CrashSymbolicationStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    PARTIAL,
    FAILED,
    CANCELED
}
