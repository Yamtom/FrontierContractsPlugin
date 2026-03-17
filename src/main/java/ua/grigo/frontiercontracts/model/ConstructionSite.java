package ua.grigo.frontiercontracts.model;

import java.util.List;
import java.util.Objects;

public record ConstructionSite(
    String worldName,
    int anchorX,
    int anchorY,
    int anchorZ,
    int minX,
    int minY,
    int minZ,
    int maxX,
    int maxY,
    int maxZ,
    List<BlockPosition> roadTiles
) {
    public ConstructionSite {
        worldName = Objects.requireNonNull(worldName);
        roadTiles = roadTiles == null ? List.of() : List.copyOf(roadTiles);
    }

    public boolean isRoadSite() {
        return !roadTiles.isEmpty();
    }

    public boolean contains(String worldName, int x, int y, int z) {
        return this.worldName.equals(worldName)
            && x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }
}
