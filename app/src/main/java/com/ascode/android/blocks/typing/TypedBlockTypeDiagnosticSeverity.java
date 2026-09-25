package com.ascode.android.blocks.typing;

public enum TypedBlockTypeDiagnosticSeverity {

    WARNING,
    ERROR;

    public boolean isError() {
        return this == ERROR;
    }
}
