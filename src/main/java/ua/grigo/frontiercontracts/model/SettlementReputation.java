package ua.grigo.frontiercontracts.model;

/**
 * Tracks a single player's reputation with one specific settlement board.
 * Reputation gates tier access and scales payouts according to config.
 */
public final class SettlementReputation {
    private final String boardId;
    private final java.util.UUID playerUuid;
    private int reputation;
    private long updatedAt;
    private int lifetimeCompleted;

    public SettlementReputation(String boardId, java.util.UUID playerUuid, int reputation, long updatedAt) {
        this(boardId, playerUuid, reputation, updatedAt, 0);
    }

    public SettlementReputation(String boardId, java.util.UUID playerUuid, int reputation, long updatedAt, int lifetimeCompleted) {
        this.boardId = boardId;
        this.playerUuid = playerUuid;
        this.reputation = reputation;
        this.updatedAt = updatedAt;
        this.lifetimeCompleted = Math.max(0, lifetimeCompleted);
    }

    public String boardId() { return boardId; }
    public java.util.UUID playerUuid() { return playerUuid; }

    public int reputation() { return reputation; }
    public void add(int delta) {
        this.reputation += delta;
        this.updatedAt = System.currentTimeMillis() / 1000L;
    }

    public long updatedAt() { return updatedAt; }

    public int lifetimeCompleted() { return lifetimeCompleted; }

    public void incrementLifetimeCompleted() {
        this.lifetimeCompleted++;
    }
}
