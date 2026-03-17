package ua.grigo.frontiercontracts.model;

import java.util.UUID;

public final class PlayerStats {
    private final UUID playerUuid;
    private int reputation;
    private int completedContracts;
    private int failedContracts;

    public PlayerStats(UUID playerUuid, int reputation, int completedContracts, int failedContracts) {
        this.playerUuid = playerUuid;
        this.reputation = reputation;
        this.completedContracts = completedContracts;
        this.failedContracts = failedContracts;
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

    public int completedContracts() {
        return completedContracts;
    }

    public void incrementCompletedContracts() {
        this.completedContracts++;
    }

    public int failedContracts() {
        return failedContracts;
    }

    public void incrementFailedContracts() {
        this.failedContracts++;
    }
}
