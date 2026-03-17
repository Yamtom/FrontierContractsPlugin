package ua.grigo.frontiercontracts.model;

import org.bukkit.Material;

public final class ContractRequirement {
    private final Material material;
    private final int amount;
    private int deliveredAmount;

    public ContractRequirement(Material material, int amount) {
        this(material, amount, 0);
    }

    public ContractRequirement(Material material, int amount, int deliveredAmount) {
        this.material = material == null ? Material.AIR : material;
        this.amount = Math.max(1, amount);
        this.deliveredAmount = Math.max(0, Math.min(deliveredAmount, this.amount));
    }

    public Material material() {
        return material;
    }

    public int amount() {
        return amount;
    }

    public int deliveredAmount() {
        return deliveredAmount;
    }

    public void setDeliveredAmount(int deliveredAmount) {
        this.deliveredAmount = Math.max(0, Math.min(deliveredAmount, amount));
    }

    public int remainingAmount() {
        return Math.max(0, amount - deliveredAmount);
    }

    public boolean isComplete() {
        return deliveredAmount >= amount;
    }

    public boolean accepts(Material candidate) {
        return candidate != null && candidate == material && !isComplete();
    }

    public void addDeliveredAmount(int delta) {
        setDeliveredAmount(deliveredAmount + Math.max(0, delta));
    }

    public ContractRequirement scaled(double difficultyModifier) {
        int scaledAmount = (int) Math.max(1, Math.round(amount * difficultyModifier));
        return new ContractRequirement(material, scaledAmount, 0);
    }

    public ContractRequirement copy() {
        return new ContractRequirement(material, amount, deliveredAmount);
    }
}
