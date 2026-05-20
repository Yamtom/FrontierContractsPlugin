package ua.grigo.frontiercontracts.model;

import java.util.UUID;

public final class PlayerStats {
    private final UUID playerUuid;
    private int reputation;
    private int contractsCompleted;
    private int failedContracts;
    private int xp;
    private long lastHighRankAt;
    private int prestigeLevel;

    public PlayerStats(UUID playerUuid, int reputation, int contractsCompleted, int failedContracts, int xp) {
        this(playerUuid, reputation, contractsCompleted, failedContracts, xp, 0L, 0);
    }

    public PlayerStats(UUID playerUuid, int reputation, int contractsCompleted, int failedContracts, int xp, long lastHighRankAt, int prestigeLevel) {
        this.playerUuid = playerUuid;
        this.reputation = reputation;
        this.contractsCompleted = contractsCompleted;
        this.failedContracts = failedContracts;
        this.xp = Math.max(0, xp);
        this.lastHighRankAt = Math.max(0L, lastHighRankAt);
        this.prestigeLevel = Math.max(0, prestigeLevel);
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

    /**
     * Performs a prestige: increments prestige level and resets accumulated XP to 0.
     * Caller must verify eligibility (max level reached, prestige cap not hit) before calling.
     */
    public void prestige() {
        prestigeLevel++;
        xp = 0;
    }
}
