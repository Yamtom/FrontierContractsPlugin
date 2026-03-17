package ua.grigo.frontiercontracts.model;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import ua.grigo.frontiercontracts.config.PluginSettings;
import ua.grigo.frontiercontracts.util.TextUtil;

/**
 * Immutable template definition loaded from contracts.yml.
 * A template is instantiated into a {@link ContractOffer} at generation time.
 */
public final class ContractTemplate {
    private static final DecimalFormat MONEY_FMT = new DecimalFormat("0.##");

    private final String key;
    private final ContractScope scope;
    private final ContractType type;
    private final String difficulty;
    private final int weight;
    private final Material iconMaterial;
    private final String title;
    private final List<String> description;
    private final Material deliveryMaterial;
    private final int objectiveAmount;
    private final int durationMinutes;
    private final boolean partialDeliveryAllowed;
    private final boolean publicOffer;
    private final RewardBundle rewards;
    private final RewardBundle bonusReward;
    private final BonusConfig bonusConfig;
    /** Pool tags used to match this template to board contract-pools. Empty = available to all boards. */
    private final List<String> poolTags;

    public ContractTemplate(
        String key,
        ContractScope scope,
        ContractType type,
        String difficulty,
        int weight,
        Material iconMaterial,
        String title,
        List<String> description,
        Material deliveryMaterial,
        int objectiveAmount,
        int durationMinutes,
        boolean partialDeliveryAllowed,
        boolean publicOffer,
        RewardBundle rewards,
        RewardBundle bonusReward,
        BonusConfig bonusConfig,
        List<String> poolTags
    ) {
        this.key = key;
        this.scope = scope;
        this.type = type;
        this.difficulty = difficulty;
        this.weight = weight;
        this.iconMaterial = iconMaterial == null ? deliveryMaterial : iconMaterial;
        this.title = title;
        this.description = description == null ? List.of() : List.copyOf(description);
        this.deliveryMaterial = deliveryMaterial;
        this.objectiveAmount = Math.max(1, objectiveAmount);
        this.durationMinutes = Math.max(1, durationMinutes);
        this.partialDeliveryAllowed = partialDeliveryAllowed;
        this.publicOffer = publicOffer;
        this.rewards = rewards == null ? RewardBundle.empty() : rewards;
        this.bonusReward = bonusReward == null ? RewardBundle.empty() : bonusReward;
        this.bonusConfig = bonusConfig == null ? new BonusConfig(1.0D) : bonusConfig;
        this.poolTags = poolTags == null ? List.of() : List.copyOf(poolTags);
    }

    public String key() { return key; }
    public ContractScope scope() { return scope; }
    public ContractType type() { return type; }
    public String difficulty() { return difficulty; }
    public int weight() { return weight; }
    public List<String> poolTags() { return poolTags; }

    /**
     * True if this template belongs to a given board's pool.
     * A template with no pool tags is available to every board.
     */
    public boolean isAvailableToBoard(List<String> boardPools) {
        if (poolTags.isEmpty()) return true;
        for (String tag : poolTags) {
            if (boardPools.contains(tag)) return true;
        }
        return false;
    }

    public ContractOffer createOffer(
        String boardId,
        double difficultyModifier,
        double rewardModifier,
        double reputationModifier,
        long nowEpochSeconds,
        PluginSettings settings
    ) {
        int scaledObjectiveAmount = (int) Math.max(1, Math.round(objectiveAmount * difficultyModifier));
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
            difficulty,
            deliveryMaterial,
            scaledObjectiveAmount,
            0,
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
            true
        );
    }

    public String targetName() {
        return TextUtil.prettyToken(deliveryMaterial.name());
    }
}
