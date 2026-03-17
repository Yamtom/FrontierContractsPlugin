package ua.grigo.frontiercontracts.model;

public enum BoardType {
    VILLAGE,
    TOWN,
    OUTPOST,
    MINE,
    FARM,
    PORT,
    GARRISON,
    CHECKPOINT,
    CAMP,
    CUSTOM;

    public String displayName() {
        return switch (this) {
            case VILLAGE -> "Village";
            case TOWN -> "Town";
            case OUTPOST -> "Outpost";
            case MINE -> "Mine";
            case FARM -> "Farm";
            case PORT -> "Port";
            case GARRISON -> "Garrison";
            case CHECKPOINT -> "Checkpoint";
            case CAMP -> "Camp";
            case CUSTOM -> "Settlement";
        };
    }

    public static BoardType fromString(String raw) {
        if (raw == null) {
            return CUSTOM;
        }
        try {
            return valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return CUSTOM;
        }
    }
}
