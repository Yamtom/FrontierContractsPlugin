package ua.grigo.frontiercontracts.model;

public record ConstructionRules(
    boolean allowMultiMaterialTurnIn,
    boolean completeOnlyWhenAllRequirementsMet,
    boolean publicConstructionProjects,
    String signProgressFormat,
    int wheatPerFloor
) {
    public ConstructionRules {
        signProgressFormat = signProgressFormat == null || signProgressFormat.isBlank()
            ? "{done}/{total} req"
            : signProgressFormat;
        wheatPerFloor = Math.max(1, wheatPerFloor);
    }

    public static ConstructionRules defaults() {
        return new ConstructionRules(true, true, true, "{done}/{total} req", 3);
    }
}
