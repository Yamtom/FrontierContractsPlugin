package ua.grigo.frontiercontracts.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;

public final class ContractOffer {
    private final String id;
    private final String templateKey;
    private final String boardId;
    private final ContractScope scope;
    private final String sourceBoardId;
    private final String targetBoardId;
    private final UUID ownerUuid;
    private final ContractType type;
    private final ContractRank rank;
    private final String difficulty;
    private final List<ContractRequirement> requirements;
    private final ConstructionMetadata metadata;
    private final ConstructionSite site;
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
    private final boolean specialOffer;
    private final int maxPlayers;
    private int activePlayers;
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
        ContractRank rank,
        String difficulty,
        List<ContractRequirement> requirements,
        ConstructionMetadata metadata,
        ConstructionSite site,
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
        int maxPlayers,
        int activePlayers,
        boolean specialOffer,
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
        this.rank = rank;
        this.difficulty = difficulty;
        this.requirements = requirements == null ? List.of() : requirements.stream().map(ContractRequirement::copy).toList();
        this.metadata = metadata == null ? ConstructionMetadata.empty() : metadata;
        this.site = site;
        this.partialDeliveryAllowed = partialDeliveryAllowed;
        this.publicOffer = publicOffer;
        this.contractDurationSeconds = contractDurationSeconds;
        this.createdAtEpochSeconds = createdAtEpochSeconds;
        this.offerExpiresAtEpochSeconds = offerExpiresAtEpochSeconds;
        Material fallbackMaterial = this.requirements.isEmpty() ? Material.CHEST : this.requirements.getFirst().material();
        this.iconMaterial = iconMaterial == null ? fallbackMaterial : iconMaterial;
        this.title = title == null ? templateKey : title;
        this.description = description == null ? "" : description;
        this.rewards = rewards == null ? RewardBundle.empty() : rewards;
        this.bonusReward = bonusReward == null ? RewardBundle.empty() : bonusReward;
        this.bonusConfig = bonusConfig == null ? new BonusConfig(1.0D) : bonusConfig;
        this.maxPlayers = Math.max(1, maxPlayers);
        this.activePlayers = Math.max(0, activePlayers);
        this.specialOffer = specialOffer;
        this.active = active;
    }

    public String id() { return id; }
    public String templateKey() { return templateKey; }
    public String boardId() { return boardId; }
    public ContractScope scope() { return scope; }
    public String sourceBoardId() { return sourceBoardId; }
    public String targetBoardId() { return targetBoardId; }
    public UUID ownerUuid() { return ownerUuid; }
    public ContractType type() { return type; }
    public ContractRank rank() { return rank; }
    public String difficulty() { return difficulty; }
    public List<ContractRequirement> requirements() { return requirements; }
    public ConstructionMetadata metadata() { return metadata; }
    public ConstructionSite site() { return site; }
    public boolean partialDeliveryAllowed() { return partialDeliveryAllowed; }
    public boolean publicOffer() { return publicOffer; }
    public long contractDurationSeconds() { return contractDurationSeconds; }
    public long createdAtEpochSeconds() { return createdAtEpochSeconds; }
    public long offerExpiresAtEpochSeconds() { return offerExpiresAtEpochSeconds; }
    public Material iconMaterial() { return iconMaterial; }
    public String title() { return title; }
    public String description() { return description; }
    public RewardBundle rewards() { return rewards; }
    public RewardBundle bonusReward() { return bonusReward; }
    public BonusConfig bonusConfig() { return bonusConfig; }
    public boolean specialOffer() { return specialOffer; }
    public boolean active() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int maxPlayers() { return maxPlayers; }
    public int activePlayers() { return activePlayers; }
    public void setActivePlayers(int activePlayers) { this.activePlayers = Math.max(0, activePlayers); }
    public void incrementActivePlayers() { this.activePlayers++; }
    public void decrementActivePlayers() { if (activePlayers > 0) activePlayers--; }
    public boolean canAccept() { return activePlayers < maxPlayers; }

    public Material requiredMaterial() {
        return requirements.isEmpty() ? Material.AIR : requirements.getFirst().material();
    }

    public String objectiveKey() {
        return requiredMaterial().name();
    }

    public int requiredAmount() {
        return requirements.isEmpty() ? 0 : requirements.getFirst().amount();
    }

    public int objectiveAmount() {
        return totalRequiredAmount();
    }

    public int deliveredAmount() {
        return requirements.isEmpty() ? 0 : requirements.getFirst().deliveredAmount();
    }

    public void setDeliveredAmount(int deliveredAmount) {
        if (!requirements.isEmpty()) {
            requirements.getFirst().setDeliveredAmount(deliveredAmount);
        }
    }

    public boolean isExpired(long nowEpochSeconds) {
        return nowEpochSeconds >= offerExpiresAtEpochSeconds;
    }

    public boolean isBoardLocal() {
        return scope == ContractScope.LOCAL && boardId != null;
    }

    public boolean isBoardBackedShared() {
        return boardId != null && scope != ContractScope.GLOBAL;
    }

    public boolean isPersonalFor(UUID playerUuid) {
        return ownerUuid != null && ownerUuid.equals(playerUuid);
    }

    public boolean isConstruction() {
        return type == ContractType.CONSTRUCTION;
    }

    public boolean isProjectOffer() {
        return rank == null;
    }

    public boolean requiresWorldValidation() {
        return isConstruction() && metadata.isRoadProject() && site != null && site.isRoadSite();
    }

    public boolean acceptsMaterial(Material material) {
        return findRequirement(material) != null;
    }

    public ContractRequirement findRequirement(Material material) {
        if (material == null) {
            return null;
        }
        for (ContractRequirement requirement : requirements) {
            if (requirement.accepts(material)) {
                return requirement;
            }
        }
        return null;
    }

    public int remainingAmount() {
        return Math.max(0, totalRequiredAmount() - totalDeliveredAmount());
    }

    public boolean isComplete() {
        return requirements.stream().allMatch(ContractRequirement::isComplete);
    }

    public int totalRequiredAmount() {
        return requirements.stream().mapToInt(ContractRequirement::amount).sum();
    }

    public int totalDeliveredAmount() {
        return requirements.stream().mapToInt(ContractRequirement::deliveredAmount).sum();
    }

    public int completedRequirementCount() {
        return (int) requirements.stream().filter(ContractRequirement::isComplete).count();
    }

    public int totalRequirementCount() {
        return requirements.size();
    }

    public double aggregateCompletionRatio() {
        int total = totalRequiredAmount();
        if (total <= 0) {
            return 0.0D;
        }
        return Math.min(1.0D, (double) totalDeliveredAmount() / (double) total);
    }

    public List<ContractRequirement> pendingRequirements() {
        List<ContractRequirement> pending = new ArrayList<>();
        for (ContractRequirement requirement : requirements) {
            if (!requirement.isComplete()) {
                pending.add(requirement);
            }
        }
        return pending;
    }
}
