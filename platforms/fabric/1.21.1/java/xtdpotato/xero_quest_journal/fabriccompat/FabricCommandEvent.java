package xtdpotato.xero_quest_journal.fabriccompat;

import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;

public record FabricCommandEvent(ParseResults<CommandSourceStack> getParseResults) {
    public boolean isCanceled() { return false; }
    public Throwable getException() { return null; }
}
