package ua.grigo.frontiercontracts.contract;

import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import ua.grigo.frontiercontracts.model.BlockPosition;
import ua.grigo.frontiercontracts.model.ConstructionMetadata;
import ua.grigo.frontiercontracts.model.ConstructionSite;
import ua.grigo.frontiercontracts.model.ContractOffer;
import ua.grigo.frontiercontracts.model.ContractRequirement;

public final class ConstructionValidator {
    private final Server server;

    public ConstructionValidator(Server server) {
        this.server = server;
    }

    /**
     * Validates a road site: every road tile must have the required surface material.
     * Returns true if the road is fully complete.
     */
    public boolean validateRoad(ContractOffer offer) {
        ConstructionMetadata metadata = offer.metadata();
        ConstructionSite site = offer.site();
        if (!metadata.isRoadProject() || site == null || metadata.surface() == null) {
            return false;
        }
        World world = server.getWorld(site.worldName());
        if (world == null) {
            return false;
        }
        for (BlockPosition tile : site.roadTiles()) {
            if (world.getBlockAt(tile.x(), tile.y(), tile.z()).getType() != metadata.surface()) {
                return false;
            }
            if (!world.getBlockAt(tile.x(), tile.y() - 1, tile.z()).getType().isSolid()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Scans the building AABB, updates each requirement's deliveredAmount based on actual
     * block counts in the world, and returns true if all requirements are now met.
     * <p>
     * This method mutates the offer's requirement delivered amounts and must be followed
     * by a persistOffer() call in the caller.
     */
    public boolean validateBuilding(ContractOffer offer) {
        ConstructionSite site = offer.site();
        if (site == null || offer.requirements().isEmpty()) {
            return false;
        }
        World world = server.getWorld(site.worldName());
        if (world == null) {
            return false;
        }

        Map<Material, Integer> blockCounts = new EnumMap<>(Material.class);
        for (int x = site.minX(); x <= site.maxX(); x++) {
            for (int y = site.minY(); y <= site.maxY(); y++) {
                for (int z = site.minZ(); z <= site.maxZ(); z++) {
                    Material mat = world.getBlockAt(x, y, z).getType();
                    if (mat != Material.AIR) {
                        blockCounts.merge(mat, 1, Integer::sum);
                    }
                }
            }
        }

        for (ContractRequirement requirement : offer.requirements()) {
            int count = blockCounts.getOrDefault(requirement.material(), 0);
            requirement.setDeliveredAmount(count);
        }

        return offer.isComplete();
    }
}
