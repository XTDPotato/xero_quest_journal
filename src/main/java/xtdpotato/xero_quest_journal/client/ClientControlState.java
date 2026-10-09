package xtdpotato.xero_quest_journal.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import xtdpotato.xero_quest_journal.core.network.QuestControlNetwork;
import java.util.Map;

public final class ClientControlState {
    private static final Gson GSON = new Gson();
    private static Map<String, Map<String, Map<String, String>>> values = Map.of();
    private static Object connection;
    private static long revision;
    private ClientControlState() {}
    public static void tick() {
        Object next = Minecraft.getInstance().getConnection();
        if (next != connection) {
            connection = next; values = Map.of(); revision++;
            if (next == null) QuestControlNetwork.clearInbox();
        }
        String source = QuestControlNetwork.take();
        if (source == null) return;
        Map<String, Map<String, Map<String, String>>> data = GSON.fromJson(source,
                new TypeToken<Map<String, Map<String, Map<String, String>>>>() {}.getType());
        if (data != null && !values.equals(data)) { values = data; revision++; }
    }
    public static long revision() { return revision; }
    public static Map<String, String> properties(String quest, String control) {
        return values.getOrDefault(quest, Map.of()).getOrDefault(control, Map.of());
    }
    public static void click(String quest, String control) {
        PacketDistributor.sendToServer(new QuestControlNetwork.Click(quest, control, ClientQuestProgress.catalogRevision()));
    }
    public static void pointer(String quest, String control, String event, double x, double y, double width, double height) {
        if (Minecraft.getInstance().getConnection() == null) return;
        PacketDistributor.sendToServer(new QuestControlNetwork.Pointer(quest, control, ClientQuestProgress.catalogRevision(),
                event, x, y, width, height));
    }
}
