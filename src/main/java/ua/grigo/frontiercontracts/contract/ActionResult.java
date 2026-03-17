package ua.grigo.frontiercontracts.contract;

import java.util.Map;

public record ActionResult(boolean success, String messageKey, Map<String, String> placeholders) {
    public static ActionResult success(String messageKey, Map<String, String> placeholders) {
        return new ActionResult(true, messageKey, placeholders);
    }

    public static ActionResult failure(String messageKey) {
        return new ActionResult(false, messageKey, Map.of());
    }
}
