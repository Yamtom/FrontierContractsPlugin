package ua.grigo.frontiercontracts.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;

public final class PlayerContract {
    private final String id;
    private final String offerId;
    /** The board this contract was accepted from. Used for reputation updates on completion/failure. */
    private final String boardId;
    private final ContractType type;
    private final UUID playerUuid;
    private int progress;
    private final long acceptedAtEpochSeconds;
    private final long expiresAtEpochSeconds;
    private boolean deathless;
    private final List<ContractRequirement> requirements;
    private PlayerContractStatus status;
    private Long completedAtEpochSeconds;

    public PlayerContract(
        String id,
        String offerId,
        String boardId,
        ContractType type,
        UUID playerUuid,
        int progress,
        List<ContractRequirement> requirements,
        long acceptedAtEpochSeconds,
        long expiresAtEpochSeconds,
        boolean deathless,
        PlayerContractStatus status,
        Long completedAtEpochSeconds
    ) {
        this.id = id;
        this.offerId = offerId;
        this.boardId = boardId;
        this.type = type == null ? ContractType.DELIVERY : type;
        this.playerUuid = playerUuid;
        this.progress = progress;
        this.requirements = requirements == null ? new ArrayList<>() : new ArrayList<>(requirements.stream().map(ContractRequirement::copy).toList());
        this.acceptedAtEpochSeconds = acceptedAtEpochSeconds;
        this.expiresAtEpochSeconds = expiresAtEpochSeconds;
        this.deathless = deathless;
        this.status = status;
        this.completedAtEpochSeconds = completedAtEpochSeconds;
    }

    public String id() {
        return id;
    }

    public String offerId() {
        return offerId;
    }

    public String boardId() {
        return boardId;
    }

    public ContractType type() {
        return type;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public int progress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public long acceptedAtEpochSeconds() {
        return acceptedAtEpochSeconds;
    }

    public long expiresAtEpochSeconds() {
        return expiresAtEpochSeconds;
    }

    public boolean deathless() {
        return deathless;
    }

    public void setDeathless(boolean deathless) {
        this.deathless = deathless;
    }

    public List<ContractRequirement> requirements() {
        return requirements;
    }

    public PlayerContractStatus status() {
        return status;
    }

    public void setStatus(PlayerContractStatus status) {
        this.status = status;
    }

    public Long completedAtEpochSeconds() {
        return completedAtEpochSeconds;
    }

    public void setCompletedAtEpochSeconds(Long completedAtEpochSeconds) {
        this.completedAtEpochSeconds = completedAtEpochSeconds;
    }

    public boolean isExpired(long nowEpochSeconds) {
        return nowEpochSeconds >= expiresAtEpochSeconds;
    }

    public long remainingSeconds(long nowEpochSeconds) {
        return Math.max(0L, expiresAtEpochSeconds - nowEpochSeconds);
    }

    public boolean isFulfilled() {
        return !requirements.isEmpty() && requirements.stream().allMatch(ContractRequirement::isComplete);
    }

    public ContractRequirement findRequirement(Material material) {
        if (material == null) {
            return null;
        }
        for (ContractRequirement req : requirements) {
            if (req.accepts(material)) {
                return req;
            }
        }
        return null;
    }
}
