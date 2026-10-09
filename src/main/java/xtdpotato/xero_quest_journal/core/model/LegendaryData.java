package xtdpotato.xero_quest_journal.core.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Optional cover and named facets for a legendary quest. */
public record LegendaryData(String image, String subtitle, Map<String, List<String>> filters) {
    public LegendaryData {
        image = image == null ? "" : image.strip();
        subtitle = subtitle == null ? "" : subtitle.strip();
        if (image.length() > 4096 || subtitle.length() > 128 || filters != null && filters.size() > 12)
            throw new IllegalArgumentException("Invalid legendary presentation");
        Map<String, List<String>> copy = new LinkedHashMap<>();
        if (filters != null) filters.forEach((group, values) -> {
            if (group == null || group.isBlank() || group.length() > 64 || values == null
                    || values.size() > 32 || values.stream().anyMatch(value ->
                    value == null || value.isBlank() || value.length() > 64))
                throw new IllegalArgumentException("Invalid legendary filter");
            copy.put(group.strip(), values.stream().map(String::strip).distinct().toList());
        });
        filters = java.util.Collections.unmodifiableMap(copy);
    }

    public static LegendaryData empty() { return new LegendaryData("", "", Map.of()); }
}
