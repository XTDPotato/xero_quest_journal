package xtdpotato.xero_quest_journal.core.io;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import xtdpotato.xero_quest_journal.core.model.HotEventData;
import xtdpotato.xero_quest_journal.core.model.ListIconConfig;

/** Position-based DOM edits leave unrelated author code byte-for-byte unchanged. */
public final class QuestDocuments {
    private QuestDocuments() {
    }

    public static String visual(String source, String visual) {
        Element quest = root(source);
        var range = quest.sourceRange();
        String opening = source.substring(range.start().pos(), range.end().pos()).stripTrailing();
        if (opening.endsWith("/>"))
            source = splice(source, range.start().pos(), range.end().pos(),
                    "<quest" + quest.attributes().html() + "></quest>");
        return replaceSection(source, "visual", visual);
    }

    public static String metadata(String source, String name, String icon, String description,
                                  boolean replaceDescription) {
        return metadata(source, name, icon, description, replaceDescription, null, null);
    }

    public static String metadata(String source, String name, String icon, String description,
                                  boolean replaceDescription, ListIconConfig leadingIcon,
                                  ListIconConfig trailingIcon) {
        Element quest = root(source);
        String oldName = quest.hasAttr("name") ? quest.attr("name") : quest.attr("id");
        if (!oldName.equals(name)) {
            quest.attr("name", name).removeAttr("title-key");
        }
        if (icon == null || icon.isBlank()) {
            quest.removeAttr("icon");
        } else {
            quest.attr("icon", icon);
        }
        iconAttributes(quest, leadingIcon, trailingIcon);
        if (replaceDescription) {
            quest.removeAttr("description-key");
        }
        var range = quest.sourceRange();
        String opening = "<quest" + quest.attributes().html() + ">";
        if (source.substring(range.start().pos(), range.end().pos()).stripTrailing().endsWith("/>")) {
            opening += "</quest>";
        }
        String updated = splice(source, range.start().pos(), range.end().pos(), opening);
        return replaceDescription
                ? replaceSection(updated, "content", new Element("content").appendElement("p").text(description).parent().outerHtml())
                : updated;
    }

    public static String objective(String source, String value) {
        Element quest = root(source);
        if (value == null || value.isBlank()) quest.removeAttr("objective");
        else quest.attr("objective", value.strip());
        var range = quest.sourceRange();
        String opening = "<quest" + quest.attributes().html() + ">";
        if (source.substring(range.start().pos(), range.end().pos()).stripTrailing().endsWith("/>"))
            opening += "</quest>";
        return splice(source, range.start().pos(), range.end().pos(), opening);
    }

    public static void iconAttributes(Element element, ListIconConfig leading, ListIconConfig trailing) {
        applyIconAttributes(element, "leading", leading);
        applyIconAttributes(element, "trailing", trailing);
    }

    public static String completionPolicy(String source,
                                          xtdpotato.xero_quest_journal.core.model.CompletionPolicy policy) {
        Element quest = root(source);
        quest.attr("completed-events", Boolean.toString(policy.events()))
                .attr("prerequisite-mode", policy.prerequisiteMode())
                .attr("completed-reenter-structure", Boolean.toString(policy.reenterStructure()))
                .attr("completed-export-items", Boolean.toString(policy.exportAcquiredItems()))
                .attr("structure-progress", Boolean.toString(policy.structureProgress()))
                .attr("automatic-reward-popup", Boolean.toString(policy.automaticRewardPopup()));
        var range = quest.sourceRange();
        boolean selfClosing = source.substring(range.start().pos(), range.end().pos()).stripTrailing().endsWith("/>");
        return splice(source, range.start().pos(), range.end().pos(),
                "<quest" + quest.attributes().html() + (selfClosing ? "/>" : ">"));
    }

    public static String defaultUnlocked(String source, boolean unlocked) {
        Element quest = root(source);
        quest.attr("unlocked", Boolean.toString(unlocked));
        var range = quest.sourceRange();
        boolean selfClosing = source.substring(range.start().pos(), range.end().pos()).stripTrailing().endsWith("/>");
        return splice(source, range.start().pos(), range.end().pos(),
                "<quest" + quest.attributes().html() + (selfClosing ? "/>" : ">"));
    }

    public static String taskFlags(String source, boolean hideWhenLocked, boolean hidden, boolean triggersEnabled) {
        return taskFlags(source, hideWhenLocked, hidden, triggersEnabled, null);
    }

    public static String taskFlags(String source, boolean hideWhenLocked, boolean hidden, boolean triggersEnabled,
                                   Boolean focusAvailable) {
        Element quest = root(source);
        if (focusAvailable != null) quest.attr("focus-available", focusAvailable.toString());
        quest.attr("hide-when-locked", Boolean.toString(hideWhenLocked))
                .attr("hidden", Boolean.toString(hidden))
                .attr("triggers-enabled", Boolean.toString(triggersEnabled));
        var range = quest.sourceRange();
        boolean selfClosing = source.substring(range.start().pos(), range.end().pos()).stripTrailing().endsWith("/>");
        return splice(source, range.start().pos(), range.end().pos(),
                "<quest" + quest.attributes().html() + (selfClosing ? "/>" : ">"));
    }

    public static String recommended(String source, boolean recommended) {
        Element quest = root(source);
        quest.attr("recommended", Boolean.toString(recommended));
        var range = quest.sourceRange();
        boolean selfClosing = source.substring(range.start().pos(), range.end().pos()).stripTrailing().endsWith("/>");
        return splice(source, range.start().pos(), range.end().pos(),
                "<quest" + quest.attributes().html() + (selfClosing ? "/>" : ">"));
    }

    public static String listAccent(String source, String value) {
        Element quest = root(source);
        quest.attr("list-accent", xtdpotato.xero_quest_journal.core.model.QuestListAccent.normalize(value));
        var range = quest.sourceRange();
        boolean selfClosing = source.substring(range.start().pos(), range.end().pos()).stripTrailing().endsWith("/>");
        return splice(source, range.start().pos(), range.end().pos(),
                "<quest" + quest.attributes().html() + (selfClosing ? "/>" : ">"));
    }

    public static String journeyVisible(String source, boolean visible) {
        var document = org.jsoup.Jsoup.parse(source, "", org.jsoup.parser.Parser.xmlParser());
        document.selectFirst("quest").attr("journey-visible", Boolean.toString(visible));
        return document.outerHtml();
    }

    public static String journeyAppearance(String source,
            xtdpotato.xero_quest_journal.core.model.JourneyAppearance value) {
        if (value == null) return source;
        Element quest = root(source);
        quest.attr("journey-appearance", new com.google.gson.Gson().toJson(value));
        var range = quest.sourceRange();
        boolean selfClosing = source.substring(range.start().pos(), range.end().pos()).stripTrailing().endsWith("/>");
        return splice(source, range.start().pos(), range.end().pos(),
                "<quest" + quest.attributes().html() + (selfClosing ? "/>" : ">"));
    }

    public static String limitedReward(String source, boolean limitedReward) {
        Element quest = root(source);
        quest.attr("limited-reward", Boolean.toString(limitedReward));
        var range = quest.sourceRange();
        boolean selfClosing = source.substring(range.start().pos(), range.end().pos()).stripTrailing().endsWith("/>");
        return splice(source, range.start().pos(), range.end().pos(),
                "<quest" + quest.attributes().html() + (selfClosing ? "/>" : ">"));
    }

    private static void applyIconAttributes(Element element, String prefix, ListIconConfig config) {
        if (config == null) return;
        element.attr(prefix + "-icon", config.image())
                .attr(prefix + "-tracked-icon", config.trackedImage())
                .attr(prefix + "-visible", Boolean.toString(config.visible()))
                .attr(prefix + "-replace-when-tracked", Boolean.toString(config.replaceWhenTracked()))
                .attr(prefix + "-alpha", Float.toString(config.alpha()))
                .attr(prefix + "-width", Integer.toString(config.width()))
                .attr(prefix + "-height", Integer.toString(config.height()))
                .attr(prefix + "-x", Integer.toString(config.offsetX()))
                .attr(prefix + "-y", Integer.toString(config.offsetY()))
                .attr(prefix + "-tracking-style", config.trackingStyle());
    }

    public static String rules(String source, String type, String item, int count, String parentsCsv,
                               String rewardItem, int rewardCount, String rewardMode) {
        Element quest = root(source);
        quest.attr("type", type).attr("reward-mode", rewardMode);
        var range = quest.sourceRange();
        source = splice(source, range.start().pos(), range.end().pos(), "<quest" + quest.attributes().html() + ">");

        Element parents = new Element("parents");
        for (String id : parentsCsv.split(",")) {
            if (!id.isBlank()) parents.appendElement("parent").attr("id", id.strip());
        }
        source = replaceSection(source, "parents", parents.childrenSize() == 0 ? "" : parents.outerHtml());

        Element rewards = new Element("rewards");
        if (!rewardItem.isBlank()) {
            rewards.appendElement("reward").attr("type", "item").attr("item", rewardItem)
                    .attr("count", Integer.toString(rewardCount));
        }
        return replaceSection(source, "rewards", rewards.childrenSize() == 0 ? "" : rewards.outerHtml());
    }

    public static String parents(String source, java.util.List<String> ids) {
        Element quest = root(source);
        var range = quest.sourceRange();
        if (source.substring(range.start().pos(), range.end().pos()).stripTrailing().endsWith("/>"))
            source = splice(source, range.start().pos(), range.end().pos(),
                    "<quest" + quest.attributes().html() + "></quest>");
        Element parents = new Element("parents");
        for (String id : ids) parents.appendElement("parent").attr("id", id);
        return replaceSection(source, "parents", ids.isEmpty() ? "" : parents.outerHtml());
    }

    public static String identity(String source, String id, String name, String uid) {
        Element quest = root(source);
        quest.attr("id", id).attr("name", name).attr("uid", uid)
                .removeAttr("title-key");
        var range = quest.sourceRange();
        boolean selfClosing = source.substring(range.start().pos(), range.end().pos()).stripTrailing().endsWith("/>");
        return splice(source, range.start().pos(), range.end().pos(),
                "<quest" + quest.attributes().html() + (selfClosing ? "/>" : ">"));
    }

    public static String rewards(String source,
                                 java.util.List<xtdpotato.xero_quest_journal.core.model.QuestDefinition.Reward> values,
                                 String mode) {
        Element quest = root(source);
        quest.attr("reward-mode", mode);
        var range = quest.sourceRange();
        boolean selfClosing = source.substring(range.start().pos(), range.end().pos()).stripTrailing().endsWith("/>");
        source = splice(source, range.start().pos(), range.end().pos(),
                "<quest" + quest.attributes().html() + ">" + (selfClosing ? "</quest>" : ""));
        Element rewards = new Element("rewards");
        for (var reward : values) {
            Element item = rewards.appendElement("reward").attr("type", reward.type()).attr("item", reward.item())
                    .attr("count", Integer.toString(reward.count()));
            if (!reward.quality().isBlank()) item.attr("quality", reward.quality());
            if (!reward.background().isBlank()) item.attr("background", reward.background());
        }
        return replaceSection(source, "rewards", rewards.outerHtml());
    }

    public static String hotEvent(String source, HotEventData value) {
        HotEventData data = value == null ? HotEventData.empty() : value;
        String markup = "";
        if (data.enabled() || !data.image().isBlank() || !data.title().isBlank()
                || data.positioned() || data.hideImage()) {
            var event = new Element("hot-event")
                    .attr("enabled", Boolean.toString(data.enabled()))
                    .attr("image", data.image())
                    .attr("title", data.title());
            if (data.hideImage()) event.attr("show-image", "false");
            if (data.imageWidth() > 0) event.attr("image-width", Integer.toString(data.imageWidth()));
            if (data.imageHeight() > 0) event.attr("image-height", Integer.toString(data.imageHeight()));
            if (data.positioned()) {
                event.attr("x", Integer.toString(data.x()))
                        .attr("y", Integer.toString(data.y()))
                        .attr("positioned", "true");
            }
            markup = event.outerHtml();
        }
        return replaceSection(source, "hot-event", markup);
    }

    public static String legendary(String source, xtdpotato.xero_quest_journal.core.model.LegendaryData value) {
        var data = value == null ? xtdpotato.xero_quest_journal.core.model.LegendaryData.empty() : value;
        if (data.image().isBlank() && data.subtitle().isBlank() && data.filters().isEmpty())
            return replaceSection(source, "legendary", "");
        var element = new Element("legendary").attr("image", data.image()).attr("subtitle", data.subtitle());
        data.filters().forEach((group, options) -> options.forEach(option ->
                element.appendElement("filter").attr("group", group).attr("name", option)));
        return replaceSection(source, "legendary", element.outerHtml());
    }

    public static String remapParents(String source, java.util.Map<String, String> ids) {
        var parents = root(source).select("parents > parent[id]");
        for (int i = parents.size() - 1; i >= 0; i--) {
            Element parent = parents.get(i);
            if (!ids.containsKey(parent.attr("id"))) continue;
            var range = parent.sourceRange();
            boolean selfClosing = source.substring(range.start().pos(), range.end().pos()).stripTrailing().endsWith("/>");
            parent.attr("id", ids.get(parent.attr("id")));
            source = splice(source, range.start().pos(), range.end().pos(),
                    "<parent" + parent.attributes().html() + (selfClosing ? "/>" : ">"));
        }
        return source;
    }

    public static String conditions(String source,
            java.util.List<xtdpotato.xero_quest_journal.core.model.QuestDefinition.Trigger> values) {
        if (values.isEmpty()) {
            Element quest = root(source);
            if (quest.attr("type").equals("guide")) {
                quest.attr("type", "main");
                var range = quest.sourceRange();
                boolean selfClosing = source.substring(range.start().pos(), range.end().pos()).stripTrailing().endsWith("/>");
                source = splice(source, range.start().pos(), range.end().pos(),
                        "<quest" + quest.attributes().html() + (selfClosing ? "/>" : ">"));
            }
        }
        source = replaceSection(source, "trigger", "");
        source = replaceSection(source, "conditions", "");
        StringBuilder markup = new StringBuilder();
        for (var value : values) markup.append(condition(value).outerHtml());
        return replaceSection(source, "trigger", markup.toString());
    }

    private static Element condition(xtdpotato.xero_quest_journal.core.model.QuestDefinition.Trigger value) {
        var element = new Element("trigger").attr("type", value.type()).attr("target", value.item())
                .attr("count", Integer.toString(value.count()));
        if (!value.buttonText().isBlank()) element.attr("button-text", value.buttonText());
        if (!value.buttonShape().equals("rectangle")) element.attr("button-shape", value.buttonShape());
        value.children().forEach(child -> element.appendChild(condition(child)));
        return element;
    }

    public static String removeParent(String source, String parentId) {
        Element quest = root(source);
        var parents = quest.select("parents > parent[id]").stream()
                .filter(parent -> parent.attr("id").equals(parentId)).toList();
        String updated = source;
        for (int index = parents.size() - 1; index >= 0; index--) {
            Element parent = parents.get(index);
            int start = parent.sourceRange().start().pos();
            int end = parent.endSourceRange().isTracked()
                    ? parent.endSourceRange().end().pos() : parent.sourceRange().end().pos();
            updated = splice(updated, start, end, "");
        }
        Element refreshed = root(updated);
        Element parentList = refreshed.selectFirst("parents");
        if (parentList != null && parentList.children().isEmpty()) {
            int start = parentList.sourceRange().start().pos();
            int end = parentList.endSourceRange().isTracked()
                    ? parentList.endSourceRange().end().pos() : parentList.sourceRange().end().pos();
            updated = splice(updated, start, end, "");
        }
        return updated;
    }

    public static String replaceSection(String source, String tag, String markup) {
        Element quest = root(source);
        var sections = quest.children().stream().filter(child -> child.normalName().equals(tag)).toList();
        String updated = source;
        for (int i = sections.size() - 1; i >= 0; i--) {
            Element section = sections.get(i);
            int start = section.sourceRange().start().pos();
            int end = section.endSourceRange().isTracked()
                    ? section.endSourceRange().end().pos() : section.sourceRange().end().pos();
            if (start < 0 || end < start) {
                throw new IllegalArgumentException("Untracked " + tag);
            }
            updated = splice(updated, start, end, "");
        }
        quest = root(updated);
        if (!quest.endSourceRange().isTracked()) {
            throw new IllegalArgumentException("Quest needs an explicit closing tag");
        }
        int insert = quest.endSourceRange().start().pos();
        return splice(updated, insert, insert, markup);
    }

    private static Element root(String source) {
        Element quest = Jsoup.parse(source, "", Parser.xmlParser().setTrackPosition(true)).selectFirst("quest");
        if (quest == null || !quest.sourceRange().isTracked()) {
            throw new IllegalArgumentException("Missing quest element");
        }
        return quest;
    }

    private static String splice(String source, int start, int end, String replacement) {
        return source.substring(0, start) + replacement + source.substring(end);
    }
}
