package ua.grigo.frontiercontracts.board;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import ua.grigo.frontiercontracts.model.BoardColumnSide;

public final class BoardPlacementService {
    private static final Material DEFAULT_SIGN_MATERIAL = Material.matchMaterial("OAK_WALL_SIGN");
    private static final Material FALLBACK_SIGN_MATERIAL = Material.matchMaterial("OAK_SIGN");

    private final BoardStructureValidator validator;

    public BoardPlacementService(BoardStructureValidator validator) {
        this.validator = validator;
    }

    public BoardValidationResult placeDefault(Player player, Location anchor, BlockFace facing, BoardColumnSide columnSide) {
        BoardLayout layout = BoardLayout.fromAnchor(anchor, facing, columnSide.offsetFrom(facing));
        if (layout == null) {
            return BoardValidationResult.invalid(anchor, facing, columnSide, List.of("Board world is missing."));
        }

        List<BlockState> snapshots = new ArrayList<>();
        snapshots.add(layout.support().getBlock().getState());
        layout.plankBlocks().forEach(location -> snapshots.add(location.getBlock().getState()));
        layout.signBlocks().forEach(location -> snapshots.add(location.getBlock().getState()));

        try {
            if (!canReplace(layout.support().getBlock())) {
                return BoardValidationResult.invalid(anchor, facing, columnSide, List.of("Support location is blocked."));
            }
            for (Location location : layout.plankBlocks()) {
                if (!canReplace(location.getBlock())) {
                    return BoardValidationResult.invalid(anchor, facing, columnSide, List.of("Board surface space is blocked."));
                }
            }
            for (Location location : layout.signBlocks()) {
                if (!canReplace(location.getBlock())) {
                    return BoardValidationResult.invalid(anchor, facing, columnSide, List.of("Sign attachment space is blocked."));
                }
            }

            layout.support().getBlock().setType(Material.OAK_FENCE, false);
            for (Location location : layout.plankBlocks()) {
                location.getBlock().setType(Material.OAK_PLANKS, false);
            }
            for (Location location : layout.signBlocks()) {
                Material signMaterial = DEFAULT_SIGN_MATERIAL != null
                    ? DEFAULT_SIGN_MATERIAL
                    : (FALLBACK_SIGN_MATERIAL != null ? FALLBACK_SIGN_MATERIAL : Material.AIR);
                location.getBlock().setType(signMaterial, false);
                if (location.getBlock().getBlockData() instanceof Directional directional) {
                    directional.setFacing(facing);
                    location.getBlock().setBlockData(directional, false);
                }
                if (location.equals(layout.headerSign()) && location.getBlock().getState() instanceof Sign sign) {
                    sign.getSide(Side.FRONT).setLine(0, "contact");
                    sign.update(true, false);
                }
            }

            BoardValidationResult result = validator.validate(anchor, facing, columnSide);
            if (!result.valid()) {
                rollback(snapshots);
            }
            return result;
        } catch (RuntimeException exception) {
            rollback(snapshots);
            throw exception;
        }
    }

    private static boolean canReplace(Block block) {
        return block.isEmpty() || block.isPassable();
    }

    private static void rollback(List<BlockState> snapshots) {
        for (BlockState snapshot : snapshots) {
            snapshot.update(true, false);
        }
    }
}
