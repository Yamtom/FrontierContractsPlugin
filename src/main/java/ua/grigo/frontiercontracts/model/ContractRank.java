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

    public boolean isHighRank() {
        return this == B || this == A || this == S;
    }

    public boolean isEliteRank() {
        return this == A || this == S;
    }

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

    public static ContractRank parseNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ContractRank.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
