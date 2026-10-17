package xtdpotato.xero_quest_journal.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import xtdpotato.xero_quest_journal.platform.PlatformServices;
import net.minecraft.server.level.ServerPlayer;
import xtdpotato.xero_quest_journal.core.io.AtomicFiles;
import xtdpotato.xero_quest_journal.core.io.QuestRepository;
import xtdpotato.xero_quest_journal.core.model.QuestTemplate;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.model.QuestPresentation;
import xtdpotato.xero_quest_journal.core.model.TriggerGraph;
import xtdpotato.xero_quest_journal.XeroQuestJournal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class QuestTemplateStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = PlatformServices.configDir().resolve("xero_quest").resolve("templates");
    private static volatile List<QuestTemplate> snapshot = List.of();

    private QuestTemplateStore() {
    }

    public static void ensureDefaults() throws IOException {
        Files.createDirectories(ROOT);
        Path marker = QuestConfig.root().resolve("runtime/template_catalog_v2");
        String catalogVersion = Files.exists(marker) ? Files.readString(marker).strip() : "";
        if (catalogVersion.equals("6")) return;
        if (catalogVersion.equals("5")) {
            writeTeachingTemplates();
            AtomicFiles.write(marker, "6\n", true);
            return;
        }
        if (catalogVersion.equals("4")) {
            writeTeachingTemplates();
            AtomicFiles.write(marker, "6\n", true);
            return;
        }
        if (catalogVersion.equals("3")) {
            writeTeachingTemplates();
            AtomicFiles.write(marker, "6\n", true);
            return;
        }
        if (catalogVersion.equals("2")) {
            migrateDefaultRewardLayouts();
            writeTeachingTemplates();
            AtomicFiles.write(marker, "6\n", true);
            return;
        }
        var legacy = List.of(
                new QuestTemplate("guide", "指引任务", "自定义完成条件的主线任务", "main",
                        "minecraft:book", "minecraft:oak_log", 1, "", 1, "default", "", legacyDefaultElements()),
                new QuestTemplate("item", "收集物品", "自定义物品完成条件", "commission",
                        "minecraft:chest", "minecraft:oak_log", 16, "", 1, "default", "", legacyDefaultElements()),
                new QuestTemplate("normal", "普通任务", "自定义任务内容", "commission",
                        "", "minecraft:oak_log", 1, "", 1, "default", "", legacyDefaultElements()),
                new QuestTemplate("commission", "委托任务", "委托任务详情", "commission",
                        "", "minecraft:oak_log", 16, "", 1, "manual", "", commissionElements()));
        // Keep referenced legacy geometry available, but remove untouched defaults from the chooser.
        for (var original : legacy) {
            Path file = ROOT.resolve(original.id() + ".json");
            if (Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    && original.equals(read(file))) AtomicFiles.write(file, GSON.toJson(original.archive()), true);
        }
        for (String type : List.of("main", "legendary", "commission", "world"))
            for (String mode : List.of("view", "travel", "submit", "structure", "event"))
                writeIfMissing(defaultTemplate(type, mode));
        QuestTemplateSceneDefaults.ensure();
        writeTeachingTemplates();
        Files.createDirectories(marker.getParent());
        AtomicFiles.write(marker, "6\n", false);
    }

    private static void writeTeachingTemplates() throws IOException {
        var progressGraph = new TriggerGraph(List.of(
                new TriggerGraph.Node("click", "event", 0, 0,
                        Map.of("event", "click", "category", "control_event", "cooldown", "4")),
                new TriggerGraph.Node("rename", "set_property", 180, 0,
                        Map.of("control", "progress", "property", "displayText", "value", "已提交"))),
                List.of(new TriggerGraph.Edge("click", "rename", "next")));
        var progressElements = new java.util.ArrayList<>(builtInDefaultElements());
        var progressButton = actionElement("rename_progress", "button", "更新进度名称", 150, 26,
                progressGraph, Map.of());
        progressElements.add(new QuestTemplate.Element(progressButton.id(), progressButton.kind(),
                progressButton.value(), 0, 0, progressButton.width(), progressButton.height(),
                progressButton.attributes(), "body"));
        QuestTemplate oldProgressExample = new QuestTemplate("tutorial_progress_label", "示例：自定义任务进度名称",
                "完成 4 个物品的提交任务。点击节点图按钮会把进度显示名改成“已提交”。也可在节点图中绑定只读进度变量 progress_current、progress_required 或 progress_ratio。",
                "world", "minecraft:paper", "minecraft:oak_log", 4, "", 1, "manual", "",
                progressElements, List.of(new QuestDefinition.Trigger("item_submit", "minecraft:oak_log", 4)),
                List.of(), "examples", false);
        var labeledProgressElements = new java.util.ArrayList<>(progressElements);
        for (int i = 0; i < labeledProgressElements.size(); i++) {
            var element = labeledProgressElements.get(i);
            if (!element.id().equals("progress")) continue;
            var attributes = new LinkedHashMap<>(element.attributes());
            attributes.put("displayText", "收集进度");
            labeledProgressElements.set(i, new QuestTemplate.Element(element.id(), element.kind(), element.value(),
                    element.x(), element.y(), element.width(), element.height(), attributes, element.parent()));
        }
        QuestTemplate progressExample = new QuestTemplate(oldProgressExample.id(), oldProgressExample.name(),
                oldProgressExample.description(), oldProgressExample.type(), oldProgressExample.icon(),
                oldProgressExample.item(), oldProgressExample.count(), oldProgressExample.rewardItem(),
                oldProgressExample.rewardCount(), oldProgressExample.rewardMode(), oldProgressExample.css(),
                labeledProgressElements, oldProgressExample.defaultConditions(),
                oldProgressExample.defaultRewards(), oldProgressExample.group(), oldProgressExample.archived());
        writeTeachingTemplate(progressExample, oldProgressExample);

        var locateGraph = new TriggerGraph(List.of(
                new TriggerGraph.Node("click", "event", 0, 0,
                        Map.of("event", "click", "category", "control_event", "cooldown", "20")),
                new TriggerGraph.Node("locate", "locate_structure", 180, 0,
                        Map.of("target", "minecraft:village_plains", "dimension", "", "radius", "32",
                                "storeVariable", "globe.located_structure")),
                new TriggerGraph.Node("done", "end", 360, -36, Map.of()),
                new TriggerGraph.Node("failed", "end", 360, 36, Map.of())),
                List.of(new TriggerGraph.Edge("click", "locate", "next"),
                        new TriggerGraph.Edge("locate", "done", "true"),
                        new TriggerGraph.Edge("locate", "failed", "false")));
        var locateElements = new java.util.ArrayList<>(builtInDefaultElements());
        var locateButton = actionElement("locate_share", "button", "定位并共享村庄坐标", 190, 28,
                locateGraph, Map.of());
        locateElements.add(new QuestTemplate.Element(locateButton.id(), locateButton.kind(),
                locateButton.value(), 0, 0, locateButton.width(), locateButton.height(),
                locateButton.attributes(), "body"));
        writeIfMissing(new QuestTemplate("tutorial_shared_locate", "示例：定位并共享结构坐标",
                "点击按钮搜索最近的平原村庄。成功后自动追踪本任务坐标，并写入默认队伍共享结构变量 globe.located_structure；同时保存结构 ID、维度及 X/Y/Z，可供同一队伍中的其他玩家和节点图读取。",
                "world", "minecraft:compass", "minecraft:oak_log", 1, "", 1, "manual", "",
                locateElements, List.of(new QuestDefinition.Trigger("confirm", "", 1)),
                List.of(), "examples", false));
    }

    private static void writeTeachingTemplate(QuestTemplate updated, QuestTemplate previousBuiltIn) throws IOException {
        Path file = ROOT.resolve(updated.id() + ".json");
        if (!Files.exists(file)) {
            writeIfMissing(updated);
            return;
        }
        if (previousBuiltIn.equals(read(file))) AtomicFiles.write(file, GSON.toJson(updated), true);
    }

    private static void migrateDefaultRewardLayouts() throws IOException {
        for (String type : List.of("main", "legendary", "commission", "world")) {
            for (String mode : List.of("view", "travel", "submit", "structure", "event")) {
                QuestTemplate updated = defaultTemplate(type, mode);
                Path file = ROOT.resolve(updated.id() + ".json");
                if (!Files.exists(file)) {
                    writeIfMissing(updated);
                } else if (defaultTemplate(type, mode, false).equals(read(file))) {
                    AtomicFiles.write(file, GSON.toJson(updated), true);
                }
            }
        }
        QuestTemplateSceneDefaults.ensure();
    }

    private static void writeIfMissing(QuestTemplate template) throws IOException {
        Path file = ROOT.resolve(template.id() + ".json");
        if (!Files.exists(file)) {
            if (template.elements().isEmpty()) {
                template = new QuestTemplate(template.id(), template.name(), template.description(), template.type(),
                        template.icon(), template.item(), template.count(), template.rewardItem(),
                        template.rewardCount(), template.rewardMode(), template.css(), builtInDefaultElements(),
                        template.defaultConditions(), template.defaultRewards());
            }
            AtomicFiles.write(file, GSON.toJson(template), false);
        }
    }

    private static List<QuestTemplate.Element> legacyDefaultElements() {
        Map<String, String> title = new LinkedHashMap<>();
        title.put("layout_width", "-2");
        title.put("layout_height", "-2");
        title.put("textColor", "#ffffff");
        title.put("textStyle", "bold");
        title.put("textSize", "1.0");
        Map<String, String> type = Map.of("layout_width", "-2", "layout_height", "-2", "textColor", "#97d4c5");
        Map<String, String> status = Map.of("layout_width", "-2", "layout_height", "-2", "textColor", "#f2cf83");
        Map<String, String> progress = Map.of("layout_width", "-2", "layout_height", "-2", "textColor", "#b8c7d9");
        Map<String, String> description = Map.of("layout_width", "-1", "layout_height", "-2",
                "textColor", "#e4eaf2", "maxLines", "0");
        return List.of(
                new QuestTemplate.Element("title", "quest_title", "", 0, 12, 220, 14, title, ""),
                new QuestTemplate.Element("type", "quest_type", "", 0, 28, 120, 12, type, ""),
                new QuestTemplate.Element("status", "quest_status", "", 0, 40, 160, 12, status, ""),
                new QuestTemplate.Element("progress", "quest_progress", "", 0, 58, 150, 12, progress, ""),
                new QuestTemplate.Element("description", "quest_description", "", 0, 72, 320, 72, description, "")
        );
    }

    private static List<QuestTemplate.Element> builtInDefaultElements() {
        var elements = new java.util.ArrayList<>(legacyBuiltInDefaultElements());
        elements.add(new QuestTemplate.Element("rewards", "quest_rewards", "", 0, 0, 350, 32,
                Map.of("layout_width", "-1", "layout_height", "-2"), "body"));
        return List.copyOf(elements);
    }

    private static List<QuestTemplate.Element> legacyBuiltInDefaultElements() {
        return List.of(
                new QuestTemplate.Element("body", "linear", "", 0, 12, 370, 230,
                        Map.of("layout_width", "-1", "layout_height", "-2", "orientation", "vertical",
                                "spacing", "12"), ""),
                new QuestTemplate.Element("title", "quest_title", "", 0, 0, 350, 16,
                        Map.of("layout_width", "-1", "layout_height", "-2", "textColor", "#ffffff"), "body"),
                new QuestTemplate.Element("divider", "relative", "", 0, 0, 350, 1,
                        Map.of("layout_width", "-1", "layout_height", "1", "background", "#508fa5aa"), "body"),
                new QuestTemplate.Element("objective", "quest_objective", "", 0, 0, 350, 24,
                        Map.of("layout_width", "-1", "layout_height", "-2", "background", "#60313e4d",
                                "padding", "6", "textColor", "#f0ebdd"), "body"),
                new QuestTemplate.Element("progress", "quest_progress", "", 0, 0, 350, 12,
                        Map.of("layout_width", "-1", "layout_height", "-2", "textColor", "#b8c7d9"), "body"),
                new QuestTemplate.Element("description", "quest_description", "", 0, 0, 350, 54,
                        Map.of("layout_width", "-1", "layout_height", "-2", "textColor", "#e4eaf2"), "body"));
    }

    private static QuestTemplate defaultTemplate(String type, String mode) {
        return defaultTemplate(type, mode, true);
    }

    private static QuestTemplate defaultTemplate(String type, String mode, boolean includeRewardFooter) {
        String prefix = switch (type) {
            case "main" -> "主线"; case "legendary" -> "传说"; case "commission" -> "委托"; default -> "世界";
        };
        String suffix = switch (mode) {
            case "view" -> "查看任务"; case "travel" -> "追踪任务"; case "submit" -> "提交任务";
            case "structure" -> "完成结构任务"; default -> "事件任务";
        };
        String description = switch (mode) {
            case "view" -> "阅读任务内容后，点击「我已查看」完成任务。";
            case "travel" -> "前往任意村庄，到达村庄范围后完成任务。";
            case "submit" -> "提交 16 个橡木原木。";
            case "structure" -> "进入任务结构，在中央指定位置放置金块完成挑战。";
            default -> "通过节点图完成任务。默认事件：玩家跳跃一次。";
        };
        String icon = switch (mode) {
            case "view" -> "minecraft:book"; case "travel" -> "minecraft:compass";
            case "submit" -> "minecraft:chest"; case "structure" -> "minecraft:structure_block";
            default -> "minecraft:redstone";
        };
        List<QuestTemplate.Element> elements = new java.util.ArrayList<>(
                includeRewardFooter ? builtInDefaultElements() : legacyBuiltInDefaultElements());
        var conditions = switch (mode) {
            case "view" -> List.of(new QuestDefinition.Trigger("confirm", "", 1));
            case "travel" -> List.of(new QuestDefinition.Trigger("location", "structure||#minecraft:village", 1));
            case "submit" -> List.of(new QuestDefinition.Trigger("item_submit", "minecraft:oak_log", 16));
            default -> List.of(new QuestDefinition.Trigger("event", mode.equals("event") ? "template_graph_only" : "template_scene_only", 1));
        };
        if (mode.equals("view") || mode.equals("submit")) {
            elements.add(actionElement("action", "button", mode.equals("view") ? "我已查看" : "提交物品", 124, 26,
                    graph("click", "activate"), Map.of()));
        } else if (mode.equals("structure")) {
            elements.add(actionElement("scene_preview", "structure_preview", "template_challenge.nbt", 144, 64,
                    TriggerGraph.empty(), Map.ofEntries(
                            Map.entry("background", "transparent"), Map.entry("backgroundHover", "transparent"),
                            Map.entry("backgroundPressed", "transparent"), Map.entry("borderColor", "transparent"),
                            Map.entry("borderColorHover", "transparent"), Map.entry("borderColorPressed", "transparent"),
                            Map.entry("borderEnabled", "false"), Map.entry("borderEnabledHover", "false"),
                            Map.entry("borderEnabledPressed", "false"), Map.entry("borderWidth", "0"),
                            Map.entry("borderWidthHover", "0"), Map.entry("borderWidthPressed", "0"),
                            Map.entry("shape", "rounded"), Map.entry("shapeHover", "rounded"),
                            Map.entry("shapePressed", "rounded"))));
            elements.add(actionElement("scene_enter", "structure_enter", "template_challenge.nbt", 124, 26,
                    graph("click", "enter_structure"), Map.of("displayText", "进入任务结构")));
        } else if (mode.equals("event")) {
            elements.add(new QuestTemplate.Element("events", "relative", "", 0, 0, 1, 1,
                    Map.of("visibility", "gone", "eventGraph", GSON.toJson(graph("jump", "complete"))), ""));
        }
        return new QuestTemplate(type + "_" + mode, prefix + suffix, description, type, icon,
                "minecraft:oak_log", mode.equals("submit") ? 16 : 1, "", 1, "manual", "",
                elements, conditions, List.of(), type, false);
    }

    private static QuestTemplate.Element actionElement(String id, String kind, String value, int w, int h,
                                                        TriggerGraph graph, Map<String, String> extra) {
        var attrs = new LinkedHashMap<String, String>();
        attrs.putAll(Map.ofEntries(
                Map.entry("layout_width", Integer.toString(w)), Map.entry("layout_height", Integer.toString(h)),
                Map.entry("background", "#ece6da"), Map.entry("backgroundHover", "#fff1da"),
                Map.entry("backgroundPressed", "#d7d0c3"), Map.entry("textColor", "#50586d"),
                Map.entry("borderColor", "transparent"), Map.entry("borderColorHover", "transparent"),
                Map.entry("borderColorPressed", "transparent"), Map.entry("borderWidth", "0"),
                Map.entry("borderWidthHover", "0"), Map.entry("borderWidthPressed", "0"),
                Map.entry("shape", "pill"), Map.entry("shapeHover", "pill"), Map.entry("shapePressed", "pill"),
                Map.entry("cornerRadius", "4"), Map.entry("gravity", "center")));
        attrs.putAll(extra);
        if (!graph.nodes().isEmpty()) attrs.put("eventGraph", GSON.toJson(graph));
        return new QuestTemplate.Element(id, kind, value, 0, 0, w, h, attrs, "body");
    }

    private static TriggerGraph graph(String event, String action) {
        return new TriggerGraph(List.of(
                new TriggerGraph.Node("trigger", "event", 0, 0, Map.of("event", event, "cooldown", "4")),
                new TriggerGraph.Node("action", action, 180, 0, Map.of())),
                List.of(new TriggerGraph.Edge("trigger", "action", "next")));
    }

    public static QuestPresentation presentation(QuestDefinition quest) {
        if (quest.presentation().authored() || quest.templateId().isBlank()) return quest.presentation();
        return snapshot.stream().filter(template -> template.id().equals(quest.templateId())).findFirst()
                .map(template -> template.presentation(quest.id())).orElse(quest.presentation());
    }

    public static Path root() {
        return ROOT;
    }

    public static List<QuestTemplate> all() {
        return snapshot;
    }

    /** Called on the server once per second; invalid edits retain the last valid catalog. */
    public static void refresh() {
        synchronized (QuestRepository.class) {
            List<QuestTemplate> next = readAll();
            if (next != null && !next.equals(snapshot)) {
                snapshot = next;
                QuestRepository.templatesChanged();
            }
        }
    }

    private static List<QuestTemplate> readAll() {
        try (var files = Files.list(ROOT)) {
            var paths = files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)).toList();
            if (paths.size() > 256) throw new IOException("Too many templates");
            var templates = new java.util.ArrayList<QuestTemplate>();
            for (Path path : paths) {
                QuestTemplate template = read(path);
                if (template == null || !path.getFileName().toString().equals(template.id() + ".json")) return null;
                templates.add(template);
            }
            templates.sort(Comparator.comparing(QuestTemplate::id));
            return List.copyOf(templates);
        } catch (IOException exception) {
            XeroQuestJournal.LOGGER.warn("Unable to read quest templates", exception);
            return null;
        }
    }

    public static QuestTemplate read(Path path) {
        try {
            if (Files.size(path) > 262144) throw new IOException("Template exceeds 256 KiB");
            var json = com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            QuestTemplate template = GSON.fromJson(json, QuestTemplate.class);
            if (template != null && !json.has("elements")) {
                template = new QuestTemplate(template.id(), template.name(), template.description(), template.type(),
                        template.icon(), template.item(), template.count(), template.rewardItem(),
                        template.rewardCount(), template.rewardMode(), template.css(),
                        List.of("guide", "item", "normal").contains(template.id()) ? legacyDefaultElements()
                                : template.id().equals("commission") ? commissionElements() : builtInDefaultElements(),
                        template.defaultConditions(), template.defaultRewards(), template.group(), template.archived());
            }
            return template;
        } catch (Exception exception) {
            XeroQuestJournal.LOGGER.warn("Unable to read quest template {}", path, exception);
            return null;
        }
    }

    public static String save(ServerPlayer author, QuestTemplate template, String expectedHash) {
        if (author == null || author.getServer() == null || !author.getServer().isSameThread()
                || !author.hasPermissions(2)) return "permission";
        if (!validItem(template.item()) || !template.rewardItem().isBlank() && !validItem(template.rewardItem())
                || template.defaultConditions().size() > 128
                || !xtdpotato.xero_quest_journal.core.runtime.QuestConditions.valid(template.defaultConditions())
                || template.defaultRewards().size() > 128
                || template.defaultRewards().stream().anyMatch(reward ->
                !xtdpotato.xero_quest_journal.core.runtime.QuestRewards.valid(reward))) {
            return "invalid";
        }
        synchronized (QuestRepository.class) {
            Path file = ROOT.resolve(template.id() + ".json");
            try {
                if (Files.isSymbolicLink(ROOT) || Files.isSymbolicLink(file)) return "save";
                boolean exists = Files.exists(file);
                if (expectedHash.isEmpty() && exists) return "duplicate";
                if (!expectedHash.isEmpty() && (!exists
                        || !expectedHash.equals(QuestRepository.fingerprint(Files.readString(file))))) return "conflict";
                String json = GSON.toJson(template);
                if (json.length() > 262144) return "invalid";
                var before = xtdpotato.xero_quest_journal.core.io.QuestAuthoringHistory.capture(List.of(file));
                AtomicFiles.write(file, json, exists);
                refresh();
                xtdpotato.xero_quest_journal.core.io.QuestAuthoringHistory.remember(author, "", "template", before);
                return "";
            } catch (IOException exception) {
                XeroQuestJournal.LOGGER.warn("Unable to save quest template {}", template.id(), exception);
                return "save";
            }
        }
    }

    public static boolean isDeletableId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,63}");
    }

    public static String delete(ServerPlayer author, List<String> ids) {
        if (author == null || author.getServer() == null || !author.getServer().isSameThread()
                || !author.hasPermissions(2)) return "permission";
        if (ids == null || ids.isEmpty() || ids.size() > 256
                || ids.stream().anyMatch(id -> !isDeletableId(id))) return "invalid";
        Set<String> uniqueIds = new LinkedHashSet<>(ids);
        synchronized (QuestRepository.class) {
            try {
                if (Files.isSymbolicLink(ROOT)) return "delete";
                for (String id : uniqueIds) {
                    Path file = ROOT.resolve(id + ".json");
                    if (Files.isSymbolicLink(file)) return "delete";
                    if (!Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return "not_found";
                }
                for (String id : uniqueIds) Files.delete(ROOT.resolve(id + ".json"));
                refresh();
                return "";
            } catch (IOException exception) {
                XeroQuestJournal.LOGGER.warn("Unable to delete quest templates {}", uniqueIds, exception);
                return "delete";
            }
        }
    }

    private static boolean validItem(String value) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(value);
        return id != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)
                && net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id) != net.minecraft.world.item.Items.AIR;
    }

    public static String fingerprint(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,63}")) return "";
        try {
            return QuestRepository.fingerprint(Files.readString(ROOT.resolve(id + ".json")));
        } catch (IOException missing) {
            return "";
        }
    }

    public static List<QuestTemplate.Element> defaultElements() {
        return builtInDefaultElements();
    }

    public static String encodeElements(List<QuestTemplate.Element> elements) {
        return GSON.toJson(elements == null ? List.of() : elements);
    }

    public static List<QuestTemplate.Element> decodeElements(String source) {
        try {
            QuestTemplate.Element[] values = GSON.fromJson(source, QuestTemplate.Element[].class);
            return values == null ? List.of() : List.of(values);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static List<QuestTemplate.Element> commissionElements() {
        return List.of(
                new QuestTemplate.Element("body", "linear", "", 0, 12, 300, 210,
                        Map.of("layout_width", "-1", "layout_height", "-2", "orientation", "vertical",
                                "spacing", "12"), ""),
                new QuestTemplate.Element("title", "quest_title", "", 0, 0, 280, 20,
                        Map.of("layout_width", "-1", "layout_height", "-2", "textStyle", "bold",
                                "textColor", "#ffffff", "textSize", "1.5"), "body"),
                new QuestTemplate.Element("location", "text", "", 0, 0, 280, 12,
                        Map.of("layout_width", "-1", "layout_height", "-2", "textColor", "#edd394"), "body"),
                new QuestTemplate.Element("divider", "relative", "", 0, 0, 280, 1,
                        Map.of("layout_width", "-1", "layout_height", "1", "background", "#608c949f"), "body"),
                new QuestTemplate.Element("objective", "quest_objective", "", 0, 0, 280, 28,
                        Map.of("layout_width", "-1", "layout_height", "-2", "padding", "7",
                                "background", "#60434c63", "textColor", "#ffffff"), "body"),
                new QuestTemplate.Element("description", "quest_description", "", 0, 0, 280, 54,
                        Map.of("layout_width", "-1", "layout_height", "-2", "textColor", "#ffffff"), "body"),
                new QuestTemplate.Element("status", "quest_status", "", 0, 0, 280, 12,
                        Map.of("layout_width", "-1", "layout_height", "-2", "textColor", "#edd394"), "body"),
                new QuestTemplate.Element("rewards", "quest_rewards", "", 0, 0, 280, 32,
                        Map.of("layout_width", "-1", "layout_height", "32"), "body")
        );
    }
}
