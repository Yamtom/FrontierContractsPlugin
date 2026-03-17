package ua.grigo.frontiercontracts.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class ProgressionSettings {
    private final EnumMap<ContractRank, Integer> variantTargets;
    private final EnumMap<ContractRank, Integer> contractXp;
    private final NavigableMap<Integer, EnumMap<ContractRank, Integer>> rankRollTable;
    private final AntiFrustrationSettings antiFrustration;
    private final int xpPerLevel;
    private final int maxLevel;

    public ProgressionSettings(
        Map<ContractRank, Integer> variantTargets,
        Map<ContractRank, Integer> contractXp,
        NavigableMap<Integer, Map<ContractRank, Integer>> rankRollTable,
        AntiFrustrationSettings antiFrustration,
        int xpPerLevel,
        int maxLevel
    ) {
        this.variantTargets = withDefaults(variantTargets, Map.of(
            ContractRank.F, 800,
            ContractRank.E, 400,
            ContractRank.D, 200,
            ContractRank.C, 100,
            ContractRank.B, 50,
            ContractRank.A, 25,
            ContractRank.S, 5
        ));
        this.contractXp = withDefaults(contractXp, Map.of(
            ContractRank.F, 1,
            ContractRank.E, 2,
            ContractRank.D, 4,
            ContractRank.C, 7,
            ContractRank.B, 11,
            ContractRank.A, 16,
            ContractRank.S, 25
        ));
        TreeMap<Integer, EnumMap<ContractRank, Integer>> rolls = new TreeMap<>();
        if (rankRollTable != null) {
            for (Map.Entry<Integer, Map<ContractRank, Integer>> entry : rankRollTable.entrySet()) {
                rolls.put(Math.max(1, entry.getKey()), withDefaults(entry.getValue(), Map.of()));
            }
        }
        if (rolls.isEmpty()) {
            rolls.put(1, withDefaults(Map.of(
                ContractRank.F, 55,
                ContractRank.E, 25,
                ContractRank.D, 12,
                ContractRank.C, 6,
                ContractRank.B, 2,
                ContractRank.A, 0,
                ContractRank.S, 0
            ), Map.of()));
            rolls.put(5, withDefaults(Map.of(
                ContractRank.F, 40,
                ContractRank.E, 28,
                ContractRank.D, 16,
                ContractRank.C, 10,
                ContractRank.B, 5,
                ContractRank.A, 1,
                ContractRank.S, 0
            ), Map.of()));
            rolls.put(10, withDefaults(Map.of(
                ContractRank.F, 26,
                ContractRank.E, 24,
                ContractRank.D, 20,
                ContractRank.C, 14,
                ContractRank.B, 10,
                ContractRank.A, 5,
                ContractRank.S, 1
            ), Map.of()));
            rolls.put(15, withDefaults(Map.of(
                ContractRank.F, 16,
                ContractRank.E, 20,
                ContractRank.D, 22,
                ContractRank.C, 18,
                ContractRank.B, 14,
                ContractRank.A, 8,
                ContractRank.S, 2
            ), Map.of()));
            rolls.put(20, withDefaults(Map.of(
                ContractRank.F, 10,
                ContractRank.E, 16,
                ContractRank.D, 22,
                ContractRank.C, 20,
                ContractRank.B, 18,
                ContractRank.A, 11,
                ContractRank.S, 3
            ), Map.of()));
        }
        this.rankRollTable = Collections.unmodifiableNavigableMap(rolls);
        this.antiFrustration = antiFrustration == null ? AntiFrustrationSettings.defaults() : antiFrustration;
        this.xpPerLevel = Math.max(1, xpPerLevel);
        this.maxLevel = Math.max(1, maxLevel);
    }

    public static ProgressionSettings defaults() {
        return new ProgressionSettings(null, null, null, null, 10, 20);
    }

    public Map<ContractRank, Integer> variantTargets() {
        return Map.copyOf(variantTargets);
    }

    public int targetFor(ContractRank rank) {
        return variantTargets.getOrDefault(rank, 0);
    }

    public int xpFor(ContractRank rank) {
        return contractXp.getOrDefault(rank == null ? ContractRank.C : rank, 0);
    }

    public Map<ContractRank, Integer> weightsForLevel(int level) {
        Map.Entry<Integer, EnumMap<ContractRank, Integer>> entry = rankRollTable.floorEntry(Math.max(1, level));
        if (entry == null) {
            entry = rankRollTable.firstEntry();
        }
        return Map.copyOf(entry.getValue());
    }

    public int levelForXp(int xp) {
        return Math.min(maxLevel, Math.max(1, 1 + Math.max(0, xp) / xpPerLevel));
    }

    public AntiFrustrationSettings antiFrustration() {
        return antiFrustration;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public int xpPerLevel() {
        return xpPerLevel;
    }

    private static EnumMap<ContractRank, Integer> withDefaults(Map<ContractRank, Integer> source, Map<ContractRank, Integer> defaults) {
        EnumMap<ContractRank, Integer> result = new EnumMap<>(ContractRank.class);
        for (ContractRank rank : ContractRank.values()) {
            int fallback = defaults.getOrDefault(rank, 0);
            int value = source == null ? fallback : Math.max(0, source.getOrDefault(rank, fallback));
            result.put(rank, value);
        }
        return result;
    }
}
