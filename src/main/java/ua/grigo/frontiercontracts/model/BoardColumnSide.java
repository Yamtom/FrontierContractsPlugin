package ua.grigo.frontiercontracts.model;

import java.util.Locale;
import org.bukkit.block.BlockFace;

/**
 * Side of the second board column relative to the board front.
 */
public enum BoardColumnSide {
    LEFT,
    RIGHT;

    public static BoardColumnSide fromString(String raw) {
        if (raw == null) {
            return RIGHT;
        }
        try {
            return valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return RIGHT;
        }
    }

    public BlockFace offsetFrom(BlockFace facing) {
        return this == LEFT ? rotateLeft(facing) : rotateRight(facing);
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
