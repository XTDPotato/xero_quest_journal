package xtdpotato.xero_quest_journal.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Immutable bounding hierarchy. Queries retain paint order, including overlapping objects. */
public final class ViewportIndex<T> {
    public record Entry<T>(T value, double left, double top, double right, double bottom, int order) {
        public boolean intersects(double l, double t, double r, double b) {
            return right >= l && left <= r && bottom >= t && top <= b;
        }
    }

    private final Node<T> root;

    public ViewportIndex(List<Entry<T>> entries) {
        root = entries.isEmpty() ? null : build(new ArrayList<>(entries));
    }

    public List<T> query(double left, double top, double right, double bottom) {
        if (root == null || right < left || bottom < top) return List.of();
        var hits = new ArrayList<Entry<T>>();
        collect(root, left, top, right, bottom, hits);
        hits.sort(Comparator.comparingInt(Entry::order));
        return hits.stream().map(Entry::value).toList();
    }

    /** Clip before rasterization so distant endpoints cannot cause unbounded draw work. */
    public static double[] clipSegment(double x1, double y1, double x2, double y2,
                                       double left, double top, double right, double bottom) {
        if (right < left || bottom < top) return null;
        double dx = x2 - x1, dy = y2 - y1, start = 0, end = 1;
        double[] p = {-dx, dx, -dy, dy}, q = {x1 - left, right - x1, y1 - top, bottom - y1};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) { if (q[i] < 0) return null; continue; }
            double t = q[i] / p[i];
            if (p[i] < 0) start = Math.max(start, t);
            else end = Math.min(end, t);
            if (start > end) return null;
        }
        return new double[]{x1 + start * dx, y1 + start * dy, x1 + end * dx, y1 + end * dy};
    }

    private static final class Node<T> {
        double left, top, right, bottom;
        Node<T> first, second;
        List<Entry<T>> entries;
    }

    private static <T> Node<T> build(List<Entry<T>> entries) {
        var node = new Node<T>();
        node.left = node.top = Double.POSITIVE_INFINITY;
        node.right = node.bottom = Double.NEGATIVE_INFINITY;
        for (var entry : entries) {
            node.left = Math.min(node.left, entry.left());
            node.top = Math.min(node.top, entry.top());
            node.right = Math.max(node.right, entry.right());
            node.bottom = Math.max(node.bottom, entry.bottom());
        }
        if (entries.size() <= 8) node.entries = List.copyOf(entries);
        else {
            boolean horizontal = node.right - node.left >= node.bottom - node.top;
            entries.sort(Comparator.comparingDouble(entry -> horizontal
                    ? entry.left() + entry.right() : entry.top() + entry.bottom()));
            int middle = entries.size() / 2;
            node.first = build(new ArrayList<>(entries.subList(0, middle)));
            node.second = build(new ArrayList<>(entries.subList(middle, entries.size())));
        }
        return node;
    }

    private static <T> void collect(Node<T> node, double l, double t, double r, double b,
                                    List<Entry<T>> result) {
        if (node.right < l || node.left > r || node.bottom < t || node.top > b) return;
        if (node.entries != null) {
            for (var entry : node.entries) if (entry.intersects(l, t, r, b)) result.add(entry);
        } else {
            collect(node.first, l, t, r, b, result);
            collect(node.second, l, t, r, b, result);
        }
    }
}
