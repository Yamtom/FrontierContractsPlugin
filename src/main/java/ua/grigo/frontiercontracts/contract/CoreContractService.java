package ua.grigo.frontiercontracts.contract;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import ua.grigo.frontiercontracts.core.ContractRankRoller;
import ua.grigo.frontiercontracts.core.ProgressionService;
import ua.grigo.frontiercontracts.model.BonusConfig;
import ua.grigo.frontiercontracts.model.Board;
import ua.grigo.frontiercontracts.model.ConstructionMetadata;
import ua.grigo.frontiercontracts.model.ContractOffer;
import ua.grigo.frontiercontracts.model.ContractRank;
import ua.grigo.frontiercontracts.model.ContractScope;
import ua.grigo.frontiercontracts.model.ContractTemplate;
import ua.grigo.frontiercontracts.model.RewardBundle;
import ua.grigo.frontiercontracts.model.SettlementProgress;
import ua.grigo.frontiercontracts.storage.StorageService;

/**
 * Spec-compliant offer-generation entry point.
 *
 * <p>Generates one set of board offers (LOCAL×3, REGIONAL×2, GLOBAL×1) using:
 * <ol>
 *   <li>Effective player level derived from {@link Board#difficultyModifier()} — no live player
 *       scan is required; difficulty already encodes the settlement's intended tier.</li>
 *   <li>Pity counter drawn from per-board {@link SettlementProgress#rankedWithoutHigh()}.</li>
 *   <li>{@link ContractRankRoller#rollRank(int, int)} for each slot.</li>
 *   <li>Template pool filtered by scope / rank / contractPools / regionTags, then weighted-random
 *       selected with recentVariants exclusion (zero-weight, not hard-excluded, with graceful
 *       fallback to the unfiltered pool).</li>
 *   <li>Each offer gets a fresh UUID and {@code expiresAt = now + scope.lifetimeMinutes()}.</li>
 *   <li>Saved via {@link StorageService#saveOffer(ContractOffer)}.</li>
 * </ol>
 *
 * <p>Pity state update: after rolling each rank, if it is a high rank (B or above) the in-memory
 * pityCounter resets to 0; otherwise it increments. The caller (or scheduler) is responsible for
 * persisting the updated {@link SettlementProgress} back to the DB.
 */
public final class CoreContractService {

    private static final Logger LOGGER = Logger.getLogger(CoreContractService.class.getName());

    /**
     * Full scope schedule: LOCAL×3, REGIONAL×2, GLOBAL×1 as per the spec.
     */
    static final List<ContractScope> SCOPE_SCHEDULE = List.of(
        ContractScope.LOCAL,
        ContractScope.LOCAL,
        ContractScope.LOCAL,
        ContractScope.REGIONAL,
        ContractScope.REGIONAL,
        ContractScope.GLOBAL
    );

    private final ContractTemplateRegistry registry;
    private final ContractRankRoller rankRoller;
    private final StorageService storage;
    private final ProgressionService progressionService;

    /**
     * Shared reference to the live settlement-progress map maintained by the application.
     * {@link CoreContractService} reads from it but never writes back — pity increments are
     * returned in the generated {@link GenerationResult} for the caller to persist.
     */
    private final Map<String, SettlementProgress> settlementProgress;

    public CoreContractService(
        ContractTemplateRegistry registry,
        ContractRankRoller rankRoller,
        StorageService storage,
        ProgressionService progressionService,
        Map<String, SettlementProgress> settlementProgress
    ) {
        this.registry = registry;
        this.rankRoller = rankRoller;
        this.storage = storage;
        this.progressionService = progressionService;
        this.settlementProgress = settlementProgress;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generates and persists offers for all scope slots of the given board.
     *
     * <p>Step-by-step:
     * <ol>
     *   <li>Determine effective level from {@link Board#difficultyModifier()}.
     *   <li>Load pity counter + recentVariants from the board's {@link SettlementProgress}.
     *   <li>For each slot in {@link #SCOPE_SCHEDULE}: roll rank, filter, select, build, persist.
     *   <li>Return a {@link GenerationResult} containing the new offers and the updated pity state.
     * </ol>
     *
     * @param board the board to generate offers for
     * @return result containing generated offers and updated pity counters
     */
    public GenerationResult generateOffersForBoard(Board board) {
        SettlementProgress progress = settlementProgress.get(board.id());
        int pityCounter = progress == null ? 0 : progress.rankedWithoutHigh();
        List<String> recentVariants = progress == null ? List.of() : progress.recentVariants();

        // 1. Effective level: each 0.1 above baseline 1.0 ≈ +1 tier
        int effectiveLevel = deriveEffectiveLevel(board.difficultyModifier());

        long now = System.currentTimeMillis() / 1000L;
        List<ContractOffer> offers = new ArrayList<>();
        int rolledWithoutHigh = pityCounter;

        // 2. Generate one offer per scope slot
        for (ContractScope scope : SCOPE_SCHEDULE) {

            // a. Roll rank
            ContractRank rank = rankRoller.rollRank(effectiveLevel, rolledWithoutHigh);

            // b. Filter templates: scope + rank + contractPools + regionTags
            List<ContractTemplate> pool = filterTemplates(scope, rank, board);

            // c. Apply recentVariants exclusion (templates in recent queue get weight 0 = excluded)
            List<ContractTemplate> candidates = excludeRecent(pool, recentVariants);
            if (candidates.isEmpty()) candidates = pool;  // graceful fallback
            if (candidates.isEmpty()) {
                // No matching template for this scope+rank — try any rank in scope
                candidates = filterByScope(scope, board);
                if (candidates.isEmpty()) {
                    LOGGER.fine("No template found for board=" + board.id() + " scope=" + scope + " rank=" + rank);
                    continue;
                }
            }

            // d. Weighted random selection
            ContractTemplate selected = weightedSelect(candidates);
            if (selected == null) continue;

            // e. Build offer
            long expiresAt = now + (long) scope.lifetimeMinutes() * 60L;
            long durationSeconds = (long) selected.durationMinutes() * 60L;
            ContractOffer offer = new ContractOffer(
                java.util.UUID.randomUUID().toString(),
                selected.key(),
                board.id(),
                scope,
                null, null, null,
                selected.type(),
                rank,
                selected.difficulty(),
                new java.util.ArrayList<>(selected.requirements()),
                ConstructionMetadata.empty(),
                null,
                selected.partialDeliveryAllowed(),
                selected.publicOffer(),
                durationSeconds,
                now,
                expiresAt,
                selected.iconMaterial(),
                selected.title(),
                "",
                selected.rewards(),
                selected.bonusReward(),
                selected.bonusConfig(),
                selected.maxPlayers(),
                0,
                false,
                true
            );

            // f. Save to DB
            try {
                storage.saveOffer(offer);
                offers.add(offer);
            } catch (SQLException e) {
                LOGGER.warning("Failed to persist offer for board=" + board.id() + ": " + e.getMessage());
                offers.add(offer); // still include in result even if persist failed
            }

            // Update in-memory pity: high rank resets, else increment
            if (rank.isHighRank()) {
                rolledWithoutHigh = 0;
            } else {
                rolledWithoutHigh++;
            }
        }

        return new GenerationResult(offers, rolledWithoutHigh);
    }

    // -------------------------------------------------------------------------
    // recentVariants queue helpers (spec section: player_stats.recent_variants_blob)
    // -------------------------------------------------------------------------

    /**
     * Adds {@code templateId} to the player's recentVariants FIFO queue, capped at
     * {@code maxSize} (configured as {@code recentVariantsQueueSize}, default 20).
     *
     * <p>Delegates to {@link ua.grigo.frontiercontracts.model.PlayerStats#updateRecentVariants}.
     */
    public static void updateRecentVariants(
        ua.grigo.frontiercontracts.model.PlayerStats stats,
        String templateId,
        int maxSize
    ) {
        stats.updateRecentVariants(templateId, maxSize);
    }

    // -------------------------------------------------------------------------
    // Filtering helpers
    // -------------------------------------------------------------------------

    private List<ContractTemplate> filterTemplates(ContractScope scope, ContractRank rank, Board board) {
        List<ContractTemplate> result = new ArrayList<>();
        for (ContractTemplate t : registry.all()) {
            if (t.scope() != scope) continue;
            if (t.rank() != rank) continue;
            if (!t.isAvailableToBoard(board.contractPools())) continue;
            if (!isRegionCompatible(t, board)) continue;
            result.add(t);
        }
        return result;
    }

    /** Broad fallback: returns all templates compatible with this board for the given scope, any rank. */
    private List<ContractTemplate> filterByScope(ContractScope scope, Board board) {
        List<ContractTemplate> result = new ArrayList<>();
        for (ContractTemplate t : registry.all()) {
            if (t.scope() != scope) continue;
            if (!t.isAvailableToBoard(board.contractPools())) continue;
            result.add(t);
        }
        return result;
    }

    /**
     * Returns templates not in the recentVariants list.
     * Templates in the list effectively get weight 0 (spec wording).
     */
    private static List<ContractTemplate> excludeRecent(
        List<ContractTemplate> pool,
        List<String> recentVariants
    ) {
        if (recentVariants.isEmpty()) return pool;
        List<ContractTemplate> filtered = new ArrayList<>();
        for (ContractTemplate t : pool) {
            if (!recentVariants.contains(t.key())) filtered.add(t);
        }
        return filtered;
    }

    /**
     * A REGIONAL template is only compatible with the board when:
     * <ul>
     *   <li>the template has no regionTags, OR
     *   <li>the board's regionTag is among the template's regionTags.
     * </ul>
     * LOCAL and GLOBAL templates are always compatible.
     */
    private static boolean isRegionCompatible(ContractTemplate t, Board board) {
        if (t.scope() != ContractScope.REGIONAL) return true;
        List<String> regionTags = t.regionTags();
        if (regionTags == null || regionTags.isEmpty()) return true;
        String boardRegion = board.regionTag();
        return boardRegion != null && regionTags.contains(boardRegion);
    }

    /**
     * Selects one template by weighted random using {@link ContractTemplate#weight()}.
     * Templates with weight ≤ 0 are skipped.
     * Returns {@code null} only if the pool is empty.
     */
    static ContractTemplate weightedSelect(List<ContractTemplate> templates) {
        if (templates.isEmpty()) return null;
        int total = 0;
        for (ContractTemplate t : templates) total += Math.max(0, t.weight());
        if (total <= 0) return templates.get(0); // equal-weight fallback
        int roll = (int) (Math.random() * total);
        int cumulative = 0;
        for (ContractTemplate t : templates) {
            int w = Math.max(0, t.weight());
            if (w == 0) continue;
            cumulative += w;
            if (roll < cumulative) return t;
        }
        return templates.getLast();
    }

    // -------------------------------------------------------------------------
    // Level derivation
    // -------------------------------------------------------------------------

    /**
     * Derives an effective player level from the board's difficulty modifier.
     *
     * <p>Baseline difficulty 1.0 → level 0 (lowest weight tier).
     * Each 0.1 above 1.0 adds 1 effective level, capped at {@link ProgressionService#maxLevel()}.
     * Difficulty below 1.0 maps to level 0.
     */
    int deriveEffectiveLevel(double difficultyModifier) {
        int level = (int) Math.round((difficultyModifier - 1.0) * 10.0);
        return Math.max(0, Math.min(level, progressionService.maxLevel()));
    }

    // -------------------------------------------------------------------------
    // Result record
    // -------------------------------------------------------------------------

    /**
     * Carries the generated offers and the updated pity counter back to the caller so that
     * the caller can persist {@link SettlementProgress} with the new {@code rankedWithoutHigh}.
     */
    public record GenerationResult(
        List<ContractOffer> offers,
        int updatedRankedWithoutHigh
    ) {}
}
