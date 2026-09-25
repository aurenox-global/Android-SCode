package com.ascode.android.lsp;

public interface LspClient {
    LspDocumentSession createSession(LspSessionConfig config);
}
