package ua.grigo.frontiercontracts.contract;

import java.util.ArrayList;
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

    private final Server server;

    public ConstructionSitePlanner(Server server) {
        this.server = server;
    }

    public ConstructionSite plan(Board board, ContractTemplate template) {
        if (board == null || template == null || template.type() != ua.grigo.frontiercontracts.model.ContractType.CONSTRUCTION) {
            return null;
        }
        ConstructionMetadata metadata = template.metadata();
        Location bell = board.bellLocation(server);
        if (bell == null || bell.getWorld() == null) {
            return null;
        }
        return metadata.isRoadProject()
            ? planRoad(board, template, bell, metadata)
            : planBuilding(board, template, bell, metadata);
    }

    private ConstructionSite planRoad(Board board, ContractTemplate template, Location bell, ConstructionMetadata metadata) {
        Set<String> blocked = blockedKeys(board, bell);
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

    private ConstructionSite planBuilding(Board board, ContractTemplate template, Location bell, ConstructionMetadata metadata) {
        Set<String> blocked = blockedKeys(board, bell);
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
            for (int x = minX; x <= maxX && valid; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    String key = bell.getWorld().getName() + ":" + x + ":" + (anchorY - 1) + ":" + z;
                    if (blocked.contains(key)) {
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

    private Set<String> blockedKeys(Board board, Location bell) {
        Set<String> blocked = new HashSet<>();
        BoardLayout layout = BoardLayout.fromBoard(board, server);
        if (layout != null) {
            blocked.add(BoardLayout.keyFor(layout.support()));
            layout.plankBlocks().forEach(location -> blocked.add(BoardLayout.keyFor(location)));
            layout.signBlocks().forEach(location -> blocked.add(BoardLayout.keyFor(location)));
        }
        blocked.add(bell.getWorld().getName() + ":" + bell.getBlockX() + ":" + bell.getBlockY() + ":" + bell.getBlockZ());
        blocked.add(bell.getWorld().getName() + ":" + bell.getBlockX() + ":" + (bell.getBlockY() - 1) + ":" + bell.getBlockZ());
        return blocked;
    }
}
