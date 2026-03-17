package ua.grigo.frontiercontracts.model;

import java.util.UUID;
import org.bukkit.Material;

public final class ContractOffer {
    private final String id;
    private final String templateKey;
    /** The board this offer belongs to. Null only for GLOBAL offers with no source board. */
    private final String boardId;
    private final ContractScope scope;
    /** For transport-style contracts: where items originate (v0.2). May be null. */
    private final String sourceBoardId;
    /** For REGIONAL/transport contracts: where delivery should be made. May be null. */
    private final String targetBoardId;
    private final UUID ownerUuid;
    private final ContractType type;
    private final String difficulty;
    private final Material requiredMaterial;
    private final int requiredAmount;
    private int deliveredAmount;
    private final boolean partialDeliveryAllowed;
    private final boolean publicOffer;
    private final long contractDurationSeconds;
    private final long createdAtEpochSeconds;
    private final long offerExpiresAtEpochSeconds;
    private final Material iconMaterial;
    private final String title;
    private final String description;
    private final RewardBundle rewards;
    private final RewardBundle bonusReward;
    private final BonusConfig bonusConfig;
    private boolean active;

    public ContractOffer(
        String id,
        String templateKey,
        String boardId,
        ContractScope scope,
        String sourceBoardId,
        String targetBoardId,
        UUID ownerUuid,
        ContractType type,
        String difficulty,
        Material requiredMaterial,
        int requiredAmount,
        int deliveredAmount,
        boolean partialDeliveryAllowed,
        boolean publicOffer,
        long contractDurationSeconds,
        long createdAtEpochSeconds,
        long offerExpiresAtEpochSeconds,
        Material iconMaterial,
        String title,
        String description,
        RewardBundle rewards,
        RewardBundle bonusReward,
        BonusConfig bonusConfig,
        boolean active
    ) {
        this.id = id;
        this.templateKey = templateKey;
        this.boardId = boardId;
        this.scope = scope;
        this.sourceBoardId = sourceBoardId;
        this.targetBoardId = targetBoardId;
        this.ownerUuid = ownerUuid;
        this.type = type;
        this.difficulty = difficulty;
        this.requiredMaterial = requiredMaterial == null ? Material.AIR : requiredMaterial;
        this.requiredAmount = Math.max(1, requiredAmount);
        this.deliveredAmount = Math.max(0, Math.min(deliveredAmount, this.requiredAmount));
        this.partialDeliveryAllowed = partialDeliveryAllowed;
        this.publicOffer = publicOffer;
        this.contractDurationSeconds = contractDurationSeconds;
        this.createdAtEpochSeconds = createdAtEpochSeconds;
        this.offerExpiresAtEpochSeconds = offerExpiresAtEpochSeconds;
        this.iconMaterial = iconMaterial == null ? this.requiredMaterial : iconMaterial;
        this.title = title == null ? templateKey : title;
        this.description = description == null ? "" : description;
        this.rewards = rewards == null ? RewardBundle.empty() : rewards;
        this.bonusReward = bonusReward == null ? RewardBundle.empty() : bonusReward;
        this.bonusConfig = bonusConfig == null ? new BonusConfig(1.0D) : bonusConfig;
        this.active = active;
    }

    public String id() {
        return id;
    }

    public String templateKey() {
        return templateKey;
    }

    public String boardId() {
        return boardId;
    }

    public ContractScope scope() {
        return scope;
    }

    public String sourceBoardId() {
        return sourceBoardId;
    }

    public String targetBoardId() {
        return targetBoardId;
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public ContractType type() {
        return type;
    }

    public String difficulty() {
        return difficulty;
    }

    public Material requiredMaterial() {
        return requiredMaterial;
    }

    public String objectiveKey() {
        return requiredMaterial.name();
    }

    public int requiredAmount() {
        return requiredAmount;
    }

    public int objectiveAmount() {
        return requiredAmount;
    }

    public int deliveredAmount() {
        return deliveredAmount;
    }

    public void setDeliveredAmount(int deliveredAmount) {
        this.deliveredAmount = Math.max(0, Math.min(deliveredAmount, requiredAmount));
    }

    public boolean partialDeliveryAllowed() {
        return partialDeliveryAllowed;
    }

    public boolean publicOffer() {
        return publicOffer;
    }

    public long contractDurationSeconds() {
        return contractDurationSeconds;
    }

    public long createdAtEpochSeconds() {
        return createdAtEpochSeconds;
    }

    public long offerExpiresAtEpochSeconds() {
        return offerExpiresAtEpochSeconds;
    }

    public Material iconMaterial() {
        return iconMaterial;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public RewardBundle rewards() {
        return rewards;
    }

    public RewardBundle bonusReward() {
        return bonusReward;
    }

    public BonusConfig bonusConfig() {
        return bonusConfig;
    }

    public boolean active() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isExpired(long nowEpochSeconds) {
        return nowEpochSeconds >= offerExpiresAtEpochSeconds;
    }

    public boolean isBoardLocal() {
        return scope == ContractScope.LOCAL && boardId != null;
    }

    public boolean isPersonalFor(UUID playerUuid) {
        return ownerUuid != null && ownerUuid.equals(playerUuid);
    }

    public boolean acceptsMaterial(Material material) {
        return material != null && material == requiredMaterial;
    }

    public int remainingAmount() {
        return Math.max(0, requiredAmount - deliveredAmount);
    }

    public boolean isComplete() {
        return remainingAmount() <= 0;
    }
}
