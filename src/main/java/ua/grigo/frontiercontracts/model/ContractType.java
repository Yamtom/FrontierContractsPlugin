package ua.grigo.frontiercontracts.model;

public enum ContractType {
    DELIVERY,
    CONSTRUCTION,
    PROCUREMENT,
    COMBAT,
    FARMING,
    EXPLORATION,
    DAILY,
    WEEKLY;

    public String displayName() {
        return switch (this) {
            case DELIVERY -> "Delivery";
            case CONSTRUCTION -> "Construction";
            case PROCUREMENT -> "Procurement";
            case COMBAT -> "Combat";
            case FARMING -> "Farming";
            case EXPLORATION -> "Exploration";
            case DAILY -> "Daily";
            case WEEKLY -> "Weekly";
        };
    }

    public boolean isImplementedInV01() {
        return this == DELIVERY || this == CONSTRUCTION;
    }
}
