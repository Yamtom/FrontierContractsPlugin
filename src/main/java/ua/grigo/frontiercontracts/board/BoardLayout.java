package ua.grigo.frontiercontracts.board;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import ua.grigo.frontiercontracts.model.Board;

public record BoardLayout(
    Location support,
    List<Location> plankBlocks,
    List<Location> signBlocks,
    Location headerSign
) {
    public static final int TASK_SIGN_COUNT = 5;

    public static BoardLayout fromBoard(Board board, Server server) {
        Location anchor = board.location(server);
        return anchor == null ? null : fromAnchor(anchor, board.facing(), board.columnSide().offsetFrom(board.facing()));
    }

    public static BoardLayout fromAnchor(Location anchor, BlockFace facing, BlockFace columnOffset) {
        World world = anchor.getWorld();
        if (world == null) {
            return null;
        }

        Location support = blockLocation(world, anchor.getBlockX(), anchor.getBlockY(), anchor.getBlockZ());
        List<Location> planks = new ArrayList<>(6);
        List<Location> signs = new ArrayList<>(6);
        BlockFace left = rotateLeft(facing);
        BlockFace right = rotateRight(facing);

        for (int height = 2; height >= 1; height--) {
            Location leftPlank = offset(support, left.getModX(), height, left.getModZ());
            Location centerPlank = offset(support, 0, height, 0);
            Location rightPlank = offset(support, right.getModX(), height, right.getModZ());
            planks.add(leftPlank);
            planks.add(centerPlank);
            planks.add(rightPlank);
            signs.add(offset(leftPlank, facing.getModX(), 0, facing.getModZ()));
            signs.add(offset(centerPlank, facing.getModX(), 0, facing.getModZ()));
            signs.add(offset(rightPlank, facing.getModX(), 0, facing.getModZ()));
        }

        return new BoardLayout(support, List.copyOf(planks), List.copyOf(signs), signs.get(1));
    }

    public Set<String> interactionKeys() {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(keyFor(support));
        plankBlocks.forEach(location -> keys.add(keyFor(location)));
        signBlocks.forEach(location -> keys.add(keyFor(location)));
        return keys;
    }

    public List<Location> taskSignBlocks() {
        List<Location> taskSigns = new ArrayList<>();
        for (Location location : signBlocks) {
            if (!location.equals(headerSign)) {
                taskSigns.add(location);
            }
        }
        return List.copyOf(taskSigns);
    }

    public static String keyFor(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private static Location offset(Location base, int dx, int dy, int dz) {
        return blockLocation(base.getWorld(), base.getBlockX() + dx, base.getBlockY() + dy, base.getBlockZ() + dz);
    }

    private static Location blockLocation(World world, int x, int y, int z) {
        return new Location(world, x, y, z);
    }

    private static BlockFace rotateLeft(BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.WEST;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            case WEST -> BlockFace.SOUTH;
            default -> BlockFace.WEST;
        };
    }

    private static BlockFace rotateRight(BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.EAST;
            case SOUTH -> BlockFace.WEST;
            case EAST -> BlockFace.SOUTH;
            case WEST -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }
}
