package com.ascode.android.lsp;

import java.util.List;

public interface LspCompletionProvider {
    String id();

    List<LspCompletionItem> getCompletions(LspSessionConfig config, LspCompletionRequest request) throws Exception;
}
