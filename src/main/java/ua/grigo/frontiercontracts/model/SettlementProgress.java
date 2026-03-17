package ua.grigo.frontiercontracts.model;

import java.util.ArrayList;
import java.util.List;

public final class SettlementProgress {
    private final String boardId;
    private int trust;
    private int routineCompleted;
    private int projectCompleted;
    private long projectCooldownUntil;
    private int rankedWithoutHigh;
    private int rankedWithoutElite;
    private final List<String> recentVariants;
    private long updatedAt;

    public SettlementProgress(
        String boardId,
        int trust,
        int routineCompleted,
        int projectCompleted,
        long projectCooldownUntil,
        int rankedWithoutHigh,
        int rankedWithoutElite,
        List<String> recentVariants,
        long updatedAt
    ) {
        this.boardId = boardId;
        this.trust = Math.max(0, trust);
        this.routineCompleted = Math.max(0, routineCompleted);
        this.projectCompleted = Math.max(0, projectCompleted);
        this.projectCooldownUntil = Math.max(0L, projectCooldownUntil);
        this.rankedWithoutHigh = Math.max(0, rankedWithoutHigh);
        this.rankedWithoutElite = Math.max(0, rankedWithoutElite);
        this.recentVariants = recentVariants == null ? new ArrayList<>() : new ArrayList<>(recentVariants);
        this.updatedAt = Math.max(0L, updatedAt);
    }

    public String boardId() {
        return boardId;
    }

    public int trust() {
        return trust;
    }

    public void addTrust(int delta) {
        trust = Math.max(0, trust + delta);
    }

    public int routineCompleted() {
        return routineCompleted;
    }

    public void incrementRoutineCompleted() {
        routineCompleted++;
    }

    public int projectCompleted() {
        return projectCompleted;
    }

    public void incrementProjectCompleted() {
        projectCompleted++;
    }

    public long projectCooldownUntil() {
        return projectCooldownUntil;
    }

    public void setProjectCooldownUntil(long projectCooldownUntil) {
        this.projectCooldownUntil = Math.max(0L, projectCooldownUntil);
    }

    public int rankedWithoutHigh() {
        return rankedWithoutHigh;
    }

    public int rankedWithoutElite() {
        return rankedWithoutElite;
    }

    public List<String> recentVariants() {
        return List.copyOf(recentVariants);
    }

    public boolean hasRecentVariant(String variantKey) {
        return variantKey != null && recentVariants.contains(variantKey);
    }

    public void rememberVariant(String variantKey, int memory) {
        if (variantKey == null || variantKey.isBlank()) {
            return;
        }
        recentVariants.remove(variantKey);
        recentVariants.add(0, variantKey);
        while (recentVariants.size() > Math.max(1, memory)) {
            recentVariants.remove(recentVariants.size() - 1);
        }
    }

    public void recordRankedGeneration(ContractRank rank, String variantKey, AntiFrustrationSettings antiFrustration) {
        if (rank == null) {
            rememberVariant(variantKey, antiFrustration.recentVariantMemory());
            return;
        }
        if (rank == ContractRank.B || rank == ContractRank.A || rank == ContractRank.S) {
            rankedWithoutHigh = 0;
        } else {
            rankedWithoutHigh++;
        }
        if (rank == ContractRank.A || rank == ContractRank.S) {
            rankedWithoutElite = 0;
        } else {
            rankedWithoutElite++;
        }
        rememberVariant(variantKey, antiFrustration.recentVariantMemory());
    }

    public long updatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = Math.max(0L, updatedAt);
    }
}
