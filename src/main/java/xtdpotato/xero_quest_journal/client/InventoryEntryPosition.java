package xtdpotato.xero_quest_journal.client;

import java.util.List;

/** Offsets are signed screen GUI pixels relative to the aligned button edge. */
public record InventoryEntryPosition(String anchor, int x, int y) {
    public static final int SIZE = 20;
    public static final List<String> ANCHORS = List.of("top_left", "top_center", "top_right",
            "center_left", "center", "center_right", "bottom_left", "bottom_center", "bottom_right");
    public InventoryEntryPosition {
        if (anchor == null || !ANCHORS.contains(anchor)) anchor = "top_left";
        x = Math.clamp(x, -16384, 16384);
        y = Math.clamp(y, -16384, 16384);
    }
    private int horizontal() { return ANCHORS.indexOf(anchor) % 3; }
    private int vertical() { return ANCHORS.indexOf(anchor) / 3; }
    private static int base(int extent, int alignment) { return Math.max(0, extent - SIZE) * alignment / 2; }
    public int[] resolve(int width, int height) {
        return new int[]{Math.clamp(base(width, horizontal()) + x, 0, Math.max(0, width - SIZE)),
                Math.clamp(base(height, vertical()) + y, 0, Math.max(0, height - SIZE))};
    }
    public InventoryEntryPosition reanchor(String next, int width, int height) {
        int[] point = resolve(width, height);
        int index = Math.max(0, ANCHORS.indexOf(next));
        return new InventoryEntryPosition(ANCHORS.get(index), point[0] - base(width, index % 3),
                point[1] - base(height, index / 3));
    }
    public InventoryEntryPosition move(int left, int top, int width, int height, boolean snap) {
        left = Math.clamp(left, 0, Math.max(0, width - SIZE));
        top = Math.clamp(top, 0, Math.max(0, height - SIZE));
        int horizontal = horizontal(), vertical = vertical();
        if (snap) {
            for (int align = 0; align < 3; align++) {
                if (Math.abs(left - base(width, align)) <= 8) { horizontal = align; left = base(width, align); }
                if (Math.abs(top - base(height, align)) <= 8) { vertical = align; top = base(height, align); }
            }
        }
        return new InventoryEntryPosition(ANCHORS.get(vertical * 3 + horizontal),
                left - base(width, horizontal), top - base(height, vertical));
    }
}
