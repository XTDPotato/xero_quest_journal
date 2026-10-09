package xtdpotato.xero_quest_journal.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class LayoutEditorState {
    public static final int DEFAULT_TASK_WIDTH = 395;
    public static final int DEFAULT_TASK_HEIGHT = 275;
    private final Map<String, Position> positions = new LinkedHashMap<>();
    private final Map<String, String> parents = new LinkedHashMap<>();
    private Set<String> deletedElements = new LinkedHashSet<>();
    private Map<String, Element> elements = new LinkedHashMap<>();
    private boolean snapToGrid = true;
    private int gridSize = 8;
    private int coordinateVersion;
    private int canvasWidth;
    private int canvasHeight;
    private Map<String, CanvasSize> taskCanvases = new LinkedHashMap<>();

    public record CanvasSize(int width, int height) {
        public CanvasSize {
            if (width < 1 || height < 1 || width > 100000 || height > 100000) {
                throw new IllegalArgumentException("Invalid task canvas dimensions");
            }
        }
    }

    public CanvasSize taskCanvas(String questId) {
        CanvasSize size = taskCanvases == null ? null : taskCanvases.get(questId);
        return size == null ? new CanvasSize(DEFAULT_TASK_WIDTH, DEFAULT_TASK_HEIGHT) : size;
    }

    public void setTaskCanvas(String questId, int width, int height) {
        if (questId == null || questId.isBlank()) throw new IllegalArgumentException("Missing task");
        CanvasSize size = new CanvasSize(width, height);
        if (taskCanvases == null) taskCanvases = new LinkedHashMap<>();
        taskCanvases.put(questId, size);
    }

    public Position position(String id, int defaultX, int defaultY) {
        return positions.computeIfAbsent(id, ignored -> new Position(defaultX, defaultY));
    }

    public void setParent(String id, String parentId) {
        Set<String> visited = new HashSet<>();
        for (String current = parentId; current != null; current = parents.get(current)) {
            if (current.equals(id) || !visited.add(current)) {
                throw new IllegalArgumentException("Cyclic layout parent");
            }
        }
        if (parentId == null) {
            parents.remove(id);
            return;
        }
        parents.put(id, parentId);
    }

    public void renameElement(String oldId, String newId) {
        if (oldId.equals(newId)) {
            return;
        }
        if (newId == null || !newId.matches("[A-Za-z0-9_-][A-Za-z0-9_.:-]{0,63}")
                || elements().containsKey(newId) || positions.containsKey(newId)) {
            throw new IllegalArgumentException("Invalid or duplicate layout element ID");
        }
        Element old = elements().remove(oldId);
        if (old == null) {
            throw new IllegalArgumentException("Missing layout element");
        }
        elements().put(newId, new Element(newId, old.quest(), old.kind(), old.value(),
                old.width(), old.height(), old.attributes()));
        Position position = positions.remove(oldId);
        if (position != null) {
            positions.put(newId, position);
        }
        String parent = parents.remove(oldId);
        if (parent != null) {
            parents.put(newId, parent);
        }
        parents.replaceAll((child, value) -> value.equals(oldId) ? newId : value);
    }

    public String parentOf(String id) {
        return parents.get(id);
    }

    public void setPosition(String id, int x, int y) {
        Position position = position(id, x, y);
        position.x = x;
        position.y = y;
    }

    public boolean ensureCanvasCoordinates(int width, int height, int legacyLeft, int legacyTop) {
        int nextWidth = Math.max(1, width);
        int nextHeight = Math.max(1, height);
        if (coordinateVersion >= 1 && canvasWidth > 0 && canvasHeight > 0) {
            return false;
        }
        for (var entry : elements().entrySet()) {
            Position position = positions.get(entry.getKey());
            if (position != null) {
                position.x = Math.max(0, position.x - legacyLeft);
                position.y = Math.max(0, position.y - legacyTop);
            }
            Element element = entry.getValue();
            Map<String, String> attributes = new LinkedHashMap<>(element.attributes());
            if (attributes.containsKey("journal_auto_x")) {
                attributes.put("journal_auto_x", Integer.toString(Math.max(0,
                        parse(attributes.get("journal_auto_x"), legacyLeft) - legacyLeft)));
            }
            if (attributes.containsKey("journal_auto_y")) {
                attributes.put("journal_auto_y", Integer.toString(Math.max(0,
                        parse(attributes.get("journal_auto_y"), legacyTop) - legacyTop)));
            }
            if (!attributes.equals(element.attributes())) {
                entry.setValue(new Element(element.id(), element.quest(), element.kind(), element.value(),
                        element.width(), element.height(), attributes));
            }
        }
        coordinateVersion = 1;
        canvasWidth = nextWidth;
        canvasHeight = nextHeight;
        return true;
    }

    public int canvasWidth() {
        return Math.max(1, canvasWidth);
    }

    public int canvasHeight() {
        return Math.max(1, canvasHeight);
    }

    public void move(String id, int x, int y, int width, int height, int screenWidth, int screenHeight) {
        moveWithin(id, x, y, width, height, 0, 0, screenWidth, screenHeight);
    }

    public void moveWithin(String id, int x, int y, int width, int height,
                           int left, int top, int right, int bottom) {
        moveWithin(id, x, y, width, height, left, top, right, bottom, true);
    }

    public void moveWithin(String id, int x, int y, int width, int height,
                           int left, int top, int right, int bottom, boolean snapGrid) {
        Position position = position(id, x, y);
        int snappedX = snapGrid ? snap(x) : x;
        int snappedY = snapGrid ? snap(y) : y;
        int maxX = Math.max(left, right - width);
        int maxY = Math.max(top, bottom - height);
        int deltaX = clamp(snappedX, left, maxX) - position.x;
        int deltaY = clamp(snappedY, top, maxY) - position.y;
        moveTreeWithin(id, deltaX, deltaY, left, top, right, bottom);
    }

    public void moveTree(String id, int deltaX, int deltaY, int screenWidth, int screenHeight) {
        moveTreeWithin(id, deltaX, deltaY, 0, 0, screenWidth, screenHeight);
    }

    public void moveTreeWithin(String id, int deltaX, int deltaY,
                               int left, int top, int right, int bottom) {
        Set<String> tree = new LinkedHashSet<>();
        collect(id, tree);
        int minX = Integer.MIN_VALUE;
        int minY = Integer.MIN_VALUE;
        int maxX = Integer.MAX_VALUE;
        int maxY = Integer.MAX_VALUE;
        for (String member : tree) {
            Position p = position(member, 0, 0);
            minX = Math.max(minX, left - p.x);
            minY = Math.max(minY, top - p.y);
            maxX = Math.min(maxX, Math.max(left, right - p.width) - p.x);
            maxY = Math.min(maxY, Math.max(top, bottom - p.height) - p.y);
        }
        int dx = clamp(deltaX, minX, Math.max(minX, maxX));
        int dy = clamp(deltaY, minY, Math.max(minY, maxY));
        for (String member : tree) {
            Position p = positions.get(member);
            p.x += dx;
            p.y += dy;
        }
    }

    private void collect(String id, Set<String> visited) {
        if (!visited.add(id)) {
            return;
        }
        for (var entry : parents.entrySet()) {
            if (id.equals(entry.getValue())) {
                collect(entry.getKey(), visited);
            }
        }
    }

    public void reset(String id) {
        positions.remove(id);
    }

    public void removeTree(String id) {
        Set<String> tree = new LinkedHashSet<>();
        collect(id, tree);
        tree.forEach(member -> {
            positions.remove(member);
            parents.remove(member);
            elements().remove(member);
        });
        parents.entrySet().removeIf(entry -> tree.contains(entry.getValue()));
    }

    public boolean isDeleted(String id) {
        return deletedElements != null && deletedElements.contains(id);
    }

    public void markDeleted(String id) {
        if (deletedElements == null) {
            deletedElements = new LinkedHashSet<>();
        }
        deletedElements.add(id);
    }

    public void moveLayer(Set<String> ids, int direction) {
        if (ids == null || ids.isEmpty() || direction == 0) {
            return;
        }
        List<String> order = new ArrayList<>(elements().keySet());
        if (direction > 0) {
            for (int index = order.size() - 2; index >= 0; index--) {
                if (ids.contains(order.get(index)) && !ids.contains(order.get(index + 1))) {
                    java.util.Collections.swap(order, index, index + 1);
                }
            }
        } else {
            for (int index = 1; index < order.size(); index++) {
                if (ids.contains(order.get(index)) && !ids.contains(order.get(index - 1))) {
                    java.util.Collections.swap(order, index, index - 1);
                }
            }
        }
        reorder(order);
    }

    public void moveLayerToEdge(Set<String> ids, boolean front) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<String> selected = elements().keySet().stream().filter(ids::contains).toList();
        List<String> rest = elements().keySet().stream().filter(id -> !ids.contains(id)).toList();
        List<String> order = new ArrayList<>(elements().size());
        if (front) {
            order.addAll(rest);
            order.addAll(selected);
        } else {
            order.addAll(selected);
            order.addAll(rest);
        }
        reorder(order);
    }

    public int depth(String id) {
        int depth = 0;
        Set<String> visited = new HashSet<>();
        for (String current = parents.get(id); current != null && visited.add(current); current = parents.get(current)) {
            depth++;
        }
        return depth;
    }

    public boolean isDescendant(String candidate, String ancestor) {
        Set<String> visited = new HashSet<>();
        for (String current = parents.get(candidate); current != null && visited.add(current); current = parents.get(current)) {
            if (current.equals(ancestor)) {
                return true;
            }
        }
        return false;
    }

    private void reorder(List<String> order) {
        Map<String, Element> reordered = new LinkedHashMap<>();
        order.forEach(id -> {
            Element element = elements().get(id);
            if (element != null) {
                reordered.put(id, element);
            }
        });
        elements = reordered;
    }

    public Map<String, Element> elements() {
        if (elements == null) {
            elements = new LinkedHashMap<>();
        }
        return elements;
    }

    public record Element(String id, String quest, String kind, String value, int width, int height,
                          Map<String, String> attributes) {
        public Element {
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }

        public Element(String id, String quest, String kind, String value, int width, int height) {
            this(id, quest, kind, value, width, height, Map.of());
        }

        public String attribute(String name, String fallback) {
            String value = attributes.get(name);
            return value == null || value.isBlank() ? fallback : value;
        }
    }

    public int snap(int value) {
        if (!snapToGrid || gridSize < 2) {
            return value;
        }
        return Math.round((float) value / gridSize) * gridSize;
    }

    public void setSnapToGrid(boolean enabled) {
        snapToGrid = enabled;
    }

    public boolean snapToGrid() {
        return snapToGrid;
    }

    public int gridSize() {
        return gridSize;
    }

    public void setGridSize(int gridSize) {
        this.gridSize = Math.max(2, Math.min(64, gridSize));
    }

    public Map<String, Position> positions() {
        return positions;
    }

    public Map<String, String> parents() {
        return parents;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int parse(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static final class Position {
        private int x;
        private int y;
        private int width;
        private int height;

        public Position() {
        }

        public Position(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public void size(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }
    }
}
