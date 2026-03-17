package ua.grigo.frontiercontracts.model;

public enum ContractScope {
    LOCAL,
    REGIONAL,
    GLOBAL;

    public String displayName() {
        return switch (this) {
            case LOCAL -> "Local";
            case REGIONAL -> "Regional";
            case GLOBAL -> "Global";
        };
    }
}
