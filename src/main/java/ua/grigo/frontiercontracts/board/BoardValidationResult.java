package ua.grigo.frontiercontracts.board;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import ua.grigo.frontiercontracts.model.BoardColumnSide;

public record BoardValidationResult(
    boolean valid,
    Location anchor,
    BlockFace facing,
    BoardColumnSide columnSide,
    Location bellLocation,
    double bellDistance,
    List<String> errors
) {
    public static BoardValidationResult invalid(Location anchor, BlockFace facing, BoardColumnSide columnSide, List<String> errors) {
        return new BoardValidationResult(false, anchor, facing, columnSide, null, -1.0D, List.copyOf(errors));
    }
}
