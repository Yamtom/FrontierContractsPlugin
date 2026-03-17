package ua.grigo.frontiercontracts.model;

public record ProjectGenerationSettings(
    boolean preferProjectInSpecialSlot,
    int defaultMinTrust,
    int defaultMinRoutineCompletions,
    int defaultMinProjectCompletions,
    long baseProjectCooldownSeconds,
    int trustPerRoutineCompletion,
    int trustPerProjectCompletion,
    int trustLevelDivisor
) {
    public ProjectGenerationSettings {
        defaultMinTrust = Math.max(0, defaultMinTrust);
        defaultMinRoutineCompletions = Math.max(0, defaultMinRoutineCompletions);
        defaultMinProjectCompletions = Math.max(0, defaultMinProjectCompletions);
        baseProjectCooldownSeconds = Math.max(0L, baseProjectCooldownSeconds);
        trustPerRoutineCompletion = Math.max(0, trustPerRoutineCompletion);
        trustPerProjectCompletion = Math.max(trustPerRoutineCompletion, trustPerProjectCompletion);
        trustLevelDivisor = Math.max(1, trustLevelDivisor);
    }

    public static ProjectGenerationSettings defaults() {
        return new ProjectGenerationSettings(true, 8, 10, 0, 7200L, 2, 5, 25);
    }
}
