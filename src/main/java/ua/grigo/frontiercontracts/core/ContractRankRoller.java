package ua.grigo.frontiercontracts.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import ua.grigo.frontiercontracts.model.AntiFrustrationSettings;
import ua.grigo.frontiercontracts.model.ContractRank;
import ua.grigo.frontiercontracts.model.SettlementProgress;

/**
 * Produces a weighted-random rank order for contract generation.
 *
 * Extracted from {@code ContractService} so it can be tested and reasoned about
 * independently. Applies pity bonuses from {@link SettlementProgress} (per-board
 * anti-frustration counters) on top of the base weight table.
 */
public final class ContractRankRoller {

    private final ProgressionService progressionService;

    public ContractRankRoller(ProgressionService progressionService) {
        this.progressionService = progressionService;
    }

    /**
     * Returns ranks in weighted-random priority order.
     * Ranks with zero weight appear at the end (safety fallback) in enum order.
     *
     * @param effectiveLevel   generation level used for the base weight lookup
     * @param progress         per-board progress (may be null for global offers)
     * @param antiFrustration  pity thresholds and bonuses from config
     * @param allowedRanks     ranks eligible for this slot
     */
    public List<ContractRank> rollRanks(
        int effectiveLevel,
        SettlementProgress progress,
        AntiFrustrationSettings antiFrustration,
        EnumSet<ContractRank> allowedRanks
    ) {
        Map<ContractRank, Integer> weights = buildWeights(effectiveLevel, progress, antiFrustration, allowedRanks);
        return weightedOrder(weights, allowedRanks);
    }

    /**
     * Rolls a single {@link ContractRank} using the weight table for {@code playerLevel}
     * and applying pity bonuses when {@code pityCounter} reaches the configured thresholds.
     *
     * <p>Spec-compliant simplified entry point:
     * <ul>
     *   <li>If {@code pityCounter} &gt;= {@code highRankPityThreshold}: add highRankWeightBonus to B and A weights.
     *   <li>If {@code pityCounter} &gt;= {@code eliteRankPityThreshold}: add eliteRankWeightBonus to S weight.
     * </ul>
     * After a high rank (B or above) is rolled, callers should reset pityCounter to 0.
     *
     * @param playerLevel  effective level used to look up the base weight table
     * @param pityCounter  number of consecutive non-high-rank rolls
     * @return a single rolled {@link ContractRank}
     */
    public ContractRank rollRank(int playerLevel, int pityCounter) {
        AntiFrustrationSettings af = progressionService.antiFrustration();
        Map<ContractRank, Integer> base = progressionService.weightsForLevel(playerLevel);
        EnumMap<ContractRank, Integer> weights = new EnumMap<>(ContractRank.class);
        for (ContractRank rank : ContractRank.values()) {
            weights.put(rank, Math.max(0, base.getOrDefault(rank, 0)));
        }
        if (pityCounter >= af.highRankPityThreshold()) {
            weights.computeIfPresent(ContractRank.B, (k, v) -> v + af.highRankWeightBonus());
            weights.computeIfPresent(ContractRank.A, (k, v) -> v + af.highRankWeightBonus());
        }
        if (pityCounter >= af.eliteRankPityThreshold()) {
            weights.computeIfPresent(ContractRank.S, (k, v) -> v + af.eliteRankWeightBonus());
        }
        return singleWeightedDraw(weights);
    }

    /**
     * Selects a single rank by weighted random from the supplied weight map.
     * Entries with zero or negative weight are skipped.
     * Falls back to {@link ContractRank#C} if all weights are zero.
     */
    private ContractRank singleWeightedDraw(Map<ContractRank, Integer> weights) {
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) return ContractRank.C;
        int roll = (int) (Math.random() * total);
        int cumulative = 0;
        for (Map.Entry<ContractRank, Integer> entry : weights.entrySet()) {
            if (entry.getValue() <= 0) continue;
            cumulative += entry.getValue();
            if (roll < cumulative) return entry.getKey();
        }
        return ContractRank.C;
    }

    // ----------------------------------------------------------------- helpers

    private Map<ContractRank, Integer> buildWeights(
        int effectiveLevel,
        SettlementProgress progress,
        AntiFrustrationSettings antiFrustration,
        EnumSet<ContractRank> allowedRanks
    ) {
        EnumMap<ContractRank, Integer> adjusted = new EnumMap<>(ContractRank.class);
        Map<ContractRank, Integer> base = progressionService.weightsForLevel(effectiveLevel);
        for (ContractRank rank : ContractRank.values()) {
            adjusted.put(rank, allowedRanks.contains(rank) ? Math.max(0, base.getOrDefault(rank, 0)) : 0);
        }
        if (progress != null && antiFrustration != null) {
            if (progress.rankedWithoutHigh() >= antiFrustration.highRankPityThreshold()) {
                adjusted.computeIfPresent(ContractRank.B, (k, v) -> v + antiFrustration.highRankWeightBonus());
                adjusted.computeIfPresent(ContractRank.A, (k, v) -> v + antiFrustration.highRankWeightBonus());
                adjusted.computeIfPresent(ContractRank.S, (k, v) -> v + antiFrustration.highRankWeightBonus());
            }
            if (progress.rankedWithoutElite() >= antiFrustration.eliteRankPityThreshold()) {
                adjusted.computeIfPresent(ContractRank.A, (k, v) -> v + antiFrustration.eliteRankWeightBonus());
                adjusted.computeIfPresent(ContractRank.S, (k, v) -> v + antiFrustration.eliteRankWeightBonus());
            }
        }
        return adjusted;
    }

    private List<ContractRank> weightedOrder(Map<ContractRank, Integer> weights, EnumSet<ContractRank> allowedRanks) {
        List<ContractRank> order = new ArrayList<>();
        EnumMap<ContractRank, Integer> remaining = new EnumMap<>(ContractRank.class);
        for (ContractRank rank : ContractRank.values()) {
            int weight = allowedRanks.contains(rank) ? Math.max(0, weights.getOrDefault(rank, 0)) : 0;
            if (weight > 0) {
                remaining.put(rank, weight);
            }
        }
        while (!remaining.isEmpty()) {
            int total = remaining.values().stream().mapToInt(Integer::intValue).sum();
            if (total <= 0) {
                break;
            }
            int roll = (int) (Math.random() * total);
            int cumulative = 0;
            ContractRank picked = null;
            for (Map.Entry<ContractRank, Integer> entry : remaining.entrySet()) {
                cumulative += entry.getValue();
                if (roll < cumulative) {
                    picked = entry.getKey();
                    break;
                }
            }
            if (picked == null) {
                picked = remaining.keySet().iterator().next();
            }
            order.add(picked);
            remaining.remove(picked);
        }
        // Append any zero-weight ranks as fallback
        for (ContractRank rank : allowedRanks) {
            if (!order.contains(rank)) {
                order.add(rank);
            }
        }
        return order;
    }
}
