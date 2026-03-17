package ua.grigo.frontiercontracts.model;

public record AntiFrustrationSettings(
    int highRankPityThreshold,
    int eliteRankPityThreshold,
    int highRankWeightBonus,
    int eliteRankWeightBonus,
    int recentVariantMemory
) {
    public AntiFrustrationSettings {
        highRankPityThreshold = Math.max(1, highRankPityThreshold);
        eliteRankPityThreshold = Math.max(highRankPityThreshold, eliteRankPityThreshold);
        highRankWeightBonus = Math.max(0, highRankWeightBonus);
        eliteRankWeightBonus = Math.max(0, eliteRankWeightBonus);
        recentVariantMemory = Math.max(1, recentVariantMemory);
    }

    public static AntiFrustrationSettings defaults() {
        return new AntiFrustrationSettings(12, 25, 12, 8, 20);
    }
}
