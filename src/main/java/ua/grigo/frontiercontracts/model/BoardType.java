package ua.grigo.frontiercontracts.model;

/**
 * Predefined settlement archetypes. Each type has a display label and a short flavour description
 * used in the board GUI header. New types can be added here without breaking existing boards.yml
 * entries that use a custom free-form type string stored on the Board itself.
 */
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
            case VILLAGE -> "Селище";
            case TOWN -> "Місто";
            case OUTPOST -> "Форпост";
            case MINE -> "Шахта";
            case FARM -> "Ферма";
            case PORT -> "Порт";
            case GARRISON -> "Гарнізон";
            case CHECKPOINT -> "Пост";
            case CAMP -> "Табір";
            case CUSTOM -> "Поселення";
        };
    }

    public static BoardType fromString(String raw) {
        if (raw == null) return CUSTOM;
        try {
            return valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return CUSTOM;
        }
    }
}
