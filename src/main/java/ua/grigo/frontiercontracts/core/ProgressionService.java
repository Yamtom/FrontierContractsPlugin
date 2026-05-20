package ua.grigo.frontiercontracts.core;

import java.util.Map;
import ua.grigo.frontiercontracts.model.AntiFrustrationSettings;
import ua.grigo.frontiercontracts.model.ContractRank;
import ua.grigo.frontiercontracts.model.PlayerStats;
import ua.grigo.frontiercontracts.model.ProgressionSettings;

/**
 * Stateless service wrapper over {@link ProgressionSettings} that provides
 * level computation, prestige checks, and the XP-application logic for
 * contract completions.
 *
 * Inspired by AuraSkills quadratic levelling curve and ScaleLeveling prestige loop.
 */
public final class ProgressionService {

    private final ProgressionSettings settings;

    public ProgressionService(ProgressionSettings settings) {
        this.settings = settings;
    }

    // ------------------------------------------------------------------ level

    /** Returns the contractor level [1..maxLevel] for the given accumulated XP. */
    public int levelForXp(int xp) {
        return settings.levelForXp(xp);
    }

    /** Returns the cumulative XP required to reach the given level. */
    public int xpNeededForLevel(int level) {
        return settings.xpNeededForLevel(level);
    }

    /** Returns the rank-roll weight table for the given level. */
    public Map<ContractRank, Integer> weightsForLevel(int level) {
        return settings.weightsForLevel(level);
    }

    public AntiFrustrationSettings antiFrustration() {
        return settings.antiFrustration();
    }

    public int maxLevel() {
        return settings.maxLevel();
    }

    // --------------------------------------------------------------- prestige

    /**
     * XP multiplier applied at the moment of awarding XP.
     * Each prestige level adds {@code prestigeXpBonusPerLevel} (default 5 %).
     * Example: prestige 2 → 1.10× XP.
     */
    public double prestigeXpMultiplier(int prestigeLevel) {
        return 1.0 + settings.prestigeXpBonusPerLevel() * Math.max(0, prestigeLevel);
    }

    /**
     * Returns true if the player may prestige right now:
     * they are at max level AND have not yet reached the prestige cap.
     */
    public boolean canPrestige(PlayerStats stats) {
        return levelForXp(stats.xp()) >= settings.maxLevel()
            && stats.prestigeLevel() < settings.maxPrestige();
    }

    // --------------------------------------------------------------- completion

    /**
     * Applies XP and completion tracking after a contract is claimed.
     * <ul>
     *   <li>XP is scaled by the veteran prestige multiplier (AuraSkills-style).</li>
     *   <li>Sets {@code lastHighRankAt} for B-rank and above (spec field).</li>
     *   <li>Does NOT persist — caller is responsible for saving stats.</li>
     * </ul>
     */
    public void applyCompletion(PlayerStats stats, ContractRank rank) {
        int baseXp = settings.xpFor(rank);
        int scaledXp = (int) Math.round(baseXp * prestigeXpMultiplier(stats.prestigeLevel()));
        stats.addXp(scaledXp);
        stats.incrementContractsCompleted();
        if (rank == ContractRank.B || rank == ContractRank.A || rank == ContractRank.S) {
            stats.setLastHighRankAt(System.currentTimeMillis() / 1000L);
        }
    }
}
