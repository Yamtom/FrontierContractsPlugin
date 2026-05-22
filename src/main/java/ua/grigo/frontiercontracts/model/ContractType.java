package ua.grigo.frontiercontracts.model;

public enum ContractType {
    DELIVERY,
    CONSTRUCTION,
    /** Large-scale build unlocked by sufficient settlement trust (reputation trigger). */
    PROJECT,
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
            case PROJECT -> "Project";
            case PROCUREMENT -> "Procurement";
            case COMBAT -> "Combat";
            case FARMING -> "Farming";
            case EXPLORATION -> "Exploration";
            case DAILY -> "Daily";
            case WEEKLY -> "Weekly";
        };
    }

    public boolean isImplementedInV01() {
        return this == DELIVERY || this == CONSTRUCTION || this == PROJECT;
    }
}
