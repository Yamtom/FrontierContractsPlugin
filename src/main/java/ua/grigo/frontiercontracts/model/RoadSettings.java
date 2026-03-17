package ua.grigo.frontiercontracts.model;

import java.util.Map;

public record RoadSettings(
    int dirtPerTile,
    Map<String, Integer> shovelCoverage
) {
    public RoadSettings {
        dirtPerTile = Math.max(1, dirtPerTile);
        shovelCoverage = shovelCoverage == null ? Map.of() : Map.copyOf(shovelCoverage);
    }

    public static RoadSettings defaults() {
        return new RoadSettings(1, Map.of());
    }

    public int coverageFor(String toolKey) {
        return shovelCoverage.getOrDefault(toolKey == null ? "" : toolKey.toUpperCase(java.util.Locale.ROOT), 0);
    }
}
