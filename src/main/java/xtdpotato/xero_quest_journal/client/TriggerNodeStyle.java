package xtdpotato.xero_quest_journal.client;

import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import xtdpotato.xero_quest_journal.core.model.MailNodeSettings;
import xtdpotato.xero_quest_journal.core.model.TriggerGraph;

/**
 * Node-graph look modelled on the Genshin (Miliastra) server node editor: a charcoal canvas with a
 * fine square grid, near-black rounded nodes topped by bright category bands with dark titles,
 * white pentagon execution pins and clean white Bezier wires.
 */
final class TriggerNodeStyle {
    /**
     * Node classes. Execution nodes share the lime band and are told apart by their glyph; events
     * are pink, queries blue, conditions amber, flow control silver and the end node coral.
     */
    enum Kind {
        EVENT(0xFFEE6394, 0xFFDC4A7E, 0xFFFFA6C6),
        QUERY(0xFF74BDF2, 0xFF58A3E0, 0xFFB3DEFF),
        LOGIC(0xFFF3B652, 0xFFE29E36, 0xFFFFD99A),
        FLOW(0xFFDADDE3, 0xFFC3C8D0, 0xFFFFFFFF),
        VALUE(0xFFD3DC5A, 0xFFC1CB46, 0xFFEFF5A6),
        ACTION(0xFFD3DC5A, 0xFFC1CB46, 0xFFEFF5A6),
        CONTROL(0xFFD3DC5A, 0xFFC1CB46, 0xFFEFF5A6),
        MAIL(0xFFD3DC5A, 0xFFC1CB46, 0xFFEFF5A6),
        TRAVEL(0xFFD3DC5A, 0xFFC1CB46, 0xFFEFF5A6),
        SCRIPT(0xFFD3DC5A, 0xFFC1CB46, 0xFFEFF5A6),
        REWARD(0xFFD3DC5A, 0xFFC1CB46, 0xFFEFF5A6),
        END(0xFFF27F6E, 0xFFDE6553, 0xFFFFB7AC);

        final int top, bottom, glow;
        /** Title and glyph colour on the bright band. */
        final int text = 0xFF17171B;

        Kind(int top, int bottom, int glow) {
            this.top = top;
            this.bottom = bottom;
            this.glow = glow;
        }
    }

    static final int CANVAS = 0xFF2B2B30;
    static final int BODY = 0xF71E1E22;
    static final int BODY_BORDER = 0xFF36363D;
    static final int PANEL = 0xFF1E1E22;
    static final int PANEL_RAISED = 0xF21B1B1F;
    static final int PANEL_BORDER = 0xFF3A3A42;
    static final int VALUE_BOX = 0xFF141417;
    static final int VALUE_BORDER = 0xFF2E2E35;
    static final int GOLD = 0xFFFFD27A;
    static final int PORT_NEXT = 0xFFF0F0F2;
    static final int PORT_TRUE = 0xFF9EE39A;
    static final int PORT_FALSE = 0xFFF4B27A;
    static final int PORT_INPUT = 0xFFF0F0F2;
    private static final int CANVAS_TOP = 0xFF2D2D32, CANVAS_BOTTOM = 0xFF27272C;
    private static final int GRID_MINOR = 0xFF333339, GRID_MAJOR = 0xFF3B3B43;

    private TriggerNodeStyle() {}

    static Kind kind(TriggerGraph.Node node) {
        String type = node.type();
        if (MailNodeSettings.TYPES.contains(type)) return Kind.MAIL;
        return switch (type) {
            case "event" -> Kind.EVENT;
            case "context" -> Kind.QUERY;
            case "js", "variable" -> Kind.LOGIC;
            case "set_variable", "calculate_variable", "set_context" -> Kind.VALUE;
            case "set_property", "reset_property", "set_theme", "set_overview_background",
                    "set_hot_event_background" -> Kind.CONTROL;
            case "delay" -> Kind.FLOW;
            case "locate_structure", "locate_biome", "teleport", "enter_structure" -> Kind.TRAVEL;
            case "kubejs_call", "kubejs_script" -> Kind.SCRIPT;
            case "reward" -> Kind.REWARD;
            case "end" -> Kind.END;
            default -> Kind.ACTION;
        };
    }

    /** Palette group of a node-library category, matching the nodes it creates. */
    static Kind kindForCategory(String category) {
        if (category.endsWith("_event")) return Kind.EVENT;
        if (category.endsWith("_value")) return Kind.QUERY;
        if (category.startsWith("mail")) return Kind.MAIL;
        if (category.startsWith("kubejs")) return Kind.SCRIPT;
        return switch (category) {
            case "control_properties", "set_property", "reset_property", "set_theme",
                    "set_overview_background", "set_hot_event_background" ->
                    Kind.CONTROL;
            case "logic", "condition", "script_condition", "variable" -> Kind.LOGIC;
            case "set_variable", "calculate_variable", "set_context" -> Kind.VALUE;
            case "location", "locate_structure", "locate_biome", "teleport", "enter_structure" -> Kind.TRAVEL;
            case "delay" -> Kind.FLOW;
            case "reward" -> Kind.REWARD;
            case "end" -> Kind.END;
            default -> Kind.ACTION;
        };
    }

    /** Canvas: charcoal gradient with a fine square grid (brighter line every four cells). */
    static void canvas(GuiGraphics g, int left, int top, int right, int bottom,
                       double originX, double originY, double zoom, boolean grid) {
        g.fillGradient(left, top, right, bottom, CANVAS_TOP, CANVAS_BOTTOM);
        if (grid) {
            double step = 24 * zoom;
            boolean majorOnly = step < 8;
            if (majorOnly) step *= 4;
            long firstColumn = (long) Math.floor((left - originX) / step);
            for (long column = firstColumn; originX + column * step <= right; column++) {
                int x = (int) Math.round(originX + column * step);
                if (x < left) continue;
                boolean major = majorOnly || Math.floorMod(column, 4) == 0;
                g.fill(x, top, x + 1, bottom, major ? GRID_MAJOR : GRID_MINOR);
            }
            long firstRow = (long) Math.floor((top - originY) / step);
            for (long row = firstRow; originY + row * step <= bottom; row++) {
                int y = (int) Math.round(originY + row * step);
                if (y < top) continue;
                boolean major = majorOnly || Math.floorMod(row, 4) == 0;
                g.fill(left, y, right, y + 1, major ? GRID_MAJOR : GRID_MINOR);
            }
        }
        vignette(g, left, top, right, bottom);
    }

    private static void vignette(GuiGraphics g, int left, int top, int right, int bottom) {
        int band = Math.min(20, Math.min(right - left, bottom - top) / 6);
        if (band < 2) return;
        g.fillGradient(left, top, right, top + band, 0x26000000, 0x00000000);
        g.fillGradient(left, bottom - band, right, bottom, 0x00000000, 0x26000000);
        for (int step = 0; step < band; step += 2) {
            int color = Math.round(0x26 * (1 - step / (float) band)) << 24;
            g.fill(left + step, top, left + step + 2, bottom, color);
            g.fill(right - step - 2, top, right - step, bottom, color);
        }
    }

    static int radius(int w, int h) {
        return Math.max(1, Math.min(5, Math.min(w, h) / 6));
    }

    /**
     * Node frame in screen space. {@code header} is the scaled header height. Content (title,
     * text, slots) is drawn afterwards by {@link TriggerNodeLayout.Layout#render}.
     */
    static void frame(GuiGraphics g, TriggerGraph.Node node, int x, int y, int w, int h, int header,
                      boolean selected, boolean hovered, boolean error, boolean snapTarget) {
        Kind kind = kind(node);
        int radius = radius(w, h);
        g.fill(x + 2, y + 4, x + w + 3, y + h + 5, 0x38000000);
        g.fill(x + 1, y + 2, x + w + 2, y + h + 3, 0x30000000);
        int ring = error ? 0xFFFF6B6B : snapTarget ? 0xFF79D7AF : selected ? GOLD : 0;
        if (ring != 0) {
            ControlAppearance.draw(g, x - 3, y - 3, w + 6, h + 6, "rounded", radius + 3, 0,
                    (ring & 0x00FFFFFF) | 0x38000000, 1);
            ControlAppearance.draw(g, x - 2, y - 2, w + 4, h + 4, "rounded", radius + 2, 0,
                    (ring & 0x00FFFFFF) | 0xA0000000, 1);
        } else if (hovered) {
            ControlAppearance.draw(g, x - 2, y - 2, w + 4, h + 4, "rounded", radius + 2, 0, 0x30FFFFFF, 1);
        }
        int border = ring != 0 ? ring : hovered ? 0xFF5C5C66 : BODY_BORDER;
        ControlAppearance.draw(g, x, y, w, h, "rounded", radius, BODY, border, 1);
        int headerBottom = Math.min(y + h - 1, y + Math.max(2, header));
        // Bright category band with rounded top corners and a light sheen along its top edge.
        for (int row = 1; row < Math.min(radius + 1, headerBottom - y); row++) {
            int inset = ControlAppearance.inset("rounded", radius, w, h, row) + 1;
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, kind.top);
        }
        int bandTop = y + Math.min(radius + 1, headerBottom - y);
        if (headerBottom > bandTop) g.fillGradient(x + 1, bandTop, x + w - 1, headerBottom, kind.top, kind.bottom);
        g.fill(x + radius, y + 1, x + w - radius, y + 2, 0x50FFFFFF);
        g.fill(x + 1, headerBottom, x + w - 1, headerBottom + 1, 0x60000000);
        // A faint lift at the foot keeps large bodies from looking flat.
        int footBottom = y + h - radius;
        if (footBottom > headerBottom + 2)
            g.fillGradient(x + 1, headerBottom + 1, x + w - 1, footBottom, 0x08FFFFFF, 0x00FFFFFF);
    }

    /** Corner brackets with a small diamond crowning the top edge (search matches). */
    static void ornaments(GuiGraphics g, int x, int y, int w, int h, int color) {
        int arm = Math.max(3, Math.min(7, Math.min(w, h) / 6));
        int left = x - 5, top = y - 5, right = x + w + 5, bottom = y + h + 5;
        g.fill(left, top, left + arm, top + 1, color);
        g.fill(left, top, left + 1, top + arm, color);
        g.fill(right - arm, top, right, top + 1, color);
        g.fill(right - 1, top, right, top + arm, color);
        g.fill(left, bottom - 1, left + arm, bottom, color);
        g.fill(left, bottom - arm, left + 1, bottom, color);
        g.fill(right - arm, bottom - 1, right, bottom, color);
        g.fill(right - 1, bottom - arm, right, bottom, color);
        if (w >= 24) MailDecor.diamond(g, x + w / 2, top, 2, color);
    }

    /** Darkens a node that does not match the active search. */
    static void dim(GuiGraphics g, int x, int y, int w, int h) {
        ControlAppearance.draw(g, x, y, w, h, "rounded", radius(w, h), 0xA8161619, 0, 0);
    }

    /** Expanding ring that announces a node that was just placed. */
    static void spawnRing(GuiGraphics g, TriggerGraph.Node node, int x, int y, int w, int h, float progress) {
        if (progress <= 0 || progress >= 1) return;
        int spread = 2 + Math.round(progress * 10);
        int alpha = Math.round((1 - progress) * 200);
        ControlAppearance.draw(g, x - spread, y - spread, w + spread * 2, h + spread * 2, "rounded",
                radius(w, h) + spread, 0, (kind(node).glow & 0x00FFFFFF) | alpha << 24, 1);
    }

    /** Category glyph drawn in node-local (unscaled) coordinates at the band's left. */
    static void glyph(GuiGraphics g, TriggerGraph.Node node, int x, int y, int size) {
        Kind kind = kind(node);
        int color = kind.text;
        int c = x + size / 2, m = y + size / 2;
        switch (kind) {
            case EVENT -> {
                // Lightning bolt.
                for (int row = 0; row < size / 2; row++) g.fill(c - row / 2, y + row, c + 2 - row / 2, y + row + 1, color);
                g.fill(c - size / 4 - 1, m - 1, c + size / 4 + 1, m + 1, color);
                for (int row = 0; row < size / 2; row++) g.fill(c - 1 + row / 2, m + row, c + 1 + row / 2, m + row + 1, color);
            }
            case LOGIC -> {
                JournalDetailRenderer.diamondRing(g, c, m, size / 2, color);
                MailDecor.diamond(g, c, m, Math.max(1, size / 2 - 3), color);
            }
            case QUERY -> {
                // Magnifier: ring plus handle.
                MailDecor.circle(g, x, y, size - 2, color);
                MailDecor.circle(g, x + 2, y + 2, size - 6, kind.top);
                for (int step = 0; step < 3; step++)
                    g.fill(x + size - 4 + step, y + size - 4 + step, x + size - 2 + step, y + size - 2 + step, color);
            }
            case VALUE -> {
                MailDecor.circle(g, x + 1, y + 1, size - 2, color);
                MailDecor.circle(g, x + 3, y + 3, size - 6, kind.top);
                MailDecor.circle(g, c - 1, m - 1, 3, color);
            }
            case ACTION -> {
                for (int row = 0; row < size; row++) {
                    int half = size / 2 - Math.abs(row - size / 2);
                    g.fill(x + 2, y + row, x + 2 + Math.max(1, half), y + row + 1, color);
                }
            }
            case CONTROL -> {
                g.fill(x, y + 2, x + size, y + 3, color);
                g.fill(x, m, x + size, m + 1, color);
                g.fill(x, y + size - 3, x + size, y + size - 2, color);
                g.fill(x + size - 4, y + 1, x + size - 2, y + 4, color);
                g.fill(x + 2, m - 1, x + 4, m + 2, color);
                g.fill(x + size / 2, y + size - 4, x + size / 2 + 2, y + size - 1, color);
            }
            case FLOW -> {
                MailDecor.circle(g, x, y, size, color);
                MailDecor.circle(g, x + 1, y + 1, size - 2, kind.top);
                g.fill(c, y + 2, c + 1, m + 1, color);
                g.fill(c, m, c + size / 3, m + 1, color);
            }
            case MAIL -> JournalNavButton.drawIcon(g, "mail_envelope", x - 1, y - 1, size + 2, color);
            case TRAVEL -> JournalDetailRenderer.pin(g, x + 1, y, color);
            case SCRIPT -> JournalNavButton.drawIcon(g, "source", x - 1, y - 1, size + 2, color);
            case REWARD -> JournalNavButton.drawIcon(g, "gift", x - 1, y - 1, size + 2, color);
            case END -> {
                g.fill(x + 1, y + 1, x + size - 1, y + size - 1, color);
                g.fill(x + 3, y + 3, x + size - 3, y + size - 3, kind.top);
            }
        }
    }

    /**
     * Execution pin: a pentagon pointing along the flow. Connected pins are filled, free pins are
     * drawn as an outline, hovered pins grow and glow.
     */
    static void port(GuiGraphics g, double x, double y, int color, boolean connected, boolean hovered) {
        int cx = (int) Math.round(x), cy = (int) Math.round(y);
        int half = hovered ? 5 : 4;
        if (hovered) g.fill(cx - half - 3, cy - half - 2, cx + half + 3, cy + half + 2, (color & 0x00FFFFFF) | 0x40000000);
        pentagon(g, cx, cy, half + 1, 0xC0101013);
        pentagon(g, cx, cy, half, color);
        if (!connected) pentagon(g, cx, cy, half - 1, 0xFF1E1E22);
    }

    private static void pentagon(GuiGraphics g, int cx, int cy, int half, int color) {
        if (half <= 0) return;
        for (int row = -half; row < half; row++) {
            double distance = Math.abs(row + .5);
            int nose = (int) Math.round(half - distance);
            g.fill(cx - half - 1, cy + row, cx + 1 + nose, cy + row + 1, color);
        }
    }

    /** Pulsing frame marking an input that can receive the wire being dragged. */
    static void portHalo(GuiGraphics g, double x, double y, long nanos, boolean reducedMotion) {
        float pulse = reducedMotion ? .5F : (float) ((Math.sin(nanos / 160_000_000.0) + 1) * .5);
        int cx = (int) Math.round(x), cy = (int) Math.round(y);
        int size = 8 + Math.round(pulse * 2);
        int color = (GOLD & 0x00FFFFFF) | Math.round(70 + pulse * 80) << 24;
        ControlAppearance.draw(g, cx - size, cy - size, size * 2, size * 2, "rounded", 4, 0, color, 1);
    }

    /** Vertical port offset inside a node at zoom 1 (input and "next" share the centre row). */
    static int portOffset(String port) {
        return port.equals("true") ? 24 : port.equals("false") ? 40 : 32;
    }

    static int portColor(String port) {
        return switch (port) {
            case "true" -> PORT_TRUE;
            case "false" -> PORT_FALSE;
            default -> PORT_NEXT;
        };
    }

    /** Lightens a colour towards white for highlighted wires. */
    static int brighten(int color) {
        return mix(color, 0xFFFFFFFF, .7F);
    }

    /** Opaque blend: {@code amount} of {@code color} over {@code base}. */
    static int mix(int color, int base, float amount) {
        float rest = 1 - amount;
        int red = Math.round((color >>> 16 & 255) * amount + (base >>> 16 & 255) * rest);
        int green = Math.round((color >>> 8 & 255) * amount + (base >>> 8 & 255) * rest);
        int blue = Math.round((color & 255) * amount + (base & 255) * rest);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    /** Point on the wire's cubic curve; horizontal tangents leave and enter the ports. */
    static double[] point(double x1, double y1, double x2, double y2, double t) {
        double c = Math.max(24, Math.abs(x2 - x1) * 0.45);
        double ax = x1 + c, bx = x2 - c;
        double u = 1 - t;
        double x = u * u * u * x1 + 3 * u * u * t * ax + 3 * u * t * t * bx + t * t * t * x2;
        double y = u * u * u * y1 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y2;
        return new double[]{x, y};
    }

    static int segments(double x1, double y1, double x2, double y2) {
        return (int) Math.clamp(Math.hypot(x2 - x1, y2 - y1) / 8, 10, 48);
    }

    /** Draws a clean wire; {@code dashed} marks a wire still being dragged. */
    static void wire(GuiGraphics g, double x1, double y1, double x2, double y2, int color, int thickness,
                     boolean dashed, int left, int top, int right, int bottom) {
        wire(g, x1, y1, x2, y2, color, thickness, dashed, false, left, top, right, bottom);
    }

    static void wire(GuiGraphics g, double x1, double y1, double x2, double y2, int color, int thickness,
                     boolean dashed, boolean casing, int left, int top, int right, int bottom) {
        int count = segments(x1, y1, x2, y2);
        double[] previous = point(x1, y1, x2, y2, 0);
        for (int i = 1; i <= count; i++) {
            double[] next = point(x1, y1, x2, y2, i / (double) count);
            boolean visible = Math.max(previous[0], next[0]) >= left - 8 && Math.min(previous[0], next[0]) <= right + 8
                    && Math.max(previous[1], next[1]) >= top - 8 && Math.min(previous[1], next[1]) <= bottom + 8;
            if (visible && (!dashed || i % 2 == 1)) {
                if (casing) segment(g, previous[0], previous[1], next[0], next[1], thickness + 2, 0x90080A0E);
                segment(g, previous[0], previous[1], next[0], next[1], thickness, color);
            }
            previous = next;
        }
    }

    /** Soft opaque halo under a highlighted wire (opaque so overlapping segments do not bead). */
    static void halo(GuiGraphics g, double x1, double y1, double x2, double y2, int color, int thickness,
                     int left, int top, int right, int bottom) {
        wire(g, x1, y1, x2, y2, mix(color, CANVAS, .35F), thickness + 4, false, false,
                left, top, right, bottom);
    }

    /** Gold diamonds travelling along a wire to show the direction of execution. */
    static void flow(GuiGraphics g, double x1, double y1, double x2, double y2, int color, long nanos) {
        for (int pulse = 0; pulse < 2; pulse++) {
            double t = ((nanos / 1_400_000_000.0) + pulse * 0.5) % 1.0;
            double[] p = point(x1, y1, x2, y2, t);
            int px = (int) Math.round(p[0]), py = (int) Math.round(p[1]);
            MailDecor.diamond(g, px, py, 4, 0xC0101013);
            MailDecor.diamond(g, px, py, 3, GOLD);
            MailDecor.diamond(g, px, py, 1, 0xFFFFFFFF);
        }
    }

    /** Dashed alignment guide across the canvas (vertical when {@code vertical}). */
    static void guide(GuiGraphics g, int position, int from, int to, boolean vertical) {
        for (int at = from; at < to; at += 6) {
            int end = Math.min(to, at + 3);
            if (vertical) g.fill(position, at, position + 1, end, 0xB0FFD27A);
            else g.fill(at, position, end, position + 1, 0xB0FFD27A);
        }
    }

    /** Rounded toolbar plate behind a group of tool buttons on the window chrome. */
    static void toolbar(GuiGraphics g, int left, int top, int right, int bottom) {
        g.fill(left + 1, top + 2, right + 1, bottom + 2, 0x40000000);
        ControlAppearance.draw(g, left, top, right - left, bottom - top, "rounded", 8, 0xFF27272C, PANEL_BORDER, 1);
    }

    private static void segment(GuiGraphics g, double x1, double y1, double x2, double y2, int thickness, int color) {
        double length = Math.hypot(x2 - x1, y2 - y1);
        if (length < 0.01) return;
        float angle = (float) Math.toDegrees(Math.atan2(y2 - y1, x2 - x1));
        int half = thickness / 2;
        g.pose().pushPose();
        g.pose().translate(x1, y1, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        g.fill(-1, -half, (int) Math.ceil(length) + 1, thickness - half, color);
        g.pose().popPose();
    }

    /** Distance from a point to the drawn wire, for selection. */
    static double distance(double x1, double y1, double x2, double y2, double px, double py) {
        int count = segments(x1, y1, x2, y2);
        double best = Double.MAX_VALUE;
        double[] previous = point(x1, y1, x2, y2, 0);
        for (int i = 1; i <= count; i++) {
            double[] next = point(x1, y1, x2, y2, i / (double) count);
            double dx = next[0] - previous[0], dy = next[1] - previous[1];
            double lengthSquared = dx * dx + dy * dy;
            double t = lengthSquared == 0 ? 0
                    : Math.clamp(((px - previous[0]) * dx + (py - previous[1]) * dy) / lengthSquared, 0, 1);
            best = Math.min(best, Math.hypot(px - previous[0] - t * dx, py - previous[1] - t * dy));
            previous = next;
        }
        return best;
    }
}
