package ua.grigo.frontiercontracts.model;

/**
 * Contract categories. v0.1 generation is restricted to DELIVERY.
 * Other types are defined here so config and future modules can reference them without code changes.
 */
public enum ContractType {
    DELIVERY,
    PROCUREMENT,
    COMBAT,
    FARMING,
    EXPLORATION,
    DAILY,
    WEEKLY;

    public String displayName() {
        return switch (this) {
            case DELIVERY -> "Поставка";
            case PROCUREMENT -> "Закупівля";
            case COMBAT -> "Бойове";
            case FARMING -> "Сільське";
            case EXPLORATION -> "Розвідка";
            case DAILY -> "Щоденне";
            case WEEKLY -> "Щотижневе";
        };
    }

    public boolean isImplementedInV01() {
        return this == DELIVERY;
    }
}
