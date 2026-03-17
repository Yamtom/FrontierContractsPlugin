package ua.grigo.frontiercontracts.board;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ua.grigo.frontiercontracts.config.PluginSettings;
import ua.grigo.frontiercontracts.model.Board;
import ua.grigo.frontiercontracts.model.BoardColumnSide;
import ua.grigo.frontiercontracts.model.BoardType;
import ua.grigo.frontiercontracts.model.ContractType;

/**
 * Loads, persists, validates, and indexes settlement boards.
 */
public final class BoardService {
    public static final int PHYSICAL_TASK_SIGN_SLOTS = BoardLayout.TASK_SIGN_COUNT;

    private final File boardsFile;
    private final Server server;
    private final Logger logger;

    private BoardStructureValidator validator;
    private PluginSettings settings;

    private final Map<String, Board> boardsById = new LinkedHashMap<>();
    private final Map<String, String> locationIndex = new HashMap<>();

    public BoardService(File dataFolder, Server server, Logger logger) {
        this.boardsFile = new File(dataFolder, "boards.yml");
        this.server = server;
        this.logger = logger;
    }

    public void load(PluginSettings settings) {
        this.settings = settings;
        this.validator = new BoardStructureValidator(server, settings);
        boardsById.clear();
        locationIndex.clear();
        boolean normalized = false;

        if (!boardsFile.exists()) {
            logger.info("boards.yml not found. No boards loaded.");
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(boardsFile);
        ConfigurationSection root = yaml.getConfigurationSection("boards");
        if (root == null) {
            logger.info("boards.yml contains no 'boards' section.");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            Board board = parseBoard(id, section);
            if (board == null) {
                continue;
            }
            normalized |= board.localOfferSlots() != PHYSICAL_TASK_SIGN_SLOTS;
            applyValidation(board, validator.validate(board));
            register(board);
        }

        if (normalized) {
            save();
        }

        logger.info("Loaded " + boardsById.size() + " settlement board(s).");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Board board : boardsById.values()) {
            String prefix = "boards." + board.id();
            yaml.set(prefix + ".name", board.name());
            yaml.set(prefix + ".type", board.type().name());
            if (board.type() == BoardType.CUSTOM && board.customTypeName() != null) {
                yaml.set(prefix + ".custom-type-name", board.customTypeName());
            }
            yaml.set(prefix + ".world", board.worldName());
            yaml.set(prefix + ".anchor.x", board.anchorX());
            yaml.set(prefix + ".anchor.y", board.anchorY());
            yaml.set(prefix + ".anchor.z", board.anchorZ());
            yaml.set(prefix + ".facing", board.facing().name());
            yaml.set(prefix + ".column-side", board.columnSide().name());
            if (board.bellX() != null && board.bellY() != null && board.bellZ() != null) {
                yaml.set(prefix + ".bell.x", board.bellX());
                yaml.set(prefix + ".bell.y", board.bellY());
                yaml.set(prefix + ".bell.z", board.bellZ());
            }
            yaml.set(prefix + ".bell-distance", board.bellDistance());
            yaml.set(prefix + ".bell-distance-valid", board.bellDistanceValid());
            yaml.set(prefix + ".validation-errors", board.validationErrors());
            yaml.set(prefix + ".region-tag", board.regionTag());
            yaml.set(prefix + ".linked-boards", board.linkedBoardIds());
            yaml.set(prefix + ".contract-pools", board.contractPools());
            if (!board.categoryWeights().isEmpty()) {
                for (Map.Entry<String, Double> entry : board.categoryWeights().entrySet()) {
                    yaml.set(prefix + ".category-weights." + entry.getKey().toLowerCase(), entry.getValue());
                }
            }
            yaml.set(prefix + ".difficulty-modifier", board.difficultyModifier());
            yaml.set(prefix + ".reward-modifier", board.rewardModifier());
            yaml.set(prefix + ".reputation-modifier", board.reputationModifier());
            yaml.set(prefix + ".local-offer-slots", board.localOfferSlots());
            yaml.set(prefix + ".refresh-interval-seconds", board.refreshIntervalSeconds());
            yaml.set(prefix + ".next-refresh-at", board.nextRefreshAtEpochSeconds());
            if (board.hasFlavorText()) {
                yaml.set(prefix + ".flavor-text", board.flavorText());
            }
            yaml.set(prefix + ".active", board.active());
        }

        try {
            yaml.save(boardsFile);
        } catch (IOException exception) {
            logger.severe("Failed to save boards.yml: " + exception.getMessage());
        }
    }

    public void register(Board board) {
        boardsById.put(board.id(), board);
        reindex(board);
    }

    public boolean addBoard(Board board) {
        if (boardsById.containsKey(board.id())) {
            return false;
        }
        revalidateBoard(board, false);
        register(board);
        save();
        return true;
    }

    public boolean removeBoard(String id) {
        Board board = boardsById.remove(id);
        if (board == null) {
            return false;
        }
        removeIndex(board);
        save();
        return true;
    }

    public Optional<Board> getBoard(String id) {
        return Optional.ofNullable(boardsById.get(id));
    }

    public Optional<Board> getBoardAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        String key = location.getWorld().getName() + ":"
            + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
        String boardId = locationIndex.get(key);
        return boardId == null ? Optional.empty() : Optional.ofNullable(boardsById.get(boardId));
    }

    public Optional<Board> getNearestActiveBoard(Location location, double maxRadius) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        Board nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Board board : boardsById.values()) {
            if (!board.active() || !board.isValidStructure()) {
                continue;
            }
            if (!board.worldName().equals(location.getWorld().getName())) {
                continue;
            }
            double dx = board.blockX() - location.getBlockX();
            double dy = board.blockY() - location.getBlockY();
            double dz = board.blockZ() - location.getBlockZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance <= maxRadius && distance < nearestDistance) {
                nearestDistance = distance;
                nearest = board;
            }
        }
        return Optional.ofNullable(nearest);
    }

    public Collection<Board> allBoards() {
        return Collections.unmodifiableCollection(boardsById.values());
    }

    public List<Board> activeBoards() {
        List<Board> result = new ArrayList<>();
        for (Board board : boardsById.values()) {
            if (board.active() && board.isValidStructure()) {
                result.add(board);
            }
        }
        return result;
    }

    public List<Board> getRegionalPeers(Board board) {
        List<Board> peers = new ArrayList<>();
        for (Board other : boardsById.values()) {
            if (other.id().equals(board.id())) {
                continue;
            }
            if (!other.active() || !other.isValidStructure()) {
                continue;
            }
            boolean tagMatch = board.hasRegionTag() && board.regionTag().equals(other.regionTag());
            boolean explicitLink = board.linkedBoardIds().contains(other.id());
            if (tagMatch || explicitLink) {
                peers.add(other);
            }
        }
        return peers;
    }

    public BoardValidationResult validateBoard(Board board) {
        return revalidateBoard(board, true);
    }

    public BoardValidationResult revalidateBoard(Board board, boolean persist) {
        BoardValidationResult result = validator.validate(board);
        applyValidation(board, result);
        reindex(board);
        if (persist) {
            save();
        }
        return result;
    }

    public BoardStructureValidator validator() {
        return validator;
    }

    public PluginSettings settings() {
        return settings;
    }

    private void applyValidation(Board board, BoardValidationResult result) {
        if (result == null) {
            return;
        }
        if (result.bellLocation() != null) {
            board.setBell(
                result.bellLocation().getBlockX(),
                result.bellLocation().getBlockY(),
                result.bellLocation().getBlockZ()
            );
        } else {
            board.setBell(null, null, null);
        }
        board.setBellDistance(result.bellDistance());
        board.setBellDistanceValid(result.bellLocation() != null && result.bellDistance() <= settings.boardBellRadius());
        board.setValidationErrors(result.errors());
        board.setFacing(result.facing());
        board.setColumnSide(result.columnSide());
        board.setLocalOfferSlots(PHYSICAL_TASK_SIGN_SLOTS);
        if (board.nextRefreshAtEpochSeconds() <= 0L) {
            board.setNextRefreshAtEpochSeconds((System.currentTimeMillis() / 1000L) + board.refreshIntervalSeconds());
        }
    }

    private void reindex(Board board) {
        removeIndex(board);
        BoardLayout layout = BoardLayout.fromBoard(board, server);
        if (layout == null) {
            locationIndex.put(board.locationKey(), board.id());
            return;
        }
        for (String key : layout.interactionKeys()) {
            locationIndex.put(key, board.id());
        }
    }

    private void removeIndex(Board board) {
        locationIndex.entrySet().removeIf(entry -> entry.getValue().equals(board.id()));
    }

    private Board parseBoard(String id, ConfigurationSection section) {
        String world = section.getString("world");
        if (world == null || world.isBlank()) {
            logger.warning("Board '" + id + "' is missing world and was skipped.");
            return null;
        }

        int anchorX = section.getInt("anchor.x", section.getInt("x", 0));
        int anchorY = section.getInt("anchor.y", section.getInt("y", 64));
        int anchorZ = section.getInt("anchor.z", section.getInt("z", 0));

        Map<String, Double> categoryWeights = new HashMap<>();
        ConfigurationSection weightsSection = section.getConfigurationSection("category-weights");
        if (weightsSection != null) {
            for (String key : weightsSection.getKeys(false)) {
                try {
                    ContractType.valueOf(key.toUpperCase(java.util.Locale.ROOT));
                    categoryWeights.put(key.toUpperCase(java.util.Locale.ROOT), weightsSection.getDouble(key, 1.0D));
                } catch (IllegalArgumentException ignored) {
                    logger.warning("Board '" + id + "' contains unknown category weight '" + key + "'.");
                }
            }
        }

        Integer bellX = section.contains("bell.x") ? section.getInt("bell.x") : null;
        Integer bellY = section.contains("bell.y") ? section.getInt("bell.y") : null;
        Integer bellZ = section.contains("bell.z") ? section.getInt("bell.z") : null;

        return new Board(
            id,
            section.getString("name", id),
            BoardType.fromString(section.getString("type", "CUSTOM")),
            section.getString("custom-type-name"),
            world,
            anchorX,
            anchorY,
            anchorZ,
            parseFacing(section.getString("facing", "NORTH")),
            BoardColumnSide.fromString(section.getString("column-side", "RIGHT")),
            bellX,
            bellY,
            bellZ,
            section.getDouble("bell-distance", -1.0D),
            section.getBoolean("bell-distance-valid", false),
            section.getStringList("validation-errors"),
            section.getString("region-tag", ""),
            section.getStringList("linked-boards"),
            section.getStringList("contract-pools"),
            categoryWeights,
            section.getDouble("difficulty-modifier", 1.0D),
            section.getDouble("reward-modifier", section.getDouble("economy-modifier", 1.0D)),
            section.getDouble("reputation-modifier", 1.0D),
            section.getInt("local-offer-slots", settings == null ? 3 : settings.defaultLocalOfferSlots()),
            section.getString("flavor-text", ""),
            section.getLong("refresh-interval-seconds", settings == null ? 300L : settings.boardRefreshIntervalSeconds()),
            section.getLong("next-refresh-at", 0L),
            section.getBoolean("active", true),
            System.currentTimeMillis() / 1000L
        );
    }

    private BlockFace parseFacing(String raw) {
        try {
            BlockFace face = BlockFace.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
            return face == BlockFace.UP || face == BlockFace.DOWN ? BlockFace.NORTH : face;
        } catch (IllegalArgumentException exception) {
            return BlockFace.NORTH;
        }
    }
}
