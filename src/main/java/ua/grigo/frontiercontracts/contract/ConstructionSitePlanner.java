package ua.grigo.frontiercontracts.contract;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import ua.grigo.frontiercontracts.board.BoardLayout;
import ua.grigo.frontiercontracts.model.BlockPosition;
import ua.grigo.frontiercontracts.model.Board;
import ua.grigo.frontiercontracts.model.ConstructionMetadata;
import ua.grigo.frontiercontracts.model.ConstructionSite;
import ua.grigo.frontiercontracts.model.ContractTemplate;

public final class ConstructionSitePlanner {
    private static final BlockFace[] CARDINALS = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private static final int SITE_SEPARATION_RADIUS = 15;
    private static final int MAX_FLATNESS_VARIANCE = 2;

    private final Server server;

    public ConstructionSitePlanner(Server server) {
        this.server = server;
    }

    public ConstructionSite plan(Board board, ContractTemplate template, Collection<ConstructionSite> activeSites) {
        if (board == null || template == null || template.type() != ua.grigo.frontiercontracts.model.ContractType.CONSTRUCTION) {
            return null;
        }
        ConstructionMetadata metadata = template.metadata();
        Location bell = board.bellLocation(server);
        if (bell == null || bell.getWorld() == null) {
            return null;
        }
        Collection<ConstructionSite> sites = activeSites == null ? List.of() : activeSites;
        return metadata.isRoadProject()
            ? planRoad(board, template, bell, metadata, sites)
            : planBuilding(board, template, bell, metadata, sites);
    }

    private ConstructionSite planRoad(Board board, ContractTemplate template, Location bell, ConstructionMetadata metadata, Collection<ConstructionSite> activeSites) {
        Set<String> blocked = blockedKeys(board, bell, activeSites);
        int startIndex = Math.floorMod((board.id() + ":" + template.key()).hashCode(), CARDINALS.length);
        int tiles = Math.max(1, metadata.roadTiles() == null ? 1 : metadata.roadTiles());

        for (int directionOffset = 0; directionOffset < CARDINALS.length; directionOffset++) {
            BlockFace direction = CARDINALS[(startIndex + directionOffset) % CARDINALS.length];
            List<BlockPosition> tilesList = new ArrayList<>(tiles);
            boolean valid = true;
            for (int step = 0; step < tiles; step++) {
                int distance = 3 + step;
                int x = bell.getBlockX() + direction.getModX() * distance;
                int y = bell.getBlockY() - 1;
                int z = bell.getBlockZ() + direction.getModZ() * distance;
                String key = bell.getWorld().getName() + ":" + x + ":" + y + ":" + z;
                if (blocked.contains(key)) {
                    valid = false;
                    break;
                }
                tilesList.add(new BlockPosition(x, y, z));
            }
            if (valid) {
                return siteFromTiles(bell.getWorld(), tilesList);
            }
        }
        return null;
    }

    private ConstructionSite planBuilding(Board board, ContractTemplate template, Location bell, ConstructionMetadata metadata, Collection<ConstructionSite> activeSites) {
        Set<String> blocked = blockedKeys(board, bell, activeSites);
        int footprint = metadata.floorCount() >= 2 ? 9 : 7;
        int half = footprint / 2;
        int startIndex = Math.floorMod((board.id() + ":" + template.key()).hashCode(), CARDINALS.length);

        for (int directionOffset = 0; directionOffset < CARDINALS.length; directionOffset++) {
            BlockFace direction = CARDINALS[(startIndex + directionOffset) % CARDINALS.length];
            int anchorX = bell.getBlockX() + direction.getModX() * 6;
            int anchorY = bell.getBlockY();
            int anchorZ = bell.getBlockZ() + direction.getModZ() * 6;
            int minX = anchorX - half;
            int maxX = anchorX + half;
            int minZ = anchorZ - half;
            int maxZ = anchorZ + half;
            boolean valid = true;

            // Check 1: no board structure blocks in the footprint
            outer:
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    String key = bell.getWorld().getName() + ":" + x + ":" + (anchorY - 1) + ":" + z;
                    if (blocked.contains(key)) {
                        valid = false;
                        break outer;
                    }
                }
            }

            // Check 2: terrain flatness (surface height variance <= MAX_FLATNESS_VARIANCE)
            if (valid) {
                int minSurfaceY = Integer.MAX_VALUE;
                int maxSurfaceY = Integer.MIN_VALUE;
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        int surfaceY = bell.getWorld().getHighestBlockYAt(x, z);
                        if (surfaceY < minSurfaceY) minSurfaceY = surfaceY;
                        if (surfaceY > maxSurfaceY) maxSurfaceY = surfaceY;
                    }
                }
                if (maxSurfaceY - minSurfaceY > MAX_FLATNESS_VARIANCE) {
                    valid = false;
                }
            }

            // Check 3: minimum separation from existing construction sites
            if (valid) {
                String worldName = bell.getWorld().getName();
                for (ConstructionSite existing : activeSites) {
                    if (!existing.worldName().equals(worldName)) continue;
                    int dx = existing.anchorX() - anchorX;
                    int dz = existing.anchorZ() - anchorZ;
                    if (dx * dx + dz * dz < SITE_SEPARATION_RADIUS * SITE_SEPARATION_RADIUS) {
                        valid = false;
                        break;
                    }
                }
            }

            if (valid) {
                return new ConstructionSite(
                    bell.getWorld().getName(),
                    anchorX,
                    anchorY,
                    anchorZ,
                    minX,
                    anchorY - 1,
                    minZ,
                    maxX,
                    anchorY + Math.max(4, metadata.floorCount() * 4),
                    maxZ,
                    List.of()
                );
            }
        }
        return null;
    }

    private ConstructionSite siteFromTiles(World world, List<BlockPosition> tiles) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPosition tile : tiles) {
            minX = Math.min(minX, tile.x());
            minY = Math.min(minY, tile.y());
            minZ = Math.min(minZ, tile.z());
            maxX = Math.max(maxX, tile.x());
            maxY = Math.max(maxY, tile.y());
            maxZ = Math.max(maxZ, tile.z());
        }
        BlockPosition anchor = tiles.getFirst();
        return new ConstructionSite(world.getName(), anchor.x(), anchor.y(), anchor.z(), minX, minY, minZ, maxX, maxY, maxZ, tiles);
    }

    private Set<String> blockedKeys(Board board, Location bell, Collection<ConstructionSite> activeSites) {
        Set<String> blocked = new HashSet<>();
        BoardLayout layout = BoardLayout.fromBoard(board, server);
        if (layout != null) {
            blocked.add(BoardLayout.keyFor(layout.support()));
            layout.plankBlocks().forEach(location -> blocked.add(BoardLayout.keyFor(location)));
            layout.signBlocks().forEach(location -> blocked.add(BoardLayout.keyFor(location)));
        }
        String worldName = bell.getWorld().getName();
        blocked.add(worldName + ":" + bell.getBlockX() + ":" + bell.getBlockY() + ":" + bell.getBlockZ());
        blocked.add(worldName + ":" + bell.getBlockX() + ":" + (bell.getBlockY() - 1) + ":" + bell.getBlockZ());
        for (ConstructionSite existing : activeSites) {
            if (!existing.worldName().equals(worldName)) continue;
            for (BlockPosition tile : existing.roadTiles()) {
                blocked.add(worldName + ":" + tile.x() + ":" + tile.y() + ":" + tile.z());
            }
        }
        return blocked;
    }
}
