package ua.grigo.frontiercontracts.model;

public enum ContractScope {
    LOCAL,
    REGIONAL,
    GLOBAL;

    public String displayName() {
        return switch (this) {
            case LOCAL -> "Місцевий";
            case REGIONAL -> "Регіональний";
            case GLOBAL -> "Глобальний";
        };
    }
}
