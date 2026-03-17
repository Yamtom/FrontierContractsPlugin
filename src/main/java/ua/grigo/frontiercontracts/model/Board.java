package ua.grigo.frontiercontracts.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

/**
 * Represents one physical contract board registered in a world.
 */
public final class Board {

    private final String id;
    private String name;
    private BoardType type;
    private String customTypeName;

    /** World name (stored as string so boards can survive across world reloads). */
    private final String worldName;
    private final int anchorX;
    private final int anchorY;
    private final int anchorZ;
    private BlockFace facing;
    private BoardColumnSide columnSide;
    private Integer bellX;
    private Integer bellY;
    private Integer bellZ;
    private double bellDistance;
    private boolean bellDistanceValid;
    private List<String> validationErrors;

    /**
     * Region tag used for REGIONAL contract grouping.
     * All boards sharing the same tag draw from the same REGIONAL pool.
     */
    private String regionTag;

    /**
     * Optional explicit routes to other board IDs that this settlement trades with.
     * Used for transport-style delivery in v0.2+ but parsed and stored from v0.1.
     */
    private List<String> linkedBoardIds;

    /** Template pool keys that this board is allowed to draw LOCAL contracts from. */
    private List<String> contractPools;

    /**
     * Per-category weight multipliers applied on top of template weights during local offer generation.
     * Key: category name matching ContractType.name(), value: multiplier (1.0 = neutral).
     */
    private Map<String, Double> categoryWeights;

    /** Difficulty multiplier applied to objective amounts. */
    private double difficultyModifier;

    /** Reward multiplier applied to money rewards. */
    private double rewardModifier;

    /** Reputation multiplier applied to reputation rewards. */
    private double reputationModifier;

    /** Maximum simultaneous LOCAL active offers on this board. */
    private int localOfferSlots;

    /** Rotating flavor line displayed in the board GUI header. */
    private String flavorText;
    private long refreshIntervalSeconds;
    private long nextRefreshAtEpochSeconds;

    private boolean active;
    private final long createdAt;

    public Board(
        String id,
        String name,
        BoardType type,
        String customTypeName,
        String worldName,
        int anchorX,
        int anchorY,
        int anchorZ,
        BlockFace facing,
        BoardColumnSide columnSide,
        Integer bellX,
        Integer bellY,
        Integer bellZ,
        double bellDistance,
        boolean bellDistanceValid,
        List<String> validationErrors,
        String regionTag,
        List<String> linkedBoardIds,
        List<String> contractPools,
        Map<String, Double> categoryWeights,
        double difficultyModifier,
        double rewardModifier,
        double reputationModifier,
        int localOfferSlots,
        String flavorText,
        long refreshIntervalSeconds,
        long nextRefreshAtEpochSeconds,
        boolean active,
        long createdAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.type = type == null ? BoardType.CUSTOM : type;
        this.customTypeName = customTypeName;
        this.worldName = Objects.requireNonNull(worldName);
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.facing = facing == null ? BlockFace.NORTH : facing;
        this.columnSide = columnSide == null ? BoardColumnSide.RIGHT : columnSide;
        this.bellX = bellX;
        this.bellY = bellY;
        this.bellZ = bellZ;
        this.bellDistance = bellDistance;
        this.bellDistanceValid = bellDistanceValid;
        this.validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
        this.regionTag = regionTag == null ? "" : regionTag;
        this.linkedBoardIds = linkedBoardIds == null ? List.of() : List.copyOf(linkedBoardIds);
        this.contractPools = contractPools == null ? List.of() : List.copyOf(contractPools);
        this.categoryWeights = categoryWeights == null ? Map.of() : Map.copyOf(categoryWeights);
        this.difficultyModifier = Math.max(0.1D, difficultyModifier);
        this.rewardModifier = Math.max(0.1D, rewardModifier);
        this.reputationModifier = Math.max(0.1D, reputationModifier);
        this.localOfferSlots = Math.max(1, localOfferSlots);
        this.flavorText = flavorText == null ? "" : flavorText;
        this.refreshIntervalSeconds = Math.max(30L, refreshIntervalSeconds);
        this.nextRefreshAtEpochSeconds = Math.max(0L, nextRefreshAtEpochSeconds);
        this.active = active;
        this.createdAt = createdAt;
    }

    public String id() { return id; }

    public String name() { return name; }
    public void setName(String name) { this.name = Objects.requireNonNull(name); }

    public BoardType type() { return type; }
    public void setType(BoardType type) { this.type = type == null ? BoardType.CUSTOM : type; }

    public String customTypeName() { return customTypeName; }
    public void setCustomTypeName(String name) { this.customTypeName = name; }

    public String displayTypeName() {
        if (type == BoardType.CUSTOM && customTypeName != null && !customTypeName.isBlank()) {
            return customTypeName;
        }
        return type.displayName();
    }

    public String worldName() { return worldName; }
    public int blockX() { return anchorX; }
    public int blockY() { return anchorY; }
    public int blockZ() { return anchorZ; }
    public int anchorX() { return anchorX; }
    public int anchorY() { return anchorY; }
    public int anchorZ() { return anchorZ; }
    public BlockFace facing() { return facing; }
    public void setFacing(BlockFace facing) { this.facing = facing == null ? BlockFace.NORTH : facing; }
    public BoardColumnSide columnSide() { return columnSide; }
    public void setColumnSide(BoardColumnSide columnSide) {
        this.columnSide = columnSide == null ? BoardColumnSide.RIGHT : columnSide;
    }

    /** Returns a Location for the support-post anchor, or null if the world is not loaded. */
    public Location location(org.bukkit.Server server) {
        World world = server.getWorld(worldName);
        return world == null ? null : new Location(world, anchorX, anchorY, anchorZ);
    }

    public Location bellLocation(org.bukkit.Server server) {
        if (bellX == null || bellY == null || bellZ == null) {
            return null;
        }
        World world = server.getWorld(worldName);
        return world == null ? null : new Location(world, bellX, bellY, bellZ);
    }

    public String locationKey() {
        return worldName + ":" + anchorX + ":" + anchorY + ":" + anchorZ;
    }

    public String regionTag() { return regionTag; }
    public void setRegionTag(String tag) { this.regionTag = tag == null ? "" : tag; }
    public boolean hasRegionTag() { return !regionTag.isBlank(); }

    public List<String> linkedBoardIds() { return linkedBoardIds; }
    public void setLinkedBoardIds(List<String> ids) {
        this.linkedBoardIds = ids == null ? List.of() : List.copyOf(ids);
    }

    public List<String> contractPools() { return contractPools; }
    public void setContractPools(List<String> pools) {
        this.contractPools = pools == null ? List.of() : List.copyOf(pools);
    }

    public Map<String, Double> categoryWeights() { return categoryWeights; }

    public double categoryWeight(ContractType type) {
        return categoryWeights.getOrDefault(type.name().toUpperCase(Locale.ROOT), 1.0D);
    }

    public double difficultyModifier() { return difficultyModifier; }
    public void setDifficultyModifier(double value) { this.difficultyModifier = Math.max(0.1D, value); }

    public double rewardModifier() { return rewardModifier; }
    public void setRewardModifier(double value) { this.rewardModifier = Math.max(0.1D, value); }

    public double economyModifier() { return rewardModifier; }
    public void setEconomyModifier(double value) { this.rewardModifier = Math.max(0.1D, value); }

    public double reputationModifier() { return reputationModifier; }
    public void setReputationModifier(double value) { this.reputationModifier = Math.max(0.1D, value); }

    public int localOfferSlots() { return localOfferSlots; }
    public void setLocalOfferSlots(int slots) { this.localOfferSlots = Math.max(1, slots); }

    public String flavorText() { return flavorText; }
    public void setFlavorText(String text) { this.flavorText = text == null ? "" : text; }
    public boolean hasFlavorText() { return !flavorText.isBlank(); }

    public long refreshIntervalSeconds() { return refreshIntervalSeconds; }
    public void setRefreshIntervalSeconds(long refreshIntervalSeconds) {
        this.refreshIntervalSeconds = Math.max(30L, refreshIntervalSeconds);
    }

    public long nextRefreshAtEpochSeconds() { return nextRefreshAtEpochSeconds; }
    public void setNextRefreshAtEpochSeconds(long nextRefreshAtEpochSeconds) {
        this.nextRefreshAtEpochSeconds = Math.max(0L, nextRefreshAtEpochSeconds);
    }

    public Integer bellX() { return bellX; }
    public Integer bellY() { return bellY; }
    public Integer bellZ() { return bellZ; }
    public void setBell(Integer bellX, Integer bellY, Integer bellZ) {
        this.bellX = bellX;
        this.bellY = bellY;
        this.bellZ = bellZ;
    }

    public double bellDistance() { return bellDistance; }
    public void setBellDistance(double bellDistance) { this.bellDistance = bellDistance; }

    public boolean bellDistanceValid() { return bellDistanceValid; }
    public void setBellDistanceValid(boolean bellDistanceValid) { this.bellDistanceValid = bellDistanceValid; }

    public List<String> validationErrors() { return validationErrors; }
    public void setValidationErrors(List<String> validationErrors) {
        this.validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
    }

    public boolean isValidStructure() {
        return bellDistanceValid && validationErrors.isEmpty();
    }

    public boolean active() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public long createdAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        return o instanceof Board other && id.equals(other.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }
}
