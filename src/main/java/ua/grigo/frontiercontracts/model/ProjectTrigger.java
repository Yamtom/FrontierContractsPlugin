package ua.grigo.frontiercontracts.model;

public record ProjectTrigger(
    int minTrust,
    int minRoutineCompletions,
    int minProjectCompletions,
    long cooldownSeconds
) {
    public ProjectTrigger {
        minTrust = Math.max(0, minTrust);
        minRoutineCompletions = Math.max(0, minRoutineCompletions);
        minProjectCompletions = Math.max(0, minProjectCompletions);
        cooldownSeconds = Math.max(0L, cooldownSeconds);
    }

    public static ProjectTrigger none() {
        return new ProjectTrigger(0, 0, 0, 0L);
    }

    public boolean enabled() {
        return minTrust > 0 || minRoutineCompletions > 0 || minProjectCompletions > 0 || cooldownSeconds > 0L;
    }
}
