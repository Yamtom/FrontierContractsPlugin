package ua.grigo.frontiercontracts.model;

import org.bukkit.Material;

public record ConstructionMetadata(
    Material surface,
    Integer roadTiles,
    String recommendedTool,
    String building,
    Integer floors
) {
    public ConstructionMetadata {
        recommendedTool = recommendedTool == null ? "" : recommendedTool;
        building = building == null ? "" : building;
    }

    public static ConstructionMetadata empty() {
        return new ConstructionMetadata(null, null, null, "", null);
    }

    public boolean isRoadProject() {
        return surface != null && roadTiles != null && roadTiles > 0;
    }

    public boolean isBuildingProject() {
        return !building.isBlank() || (floors != null && floors > 0);
    }

    public int floorCount() {
        return floors == null ? 0 : Math.max(0, floors);
    }
}
