package xtdpotato.xero_quest_journal.client;

import java.util.Arrays;

/** Deterministic 100-point allocation shared by loot sliders and client regressions. */
public final class LootWeightDistribution {
    private LootWeightDistribution() {}

    public static int[] redistribute(int[] weights, boolean[] enabled, int selectedIndex, int selectedWeight) {
        if (weights.length != enabled.length || selectedIndex < 0 || selectedIndex >= weights.length) {
            throw new IllegalArgumentException("Mismatched loot weights");
        }
        int[] result = Arrays.copyOf(weights, weights.length);
        int fixed = Math.clamp(selectedWeight, 0, 100);
        result[selectedIndex] = fixed;
        int others = 0;
        for (int i = 0; i < enabled.length; i++) {
            if (i != selectedIndex && enabled[i]) others++;
        }
        if (others == 0) return result;
        int remaining = 100 - fixed;
        int quotient = remaining / others;
        int remainder = remaining % others;
        for (int i = 0, allocated = 0; i < enabled.length; i++) {
            if (i == selectedIndex || !enabled[i]) continue;
            result[i] = quotient + (allocated++ < remainder ? 1 : 0);
        }
        return result;
    }

    public static int[] equalize(int[] weights, boolean[] enabled) {
        if (weights.length != enabled.length) throw new IllegalArgumentException("Mismatched loot weights");
        int[] result = Arrays.copyOf(weights, weights.length);
        int count = 0;
        for (boolean value : enabled) if (value) count++;
        if (count == 0) return result;
        int quotient = 100 / count;
        int remainder = 100 % count;
        for (int i = 0, allocated = 0; i < enabled.length; i++) {
            if (enabled[i]) result[i] = quotient + (allocated++ < remainder ? 1 : 0);
        }
        return result;
    }
}
