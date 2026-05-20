package ua.grigo.frontiercontracts.model;

import java.util.ArrayList;
import java.util.List;

public final class SettlementProgress {
    private final String boardId;
    private int trustLevel;
    private int routineCompletions;
    private int projectCompleted;
    private long projectCooldownUntil;
    private int rankedWithoutHigh;
    private int rankedWithoutElite;
    private final List<String> recentVariants;
    private long updatedAt;
    private final List<String> unlockedProjects;
    private String activeProjectId;

    public SettlementProgress(
        String boardId,
        int trustLevel,
        int routineCompletions,
        int projectCompleted,
        long projectCooldownUntil,
        int rankedWithoutHigh,
        int rankedWithoutElite,
        List<String> recentVariants,
        long updatedAt
    ) {
        this(boardId, trustLevel, routineCompletions, projectCompleted, projectCooldownUntil,
            rankedWithoutHigh, rankedWithoutElite, recentVariants, updatedAt, List.of(), null);
    }

    public SettlementProgress(
        String boardId,
        int trustLevel,
        int routineCompletions,
        int projectCompleted,
        long projectCooldownUntil,
        int rankedWithoutHigh,
        int rankedWithoutElite,
        List<String> recentVariants,
        long updatedAt,
        List<String> unlockedProjects,
        String activeProjectId
    ) {
        this.boardId = boardId;
        this.trustLevel = Math.max(0, trustLevel);
        this.routineCompletions = Math.max(0, routineCompletions);
        this.projectCompleted = Math.max(0, projectCompleted);
        this.projectCooldownUntil = Math.max(0L, projectCooldownUntil);
        this.rankedWithoutHigh = Math.max(0, rankedWithoutHigh);
        this.rankedWithoutElite = Math.max(0, rankedWithoutElite);
        this.recentVariants = recentVariants == null ? new ArrayList<>() : new ArrayList<>(recentVariants);
        this.updatedAt = Math.max(0L, updatedAt);
        this.unlockedProjects = unlockedProjects == null ? new ArrayList<>() : new ArrayList<>(unlockedProjects);
        this.activeProjectId = activeProjectId;
    }

    public String boardId() {
        return boardId;
    }

    public int trustLevel() {
        return trustLevel;
    }

    public void addTrustLevel(int delta) {
        trustLevel = Math.max(0, trustLevel + delta);
    }

    public int routineCompletions() {
        return routineCompletions;
    }

    public void incrementRoutineCompletions() {
        routineCompletions++;
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

    public List<String> unlockedProjects() {
        return List.copyOf(unlockedProjects);
    }

    public boolean hasUnlockedProject(String projectId) {
        return projectId != null && unlockedProjects.contains(projectId);
    }

    public void unlockProject(String projectId) {
        if (projectId != null && !projectId.isBlank() && !unlockedProjects.contains(projectId)) {
            unlockedProjects.add(projectId);
        }
    }

    public String activeProjectId() {
        return activeProjectId;
    }

    public void setActiveProjectId(String projectId) {
        this.activeProjectId = (projectId == null || projectId.isBlank()) ? null : projectId;
    }

    public boolean hasActiveProject() {
        return activeProjectId != null;
    }
}
