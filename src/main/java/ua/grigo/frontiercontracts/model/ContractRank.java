package ua.grigo.frontiercontracts.model;

import java.util.Locale;

public enum ContractRank {
    F,
    E,
    D,
    C,
    B,
    A,
    S;

    public static ContractRank fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return C;
        }
        try {
            return ContractRank.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return C;
        }
    }
}
