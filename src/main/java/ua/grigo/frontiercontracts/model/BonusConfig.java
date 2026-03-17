package ua.grigo.frontiercontracts.model;

public record BonusConfig(double noDeathMoneyMultiplier) {
    public boolean hasNoDeathBonus() {
        return noDeathMoneyMultiplier > 1.0D;
    }
}
