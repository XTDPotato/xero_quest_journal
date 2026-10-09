package xtdpotato.xero_quest_journal.client;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import xtdpotato.xero_quest_journal.core.model.HotEventCanvas;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;

public final class ClientHotEvents {
    private static final Gson GSON = new Gson();
    private static long nextRequestId = -1;

    private ClientHotEvents() {}

    public static boolean save(HotEventCanvas canvas) {
        Minecraft game = Minecraft.getInstance();
        if (canvas == null || game.player == null || game.getConnection() == null
                || !ClientEditMode.enabled() || !game.player.hasPermissions(2)) return false;
        try {
            String json = GSON.toJson(canvas);
            PacketDistributor.sendToServer(new QuestNetwork.HotEventCanvasPayload(nextRequestId--, json));
            return true;
        } catch (Exception failure) {
            return false;
        }
    }
}
