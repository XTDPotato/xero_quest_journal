package xtdpotato.xero_quest_journal.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Group children slide behind the header while following rows share the same height change. */
public record JournalGroupSlide(JournalListModel model, Map<String, Clip> clips) {
    public record Clip(int top, int bottom) {}

    public static JournalGroupSlide apply(JournalListModel expanded, Map<String, Double> expansion) {
        var rows = new ArrayList<JournalListModel.Row>();
        var clips = new LinkedHashMap<String, Clip>();
        var source = expanded.rows();
        int removed = 0;
        for (int index = 0; index < source.size(); index++) {
            var row = source.get(index);
            rows.add(at(row, row.offset() - removed));
            if (row.group() == null) continue;
            int end = index + 1;
            while (end < source.size() && source.get(end).quest() != null && source.get(end).indent() > 0) end++;
            if (end == index + 1) continue;
            int top = row.offset() + row.height() + 4;
            var last = source.get(end - 1);
            int span = last.offset() + last.height() + 4 - top;
            double progress = Math.clamp(expansion.getOrDefault(row.group().id(), 1.), 0, 1);
            int hidden = (int) Math.round(span * (1 - progress));
            var clip = new Clip(top - removed, top - removed + span - hidden);
            removed += hidden;
            for (int child = index + 1; child < end; child++) {
                var task = source.get(child);
                rows.add(at(task, task.offset() - removed));
                clips.put(task.key(), clip);
            }
            index = end - 1;
        }
        return new JournalGroupSlide(new JournalListModel(java.util.List.copyOf(rows),
                Math.max(0, expanded.contentHeight() - removed)), Map.copyOf(clips));
    }

    private static JournalListModel.Row at(JournalListModel.Row row, int offset) {
        return new JournalListModel.Row(row.key(), row.quest(), row.group(), offset,
                row.height(), row.indent(), row.category());
    }
}
