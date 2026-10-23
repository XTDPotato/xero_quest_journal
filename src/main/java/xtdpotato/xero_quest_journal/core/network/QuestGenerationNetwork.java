package xtdpotato.xero_quest_journal.core.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import xtdpotato.xero_quest_journal.core.io.QuestGenerator;
import xtdpotato.xero_quest_journal.core.runtime.QuestProgressData;
import java.util.*;

public final class QuestGenerationNetwork {
    private static final Set<ServerPlayer> offered = Collections.newSetFromMap(new WeakHashMap<>());
    public static boolean allowed(ServerPlayer player) {
        return player.hasPermissions(2) || player.server.isSingleplayer() && player.server.isSingleplayerOwner(player.getGameProfile());
    }
    public static void offerFirst(ServerPlayer player) {
        if (player.tickCount < 40 || offered.contains(player) || QuestProgressData.get(player.server).generationOffered(player.getUUID())) return;
        open(player, true); offered.add(player);
    }
    public static void open(ServerPlayer player, boolean automatic) {
        PacketDistributor.sendToPlayer(player, new Offer(automatic, allowed(player), QuestGenerator.namespaces(player.server).stream().sorted().limit(512).toList()));
    }
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Offer.TYPE, Offer.CODEC, (payload, context) -> QuestClientInbox.accept(payload));
        registrar.playToServer(Choose.TYPE, Choose.CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            QuestProgressData.get(player.server).markGenerationOffered(player.getUUID());
            String result = "generation_dismissed";
            if (!payload.mode().equals("dismiss")) {
                if (!allowed(player)) result = "permission";
                else {
                    Set<String> available = QuestGenerator.namespaces(player.server);
                    Set<String> selected = payload.namespaces().contains("all")
                            ? available : new LinkedHashSet<>(payload.namespaces());
                    if (selected.isEmpty() || !available.containsAll(selected)) {
                        result = "invalid_action";
                    } else if (payload.options().tierItemRewards().stream().anyMatch(reward ->
                            !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(
                                    ResourceLocation.tryParse(reward.item())))) {
                        result = "invalid_action";
                    } else {
                        boolean started = QuestGenerator.startMany(player.server, selected,
                                QuestGenerator.RewardMode.RECIPE, 1,
                                QuestGenerator.GenerationMode.valueOf(payload.mode().toUpperCase(Locale.ROOT)),
                                payload.recipes(), payload.options(),
                                (message, failure) -> player.server.execute(() -> player.sendSystemMessage(message)));
                        result = started ? "generation_started" : "generation_busy";
                    }
                }
            }
            context.reply(new QuestNetwork.ActionResultPayload(payload.requestId(), result));
        });
    }
    public record Offer(boolean automatic, boolean allowed, List<String> namespaces) implements CustomPacketPayload {
        public Offer { namespaces = List.copyOf(namespaces); if (namespaces.size() > 512) throw new IllegalArgumentException("Too many namespaces"); }
        public static final Type<Offer> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "generation_offer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Offer> CODEC = new StreamCodec<>() {
            public Offer decode(RegistryFriendlyByteBuf b) {
                boolean automatic = b.readBoolean(), allowed = b.readBoolean(); int count = b.readVarInt();
                if (count < 0 || count > 512) throw new IllegalArgumentException("Invalid namespace count");
                var ids = new ArrayList<String>(); for (int i=0; i<count; i++) ids.add(b.readUtf(128));
                return new Offer(automatic, allowed, ids);
            }
            public void encode(RegistryFriendlyByteBuf b, Offer p) { b.writeBoolean(p.automatic()); b.writeBoolean(p.allowed()); b.writeVarInt(p.namespaces().size()); p.namespaces().forEach(id -> b.writeUtf(id,128)); }
        };
        public Type<Offer> type() { return TYPE; }
    }
    public record Choose(long requestId, String mode, List<String> namespaces, boolean recipes,
                         QuestGenerator.GenerationOptions options) implements CustomPacketPayload {
        public Choose(long requestId, String mode, String namespace, boolean recipes,
                      QuestGenerator.GenerationOptions options) {
            this(requestId, mode, namespace.equals("all") ? List.of("all") : List.of(namespace),
                    recipes, options);
        }
        public Choose {
            if (!List.of("dismiss", "progress", "structure", "both", "catalog").contains(mode)
                    || namespaces == null || namespaces.size() > 512
                    || namespaces.stream().anyMatch(namespace -> namespace == null
                    || !namespace.matches("[a-z0-9_.-]{1,128}"))
                    || new HashSet<>(namespaces).size() != namespaces.size()
                    || !mode.equals("dismiss") && namespaces.isEmpty())
                throw new IllegalArgumentException("Invalid generation choice");
            namespaces = List.copyOf(namespaces);
            options = options == null ? QuestGenerator.GenerationOptions.defaults() : options;
        }
        public static final Type<Choose> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("xero_quest_journal", "generation_choose"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Choose> CODEC = new StreamCodec<>() {
            public Choose decode(RegistryFriendlyByteBuf b) {
                long id = b.readVarLong();
                String mode = b.readUtf(16);
                int count = b.readVarInt();
                if (count < 0 || count > 512) throw new IllegalArgumentException("Invalid namespace count");
                var namespaces = new ArrayList<String>();
                for (int index = 0; index < count; index++) namespaces.add(b.readUtf(128));
                boolean recipes = b.readBoolean();
                var options = new QuestGenerator.GenerationOptions(
                        b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readUtf(16),
                        b.readBoolean(), b.readBoolean(),
                        b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean());
                int rewardCount = b.readVarInt();
                if (rewardCount < 0 || rewardCount > 128)
                    throw new IllegalArgumentException("Invalid tier reward count");
                var tierRewards = new ArrayList<QuestGenerator.TierItemReward>(rewardCount);
                for (int index = 0; index < rewardCount; index++)
                    tierRewards.add(new QuestGenerator.TierItemReward(
                            b.readVarInt(), b.readUtf(256), b.readVarInt(), b.readVarInt()));
                options = new QuestGenerator.GenerationOptions(options.taskMultiplier(), options.rewardMultiplier(),
                        options.experienceMultiplier(), options.experienceUnit(), options.bonusExperience(),
                        options.itemRewards(), options.advancements(), options.itemTasks(), options.combatTasks(),
                        options.structureTasks(), tierRewards);
                return new Choose(id, mode, namespaces, recipes, options);
            }
            public void encode(RegistryFriendlyByteBuf b, Choose p) {
                b.writeVarLong(p.requestId()); b.writeUtf(p.mode(), 16);
                b.writeVarInt(p.namespaces().size());
                p.namespaces().forEach(namespace -> b.writeUtf(namespace, 128));
                b.writeBoolean(p.recipes());
                var options = p.options();
                b.writeVarInt(options.taskMultiplier()); b.writeVarInt(options.rewardMultiplier());
                b.writeVarInt(options.experienceMultiplier());
                b.writeUtf(options.experienceUnit(), 16);
                b.writeBoolean(options.bonusExperience()); b.writeBoolean(options.itemRewards());
                b.writeBoolean(options.advancements()); b.writeBoolean(options.itemTasks());
                b.writeBoolean(options.combatTasks()); b.writeBoolean(options.structureTasks());
                b.writeVarInt(options.tierItemRewards().size());
                options.tierItemRewards().forEach(reward -> {
                    b.writeVarInt(reward.tier());
                    b.writeUtf(reward.item(), 256);
                    b.writeVarInt(reward.count());
                    b.writeVarInt(reward.weight());
                });
            }
        };
        public Type<Choose> type() { return TYPE; }
    }
}
