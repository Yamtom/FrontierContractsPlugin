package ua.grigo.frontiercontracts.model;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

public record RewardBundle(
    double money,
    int reputation,
    List<RewardItem> itemRewards,
    List<String> commandRewards
) {
    public RewardBundle {
        itemRewards = itemRewards == null ? List.of() : List.copyOf(itemRewards);
        commandRewards = commandRewards == null ? List.of() : List.copyOf(commandRewards);
    }

    public static RewardBundle empty() {
        return new RewardBundle(0.0D, 0, List.of(), List.of());
    }

    public boolean hasAnyReward() {
        return money > 0.0D || reputation > 0 || hasItemRewards() || hasCommandRewards();
    }

    public boolean hasItemRewards() {
        return itemRewards.stream().anyMatch(RewardItem::isValid);
    }

    public boolean hasCommandRewards() {
        return commandRewards.stream().anyMatch(command -> command != null && !command.isBlank());
    }

    public boolean hasBonusItem() {
        return hasItemRewards();
    }

    public Material bonusItemMaterial() {
        return itemRewards.isEmpty() ? Material.AIR : itemRewards.getFirst().material();
    }

    public int bonusItemAmount() {
        return itemRewards.isEmpty() ? 0 : itemRewards.getFirst().amount();
    }

    public RewardBundle scale(double rewardModifier, double reputationModifier) {
        return new RewardBundle(
            round2(money * rewardModifier),
            (int) Math.max(0, Math.round(reputation * reputationModifier)),
            itemRewards,
            commandRewards
        );
    }

    public RewardBundle combine(RewardBundle other) {
        if (other == null || !other.hasAnyReward()) {
            return this;
        }

        List<RewardItem> mergedItems = new ArrayList<>(itemRewards);
        mergedItems.addAll(other.itemRewards);

        List<String> mergedCommands = new ArrayList<>(commandRewards);
        mergedCommands.addAll(other.commandRewards);

        return new RewardBundle(
            round2(money + other.money),
            reputation + other.reputation,
            mergedItems,
            mergedCommands
        );
    }

    private static double round2(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
