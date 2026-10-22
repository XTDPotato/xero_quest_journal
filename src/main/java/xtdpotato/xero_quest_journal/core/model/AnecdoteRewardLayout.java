package xtdpotato.xero_quest_journal.core.model;

/** Relative design-canvas geometry for the ticket, reward stack and claim control. */
public record AnecdoteRewardLayout(Control ticket, Control items, Control claim) {
    public record Control(double x, double y, double scale) {
        public Control {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(scale)
                    || x < -1000 || x > 2000 || y < -1000 || y > 2000 || scale < .4 || scale > 2.5)
                throw new IllegalArgumentException("Invalid anecdote reward control geometry");
        }
        public Control move(double nextX, double nextY) { return new Control(nextX, nextY, scale); }
        public Control resize(double nextScale) { return new Control(x, y, nextScale); }
    }

    public AnecdoteRewardLayout {
        ticket = ticket == null ? defaults().ticket : ticket;
        items = items == null ? defaults().items : items;
        claim = claim == null ? defaults().claim : claim;
    }

    public static AnecdoteRewardLayout defaults() {
        return new AnecdoteRewardLayout(
                new Control(630, 426, 1.0),
                new Control(642, 432, 1.25),
                new Control(837, 437, 1.0));
    }

    public Control control(String id) {
        return switch (id) {
            case "items" -> items;
            case "claim" -> claim;
            default -> ticket;
        };
    }

    public AnecdoteRewardLayout withControl(String id, Control value) {
        return new AnecdoteRewardLayout(id.equals("ticket") ? value : ticket,
                id.equals("items") ? value : items, id.equals("claim") ? value : claim);
    }
}
