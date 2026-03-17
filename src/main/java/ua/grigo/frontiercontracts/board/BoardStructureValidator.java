package ua.grigo.frontiercontracts.board;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import ua.grigo.frontiercontracts.config.PluginSettings;
import ua.grigo.frontiercontracts.model.Board;
import ua.grigo.frontiercontracts.model.BoardColumnSide;
import ua.grigo.frontiercontracts.util.TextUtil;

public final class BoardStructureValidator {
    private static final Material BELL_MATERIAL = Material.matchMaterial("BELL");
    private static final Set<String> JOB_SITE_BLOCK_NAMES = Set.of(
        "BLAST_FURNACE",
        "BARREL",
        "BREWING_STAND",
        "CARTOGRAPHY_TABLE",
        "COMPOSTER",
        "FLETCHING_TABLE",
        "GRINDSTONE",
        "LECTERN",
        "LOOM",
        "SMITHING_TABLE",
        "SMOKER",
        "STONECUTTER"
    );

    private final Server server;
    private final PluginSettings settings;

    public BoardStructureValidator(Server server, PluginSettings settings) {
        this.server = server;
        this.settings = settings;
    }

    public BoardValidationResult validate(Board board) {
        Location anchor = board.location(server);
        if (anchor == null) {
            return BoardValidationResult.invalid(null, board.facing(), board.columnSide(), List.of("World is not loaded."));
        }
        return validate(anchor, board.facing(), board.columnSide());
    }

    public BoardValidationResult validate(Location anchor, BlockFace facing, BoardColumnSide columnSide) {
        List<String> errors = new ArrayList<>();
        BoardLayout layout = BoardLayout.fromAnchor(anchor, facing, columnSide.offsetFrom(facing));
        if (layout == null) {
            return BoardValidationResult.invalid(anchor, facing, columnSide, List.of("Board world is missing."));
        }

        Block support = layout.support().getBlock();
        if (!isFence(support.getType())) {
            errors.add("Support block must be a fence.");
        }

        for (Location plankLocation : layout.plankBlocks()) {
            if (!isPlank(plankLocation.getBlock().getType())) {
                errors.add("Board surface must be a 3x2 wall of wooden planks with a centered support.");
                break;
            }
        }

        boolean headerChecked = false;
        for (int index = 0; index < layout.signBlocks().size(); index++) {
            Block signBlock = layout.signBlocks().get(index).getBlock();
            if (!isWallSign(signBlock.getType())) {
                errors.add("Each plank block must have a front-attached wall sign.");
                break;
            }
            if (!(signBlock.getBlockData() instanceof Directional directional) || directional.getFacing() != facing) {
                errors.add("Front signs must face the board front.");
                break;
            }
            if (!headerChecked && layout.headerSign().equals(layout.signBlocks().get(index))) {
                headerChecked = true;
                if (!(signBlock.getState() instanceof Sign sign) || !normalizeSign(sign).equals("contact")) {
                    errors.add("Top-center header sign must display exactly 'contact'.");
                }
            }
        }

        Location bellLocation = findNearestBell(anchor);
        double bellDistance = bellLocation == null ? -1.0D : distance(anchor, bellLocation);
        if (bellLocation == null || bellDistance > settings.boardBellRadius()) {
            errors.add("No village bell found within " + (int) settings.boardBellRadius() + " blocks.");
        } else if (!hasVillagePoiNearBell(bellLocation)) {
            errors.add("Linked bell must belong to a village area with at least one villager bed or job site nearby.");
        }

        return new BoardValidationResult(
            errors.isEmpty(),
            anchor,
            facing,
            columnSide,
            bellLocation,
            bellDistance,
            List.copyOf(errors)
        );
    }

    public BoardValidationResult scan(Block target) {
        List<Candidate> candidates = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -3; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Block anchorBlock = target.getRelative(dx, dy, dz);
                    if (!isFence(anchorBlock.getType())) {
                        continue;
                    }
                    for (BlockFace facing : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
                        for (BoardColumnSide side : BoardColumnSide.values()) {
                            BoardValidationResult result = validate(anchorBlock.getLocation(), facing, side);
                            candidates.add(new Candidate(result, target.getLocation().distanceSquared(anchorBlock.getLocation())));
                        }
                    }
                }
            }
        }

        return candidates.stream()
            .sorted(Comparator
                .comparing((Candidate candidate) -> !candidate.result.valid())
                .thenComparingInt(candidate -> candidate.result.errors().size())
                .thenComparingDouble(Candidate::distanceSquared))
            .map(Candidate::result)
            .findFirst()
            .orElse(BoardValidationResult.invalid(target.getLocation(), BlockFace.NORTH, BoardColumnSide.RIGHT,
                List.of("No valid board structure found near the targeted block.")));
    }

    private Location findNearestBell(Location anchor) {
        int radius = (int) Math.ceil(settings.boardBellRadius());
        Location nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block block = anchor.getWorld().getBlockAt(
                        anchor.getBlockX() + dx,
                        anchor.getBlockY() + dy,
                        anchor.getBlockZ() + dz
                    );
                    if (BELL_MATERIAL == null || block.getType() != BELL_MATERIAL) {
                        continue;
                    }
                    double distance = distance(anchor, block.getLocation());
                    if (distance <= settings.boardBellRadius() && distance < nearestDistance) {
                        nearest = block.getLocation();
                        nearestDistance = distance;
                    }
                }
            }
        }
        return nearest;
    }

    private boolean hasVillagePoiNearBell(Location bellLocation) {
        int radius = (int) Math.ceil(settings.boardVillagePoiRadius());
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block block = bellLocation.getWorld().getBlockAt(
                        bellLocation.getBlockX() + dx,
                        bellLocation.getBlockY() + dy,
                        bellLocation.getBlockZ() + dz
                    );
                    if (isVillagePoi(block.getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isVillagePoi(Material material) {
        return isBed(material) || isJobSite(material);
    }

    private static boolean isBed(Material material) {
        return material != null && material.name().endsWith("_BED");
    }

    private static boolean isJobSite(Material material) {
        return material != null && JOB_SITE_BLOCK_NAMES.contains(material.name());
    }

    private static boolean isPlank(Material material) {
        return Tag.PLANKS.isTagged(material);
    }

    private static boolean isFence(Material material) {
        return material != null && material.name().endsWith("_FENCE");
    }

    private static boolean isWallSign(Material material) {
        return material != null && material.name().endsWith("_WALL_SIGN");
    }

    private static String normalizeSign(Sign sign) {
        StringBuilder builder = new StringBuilder();
        for (String line : sign.getLines()) {
            builder.append(line);
        }
        return TextUtil.normalizePlain(builder.toString());
    }

    private static double distance(Location a, Location b) {
        double ax = a.getBlockX() + 0.5D;
        double ay = a.getBlockY() + 0.5D;
        double az = a.getBlockZ() + 0.5D;
        double bx = b.getBlockX() + 0.5D;
        double by = b.getBlockY() + 0.5D;
        double bz = b.getBlockZ() + 0.5D;
        return Math.sqrt(Math.pow(ax - bx, 2) + Math.pow(ay - by, 2) + Math.pow(az - bz, 2));
    }

    private record Candidate(BoardValidationResult result, double distanceSquared) {
    }
}
