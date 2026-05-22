package ua.grigo.frontiercontracts.model.enums;

import java.util.Locale;

/**
 * Lifecycle status of a {@code PlayerContract} — the record created when a player accepts an offer.
 *
 * <p>Maps to the existing {@link ua.grigo.frontiercontracts.model.PlayerContractStatus} but uses
 * the canonical names defined by the data-model spec.  Both enums coexist; service code may use
 * either.  Conversion helpers are provided below.
 *
 * <p>State machine:
 * <pre>
 *   ACTIVE → COMPLETED
 *          → ABANDONED
 *          → EXPIRED
 * </pre>
 */
public enum ContractStatus {

    /** Player has accepted the offer and work is in progress. */
    ACTIVE,

    /** All requirements were fulfilled and rewards were distributed. */
    COMPLETED,

    /** Player explicitly abandoned the contract (reputation penalty applied). */
    ABANDONED,

    /** Contract deadline elapsed before all requirements were met. */
    EXPIRED;

    public boolean isOpen() {
        return this == ACTIVE;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == ABANDONED || this == EXPIRED;
    }

    public static ContractStatus fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return ACTIVE;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ACTIVE;
        }
    }

    /**
     * Converts the legacy {@link ua.grigo.frontiercontracts.model.PlayerContractStatus} to this enum.
     * {@code READY_TO_CLAIM} and {@code CLAIMED} both map to {@link #COMPLETED}.
     * {@code FAILED} maps to {@link #ABANDONED}.
     */
    public static ContractStatus from(ua.grigo.frontiercontracts.model.PlayerContractStatus legacy) {
        if (legacy == null) return ACTIVE;
        return switch (legacy) {
            case ACTIVE, READY_TO_CLAIM -> ACTIVE;
            case CLAIMED -> COMPLETED;
            case FAILED -> ABANDONED;
        };
    }

    /**
     * Converts this enum back to the legacy {@link ua.grigo.frontiercontracts.model.PlayerContractStatus}.
     */
    public ua.grigo.frontiercontracts.model.PlayerContractStatus toLegacy() {
        return switch (this) {
            case ACTIVE -> ua.grigo.frontiercontracts.model.PlayerContractStatus.ACTIVE;
            case COMPLETED -> ua.grigo.frontiercontracts.model.PlayerContractStatus.CLAIMED;
            case ABANDONED, EXPIRED -> ua.grigo.frontiercontracts.model.PlayerContractStatus.FAILED;
        };
    }
}
