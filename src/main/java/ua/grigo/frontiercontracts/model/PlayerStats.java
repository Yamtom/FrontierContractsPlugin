package ua.grigo.frontiercontracts.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class PlayerStats {
    private final UUID playerUuid;
    private int reputation;
    private int contractsCompleted;
    private int failedContracts;
    private int xp;
    private long lastHighRankAt;
    private int prestigeLevel;
    /** Number of consecutive contract rolls that did not yield a high rank (B+). Used for pity system. */
    private int pityCounter;
    /** FIFO queue of recently completed template ids. Caps at recentVariantsQueueSize. */
    private final List<String> recentVariants;

    /** 5-param convenience constructor — all newer fields default to 0 / empty. */
    public PlayerStats(UUID playerUuid, int reputation, int contractsCompleted, int failedContracts, int xp) {
        this(playerUuid, reputation, contractsCompleted, failedContracts, xp, 0L, 0, 0, List.of());
    }

    /** 7-param backward-compat constructor — pityCounter and recentVariants default to 0 / empty. */
    public PlayerStats(UUID playerUuid, int reputation, int contractsCompleted, int failedContracts, int xp, long lastHighRankAt, int prestigeLevel) {
        this(playerUuid, reputation, contractsCompleted, failedContracts, xp, lastHighRankAt, prestigeLevel, 0, List.of());
    }

    /** Full constructor — primary. */
    public PlayerStats(UUID playerUuid, int reputation, int contractsCompleted, int failedContracts, int xp, long lastHighRankAt, int prestigeLevel, int pityCounter, List<String> recentVariants) {
        this.playerUuid = playerUuid;
        this.reputation = reputation;
        this.contractsCompleted = contractsCompleted;
        this.failedContracts = failedContracts;
        this.xp = Math.max(0, xp);
        this.lastHighRankAt = Math.max(0L, lastHighRankAt);
        this.prestigeLevel = Math.max(0, prestigeLevel);
        this.pityCounter = Math.max(0, pityCounter);
        this.recentVariants = recentVariants == null ? new ArrayList<>() : new ArrayList<>(recentVariants);
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public int reputation() {
        return reputation;
    }

    public void addReputation(int value) {
        this.reputation += value;
    }

    public int contractsCompleted() {
        return contractsCompleted;
    }

    public void incrementContractsCompleted() {
        this.contractsCompleted++;
    }

    public int failedContracts() {
        return failedContracts;
    }

    public void incrementFailedContracts() {
        this.failedContracts++;
    }

    public int xp() {
        return xp;
    }

    public void addXp(int value) {
        xp = Math.max(0, xp + Math.max(0, value));
    }

    public long lastHighRankAt() {
        return lastHighRankAt;
    }

    public void setLastHighRankAt(long epochSeconds) {
        this.lastHighRankAt = Math.max(0L, epochSeconds);
    }

    public int prestigeLevel() {
        return prestigeLevel;
    }

    public int pityCounter() {
        return pityCounter;
    }

    public void setPityCounter(int pityCounter) {
        this.pityCounter = Math.max(0, pityCounter);
    }

    public void incrementPityCounter() {
        this.pityCounter++;
    }

    public void resetPityCounter() {
        this.pityCounter = 0;
    }

    /** Returns an unmodifiable view of the recent-variants queue. */
    public List<String> recentVariants() {
        return Collections.unmodifiableList(recentVariants);
    }

    /**
     * Adds {@code templateId} to the FIFO recent-variants queue, capped at {@code maxSize}.
     * Oldest entry is removed if the cap is exceeded.
     *
     * @param templateId the completed template id to record
     * @param maxSize    maximum queue size (typically recentVariantsQueueSize from config)
     */
    public void updateRecentVariants(String templateId, int maxSize) {
        if (templateId == null || templateId.isBlank()) return;
        recentVariants.remove(templateId); // de-duplicate: move to tail
        recentVariants.add(templateId);
        int cap = Math.max(1, maxSize);
        while (recentVariants.size() > cap) {
            recentVariants.removeFirst();
        }
    }

    /**
     * Performs a prestige: increments prestige level and resets accumulated XP to 0.
     * Caller must verify eligibility (max level reached, prestige cap not hit) before calling.
     */
    public void prestige() {
        prestigeLevel++;
        xp = 0;
    }
}
