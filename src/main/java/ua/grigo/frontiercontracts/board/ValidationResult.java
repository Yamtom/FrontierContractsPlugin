package ua.grigo.frontiercontracts.board;

/**
 * Simplified result of a board structure validation pass.
 *
 * <p>For the full detail record (errors list, bell location, etc.) see {@link BoardValidationResult}.
 * Use this enum when a single status code is sufficient — e.g. logging, scheduler checks,
 * and offer-generation gate-keeping.
 */
public enum ValidationResult {

    /** The world the board is registered in is not currently loaded. */
    WORLD_MISSING,

    /** One or more physical blocks in the expected layout are wrong or absent. */
    INVALID_STRUCTURE,

    /** Physical structure is valid but this board has no contract templates in its pools. */
    EMPTY_LOCAL_POOL,

    /** Structure and pools are valid but offer generation failed (e.g. no eligible templates). */
    GENERATION_FAILED,

    /** All checks passed — board is healthy and ready to serve offers. */
    OK;

    /** Returns {@code true} for {@link #OK} only. */
    public boolean isOk() {
        return this == OK;
    }

    /** Returns {@code true} for any failure state. */
    public boolean isFailure() {
        return this != OK;
    }
}
