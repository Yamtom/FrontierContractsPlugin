package ua.grigo.frontiercontracts.model;

public enum ContractScope {
    LOCAL,
    REGIONAL,
    GLOBAL;

    /**
     * Number of simultaneous active offer slots this scope occupies on a board.
     * LOCAL=3, REGIONAL=2, GLOBAL=1.
     */
    public int slots() {
        return switch (this) {
            case LOCAL -> 3;
            case REGIONAL -> 2;
            case GLOBAL -> 1;
        };
    }

    /**
     * Default offer lifetime in minutes.
     * LOCAL=15 min, REGIONAL=60 min, GLOBAL=240 min (4 h).
     */
    public int lifetimeMinutes() {
        return switch (this) {
            case LOCAL -> 15;
            case REGIONAL -> 60;
            case GLOBAL -> 240;
        };
    }

    public String displayName() {
        return switch (this) {
            case LOCAL -> "Local";
            case REGIONAL -> "Regional";
            case GLOBAL -> "Global";
        };
    }
}
