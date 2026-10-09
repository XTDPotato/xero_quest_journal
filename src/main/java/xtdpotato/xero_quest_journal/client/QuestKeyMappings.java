package xtdpotato.xero_quest_journal.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class QuestKeyMappings {
    public static final KeyMapping OPEN = new KeyMapping(
            "key.xero_quest_journal.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.xero_quest_journal"
    );
    public static final KeyMapping OPEN_MAILBOX = new KeyMapping(
            "key.xero_quest_journal.open_mailbox",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "key.categories.xero_quest_journal"
    );
    public static final KeyMapping OPEN_NEW_MAIL = new KeyMapping(
            "key.xero_quest_journal.open_new_mail",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            "key.categories.xero_quest_journal"
    );
    public static final KeyMapping TRACK_TASK = new KeyMapping(
            "key.xero_quest_journal.track_task",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.xero_quest_journal"
    );

    private QuestKeyMappings() {
    }
}
