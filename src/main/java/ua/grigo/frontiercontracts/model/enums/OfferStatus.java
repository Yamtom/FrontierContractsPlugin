package ua.grigo.frontiercontracts.model.enums;

import java.util.Locale;

/**
 * Lifecycle status of a {@code ContractOffer} that has been generated and stored in the DB.
 *
 * <p>State machine:
 * <pre>
 *   ACTIVE → ACCEPTED → COMPLETED
 *          ↘          ↗
 *           EXPIRED
 * </pre>
 */
public enum OfferStatus {

    /** Offer is visible on the board and available for players to accept. */
    ACTIVE,

    /** At least one player has accepted this offer; it is no longer shown as available. */
    ACCEPTED,

    /** All required deliveries/construction work has been fulfilled. */
    COMPLETED,

    /** Offer lifetime elapsed before all active contracts were completed. */
    EXPIRED;

    public boolean isOpen() {
        return this == ACTIVE;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == EXPIRED;
    }

    public static OfferStatus fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return ACTIVE;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ACTIVE;
        }
    }
}
