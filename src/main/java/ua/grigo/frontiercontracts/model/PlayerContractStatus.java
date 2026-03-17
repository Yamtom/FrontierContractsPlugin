package ua.grigo.frontiercontracts.model;

public enum PlayerContractStatus {
    ACTIVE,
    READY_TO_CLAIM,
    CLAIMED,
    FAILED;

    public boolean isOpen() {
        return this == ACTIVE || this == READY_TO_CLAIM;
    }
}
