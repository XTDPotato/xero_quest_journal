package xtdpotato.xero_quest_journal.core.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import org.jsoup.nodes.Element;
import xtdpotato.xero_quest_journal.core.model.QuestPresentation;
import xtdpotato.xero_quest_journal.core.model.TriggerGraph;
import xtdpotato.xero_quest_journal.core.model.QuestVariable;

import java.util.*;

public final class QuestPresentationDocuments {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private QuestPresentationDocuments() {}
    public static QuestPresentation read(Element visual) {
        if (visual == null) return QuestPresentation.empty();
        List<QuestPresentation.Control> controls = new ArrayList<>();
        for (Element element : visual.children()) {
            if (!element.normalName().equals("control")) continue;
            Map<String, String> properties = new LinkedHashMap<>();
            for (Element property : element.select("properties > property"))
                properties.put(property.attr("name"), property.attr("value"));
            Element graph = element.selectFirst("event-graph"), value = element.selectFirst("value");
            controls.add(new QuestPresentation.Control(element.attr("id"), element.attr("kind"),
                    value == null ? "" : value.wholeText(), number(element, "x", 0), number(element, "y", 0),
                    number(element, "width", 80), number(element, "height", 20), element.attr("parent"), properties,
                    graph == null ? TriggerGraph.empty() : GSON.fromJson(graph.wholeText(), TriggerGraph.class)));
        }
        List<QuestVariable> variables = new ArrayList<>();
        for (Element variable : visual.select("variables > variable"))
            variables.add(new QuestVariable(variable.attr("id"), variable.attr("initial"),
                    variable.hasAttr("created") ? Long.parseLong(variable.attr("created")) : 0));
        return new QuestPresentation(!visual.attr("authored").equals("false"),
                number(visual, "width", 320), number(visual, "height", 240),
                controls, variables);
    }
    private static int number(Element element, String key, int fallback) {
        return element.hasAttr(key) ? Integer.parseInt(element.attr(key)) : fallback;
    }
    public static String write(String source, QuestPresentation presentation) {
        if (source.stripLeading().startsWith("{")) {
            var object = JsonParser.parseString(source).getAsJsonObject();
            object.add("presentation", GSON.toJsonTree(presentation));
            return GSON.toJson(object);
        }
        Element visual = new Element("visual").attr("width", Integer.toString(presentation.width()))
                .attr("height", Integer.toString(presentation.height()));
        if (!presentation.authored()) visual.attr("authored", "false");
        var document = new org.jsoup.nodes.Document("");
        document.outputSettings().prettyPrint(false).syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml);
        document.appendChild(visual);
        if (!presentation.variables().isEmpty()) {
            Element variables = visual.appendElement("variables");
            for (QuestVariable variable : presentation.variables())
                variables.appendElement("variable").attr("id", variable.id())
                        .attr("initial", variable.initial()).attr("created", Long.toString(variable.created()));
        }
        for (var control : presentation.controls()) {
            Element element = visual.appendElement("control").attr("id", control.id()).attr("kind", control.kind())
                    .attr("x", Integer.toString(control.x())).attr("y", Integer.toString(control.y()))
                    .attr("width", Integer.toString(control.width())).attr("height", Integer.toString(control.height()));
            if (!control.parent().isBlank()) element.attr("parent", control.parent());
            element.appendElement("value").text(control.value());
            Element properties = element.appendElement("properties");
            control.properties().forEach((name, value) -> properties.appendElement("property").attr("name", name).attr("value", value));
            if (!control.graph().nodes().isEmpty()) element.appendElement("event-graph").text(GSON.toJson(control.graph()));
        }
        return QuestDocuments.visual(source, visual.outerHtml());
    }
}
