package ua.grigo.frontiercontracts.model;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import ua.grigo.frontiercontracts.config.PluginSettings;
import ua.grigo.frontiercontracts.util.TextUtil;

public final class ContractTemplate {
    private static final DecimalFormat MONEY_FMT = new DecimalFormat("0.##");

    private final String key;
    private final ContractScope scope;
    private final ContractType type;
    private final ContractRank rank;
    private final String difficulty;
    private final int weight;
    private final Material iconMaterial;
    private final String title;
    private final List<String> description;
    private final List<ContractRequirement> requirements;
    private final ConstructionMetadata metadata;
    private final int durationMinutes;
    private final boolean partialDeliveryAllowed;
    private final boolean publicOffer;
    private final RewardBundle rewards;
    private final RewardBundle bonusReward;
    private final BonusConfig bonusConfig;
    private final List<String> poolTags;
    private final ProjectTrigger projectTrigger;

    public ContractTemplate(
        String key,
        ContractScope scope,
        ContractType type,
        ContractRank rank,
        String difficulty,
        int weight,
        Material iconMaterial,
        String title,
        List<String> description,
        List<ContractRequirement> requirements,
        ConstructionMetadata metadata,
        int durationMinutes,
        boolean partialDeliveryAllowed,
        boolean publicOffer,
        RewardBundle rewards,
        RewardBundle bonusReward,
        BonusConfig bonusConfig,
        List<String> poolTags,
        ProjectTrigger projectTrigger
    ) {
        this.key = key;
        this.scope = scope;
        this.type = type;
        this.rank = rank;
        this.difficulty = difficulty;
        this.weight = weight;
        this.requirements = requirements == null ? List.of() : requirements.stream().map(ContractRequirement::copy).toList();
        this.metadata = metadata == null ? ConstructionMetadata.empty() : metadata;
        Material fallbackMaterial = this.requirements.isEmpty() ? Material.CHEST : this.requirements.getFirst().material();
        this.iconMaterial = iconMaterial == null ? fallbackMaterial : iconMaterial;
        this.title = title;
        this.description = description == null ? List.of() : List.copyOf(description);
        this.durationMinutes = Math.max(1, durationMinutes);
        this.partialDeliveryAllowed = partialDeliveryAllowed;
        this.publicOffer = publicOffer;
        this.rewards = rewards == null ? RewardBundle.empty() : rewards;
        this.bonusReward = bonusReward == null ? RewardBundle.empty() : bonusReward;
        this.bonusConfig = bonusConfig == null ? new BonusConfig(1.0D) : bonusConfig;
        this.poolTags = poolTags == null ? List.of() : List.copyOf(poolTags);
        this.projectTrigger = projectTrigger == null ? ProjectTrigger.none() : projectTrigger;
    }

    public String key() { return key; }
    public ContractScope scope() { return scope; }
    public ContractType type() { return type; }
    public ContractRank rank() { return rank; }
    public String difficulty() { return difficulty; }
    public int weight() { return weight; }
    public Material iconMaterial() { return iconMaterial; }
    public String title() { return title; }
    public List<String> description() { return description; }
    public List<ContractRequirement> requirements() { return requirements; }
    public ConstructionMetadata metadata() { return metadata; }
    public int durationMinutes() { return durationMinutes; }
    public boolean partialDeliveryAllowed() { return partialDeliveryAllowed; }
    public boolean publicOffer() { return publicOffer; }
    public RewardBundle rewards() { return rewards; }
    public RewardBundle bonusReward() { return bonusReward; }
    public BonusConfig bonusConfig() { return bonusConfig; }
    public List<String> poolTags() { return poolTags; }
    public ProjectTrigger projectTrigger() { return projectTrigger; }

    public boolean isProjectTemplate() {
        return projectTrigger.enabled();
    }

    public boolean isAvailableToBoard(List<String> boardPools) {
        if (poolTags.isEmpty()) {
            return true;
        }
        for (String tag : poolTags) {
            if (boardPools.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    public ContractOffer createOffer(
        String boardId,
        double difficultyModifier,
        double rewardModifier,
        double reputationModifier,
        long nowEpochSeconds,
        PluginSettings settings,
        ConstructionSite site,
        boolean specialOffer
    ) {
        List<ContractRequirement> scaledRequirements = new ArrayList<>();
        for (ContractRequirement requirement : requirements) {
            scaledRequirements.add(requirement.scaled(difficultyModifier));
        }
        int scaledObjectiveAmount = scaledRequirements.stream().mapToInt(ContractRequirement::amount).sum();
        RewardBundle scaledRewards = rewards.scale(rewardModifier, reputationModifier);
        RewardBundle scaledBonusReward = bonusReward.scale(rewardModifier, reputationModifier);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%amount%", Integer.toString(scaledObjectiveAmount));
        placeholders.put("%target%", targetName());
        placeholders.put("%difficulty%", difficulty);
        placeholders.put("%duration_minutes%", Integer.toString(durationMinutes));
        placeholders.put("%money%", MONEY_FMT.format(scaledRewards.money()));
        placeholders.put("%reputation%", Integer.toString(scaledRewards.reputation()));

        long lifetimeMinutes = switch (scope) {
            case LOCAL -> settings.localOfferLifetimeMinutes();
            case REGIONAL -> settings.regionalOfferLifetimeMinutes();
            case GLOBAL -> settings.globalOfferLifetimeMinutes();
        };

        return new ContractOffer(
            UUID.randomUUID().toString(),
            key,
            boardId,
            scope,
            null,
            null,
            null,
            type,
            rank,
            difficulty,
            scaledRequirements,
            metadata,
            site,
            partialDeliveryAllowed,
            publicOffer,
            durationMinutes * 60L,
            nowEpochSeconds,
            nowEpochSeconds + lifetimeMinutes * 60L,
            iconMaterial,
            TextUtil.applyPlaceholders(title, placeholders),
            TextUtil.joinLines(TextUtil.applyPlaceholders(description, placeholders)),
            scaledRewards,
            scaledBonusReward,
            bonusConfig,
            specialOffer,
            true
        );
    }

    public String targetName() {
        if (type == ContractType.CONSTRUCTION) {
            return title;
        }
        return requirements.isEmpty() ? title : TextUtil.prettyToken(requirements.getFirst().material().name());
    }
}
