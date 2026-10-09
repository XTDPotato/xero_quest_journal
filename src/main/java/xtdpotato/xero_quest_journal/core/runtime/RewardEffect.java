package xtdpotato.xero_quest_journal.core.runtime;

import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Effect settings stored in a reward's target; bare IDs remain valid legacy values. */
public record RewardEffect(String id, int level, boolean particles) {
    public static RewardEffect parse(String value) {
        try {
            if (value.startsWith("{")) {
                var data = JsonParser.parseString(value).getAsJsonObject();
                if (!data.has("id") || !data.has("level") || !data.has("particles")) return null;
                var effect = new RewardEffect(data.get("id").getAsString(),
                        data.get("level").getAsInt(), data.get("particles").getAsBoolean());
                return effect.valid() ? effect : null;
            }
            var effect = new RewardEffect(value, 1, true);
            return effect.valid() ? effect : null;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    public boolean valid() {
        var key = ResourceLocation.tryParse(id);
        return key != null && BuiltInRegistries.MOB_EFFECT.containsKey(key)
                && level >= 1 && level <= 256;
    }

    public String encoded() {
        var data = new com.google.gson.JsonObject();
        data.addProperty("id", id);
        data.addProperty("level", level);
        data.addProperty("particles", particles);
        return data.toString();
    }
}
