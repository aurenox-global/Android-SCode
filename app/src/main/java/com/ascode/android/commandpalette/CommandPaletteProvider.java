package com.ascode.android.commandpalette;

import java.util.List;

public interface CommandPaletteProvider {
    String id();

    List<CommandPaletteAction> getActions(CommandPaletteQuery query) throws Exception;
}
