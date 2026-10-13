package xtdpotato.xero_quest_journal.client;

import java.util.function.Consumer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.QuestScriptReference.Kind;

/** Inline graph draft; saving updates the owning graph, never executes the script. */
final class QuestScriptEditorScreen extends ScriptEditorScreenBase {
    private final Consumer<String> saved;

    QuestScriptEditorScreen(Screen parent, String initial, Consumer<String> saved) {
        this(parent, Component.translatable("screen.xero_quest_journal.graph.node.js"), initial, saved, Kind.GRAPH_PREDICATE);
    }
    QuestScriptEditorScreen(Screen parent, Component title, String initial, Consumer<String> saved) {
        this(parent, title, initial, saved, Kind.GRAPH_PREDICATE);
    }
    QuestScriptEditorScreen(Screen parent, Component title, String initial, Consumer<String> saved, Kind kind) {
        super(parent, title, initial, kind, "script-save");
        this.saved = saved;
    }
    @Override protected void saveDraft(String source) {
        saved.accept(source);
        minecraft.setScreen(parent);
    }
}
