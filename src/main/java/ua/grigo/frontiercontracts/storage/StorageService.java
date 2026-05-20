package ua.grigo.frontiercontracts.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import ua.grigo.frontiercontracts.model.BonusConfig;
import ua.grigo.frontiercontracts.model.ConstructionMetadata;
import ua.grigo.frontiercontracts.model.ContractOffer;
import ua.grigo.frontiercontracts.model.ContractRank;
import ua.grigo.frontiercontracts.model.ContractRequirement;
import ua.grigo.frontiercontracts.model.ContractScope;
import ua.grigo.frontiercontracts.model.ContractType;
import ua.grigo.frontiercontracts.model.PlayerContract;
import ua.grigo.frontiercontracts.model.PlayerContractStatus;
import ua.grigo.frontiercontracts.model.PlayerStats;
import ua.grigo.frontiercontracts.model.RewardBundle;
import ua.grigo.frontiercontracts.model.RewardItem;
import ua.grigo.frontiercontracts.model.SettlementProgress;
import ua.grigo.frontiercontracts.model.SettlementReputation;
import ua.grigo.frontiercontracts.util.ContractDataCodec;
import ua.grigo.frontiercontracts.util.RewardBundleCodec;

public final class StorageService {
    private final File databaseFile;
    private Connection connection;

    public StorageService(File dataFolder, String databaseFileName) {
        this.databaseFile = new File(dataFolder, databaseFileName);
    }

    public void initialize() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS generated_offers (
                    id TEXT PRIMARY KEY,
                    template_key TEXT NOT NULL,
                    board_id TEXT NULL,
                    scope TEXT NOT NULL,
                    owner_uuid TEXT NULL,
                    source_board_id TEXT NULL,
                    target_board_id TEXT NULL,
                    type TEXT NOT NULL,
                    rank TEXT NULL,
                    difficulty TEXT NOT NULL,
                    objective_key TEXT NOT NULL,
                    objective_amount INTEGER NOT NULL,
                    required_material TEXT NULL,
                    required_amount INTEGER NULL,
                    delivered_amount INTEGER NOT NULL DEFAULT 0,
                    requirements_blob TEXT NULL,
                    metadata_blob TEXT NULL,
                    site_blob TEXT NULL,
                    partial_delivery_allowed INTEGER NOT NULL DEFAULT 1,
                    public_offer INTEGER NOT NULL DEFAULT 1,
                    contract_duration_seconds INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    offer_expires_at INTEGER NOT NULL,
                    icon_material TEXT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL,
                    reward_money REAL NOT NULL,
                    reward_reputation INTEGER NOT NULL,
                    reward_item_material TEXT NOT NULL,
                    reward_item_amount INTEGER NOT NULL,
                    reward_blob TEXT NULL,
                    bonus_reward_blob TEXT NULL,
                    bonus_no_death_multiplier REAL NOT NULL,
                    special_offer INTEGER NOT NULL DEFAULT 0,
                    active INTEGER NOT NULL
                )
                """);

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_contracts (
                    id TEXT PRIMARY KEY,
                    offer_id TEXT NOT NULL,
                    board_id TEXT NULL,
                    player_uuid TEXT NOT NULL,
                    progress INTEGER NOT NULL,
                    accepted_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL,
                    deathless INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    completed_at INTEGER NULL
                )
                """);

            statement.executeUpdate("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_player_contract_offer
                ON player_contracts (offer_id, player_uuid)
                """);

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_stats (
                    player_uuid TEXT PRIMARY KEY,
                    reputation INTEGER NOT NULL,
                    completed_contracts INTEGER NOT NULL,
                    failed_contracts INTEGER NOT NULL,
                    contract_xp INTEGER NOT NULL DEFAULT 0
                )
                """);

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS settlement_reputation (
                    board_id TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    reputation INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY (board_id, player_uuid)
                )
                """);

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS settlement_progress (
                    board_id TEXT PRIMARY KEY,
                    trust INTEGER NOT NULL DEFAULT 0,
                    routine_completed INTEGER NOT NULL DEFAULT 0,
                    project_completed INTEGER NOT NULL DEFAULT 0,
                    project_cooldown_until INTEGER NOT NULL DEFAULT 0,
                    ranked_without_high INTEGER NOT NULL DEFAULT 0,
                    ranked_without_elite INTEGER NOT NULL DEFAULT 0,
                    recent_variants_blob TEXT NULL,
                    updated_at INTEGER NOT NULL DEFAULT 0
                )
                """);

            migrateAddColumnIfMissing(statement, "generated_offers", "board_id", "TEXT NULL");
            migrateAddColumnIfMissing(statement, "generated_offers", "source_board_id", "TEXT NULL");
            migrateAddColumnIfMissing(statement, "generated_offers", "target_board_id", "TEXT NULL");
            migrateAddColumnIfMissing(statement, "generated_offers", "rank", "TEXT NULL");
            migrateAddColumnIfMissing(statement, "generated_offers", "required_material", "TEXT NULL");
            migrateAddColumnIfMissing(statement, "generated_offers", "required_amount", "INTEGER NULL");
            migrateAddColumnIfMissing(statement, "generated_offers", "delivered_amount", "INTEGER NOT NULL DEFAULT 0");
            migrateAddColumnIfMissing(statement, "generated_offers", "requirements_blob", "TEXT NULL");
            migrateAddColumnIfMissing(statement, "generated_offers", "metadata_blob", "TEXT NULL");
            migrateAddColumnIfMissing(statement, "generated_offers", "site_blob", "TEXT NULL");
            migrateAddColumnIfMissing(statement, "generated_offers", "partial_delivery_allowed", "INTEGER NOT NULL DEFAULT 1");
            migrateAddColumnIfMissing(statement, "generated_offers", "public_offer", "INTEGER NOT NULL DEFAULT 1");
            migrateAddColumnIfMissing(statement, "generated_offers", "reward_blob", "TEXT NULL");
            migrateAddColumnIfMissing(statement, "generated_offers", "bonus_reward_blob", "TEXT NULL");
            migrateAddColumnIfMissing(statement, "generated_offers", "special_offer", "INTEGER NOT NULL DEFAULT 0");
            migrateAddColumnIfMissing(statement, "player_contracts", "board_id", "TEXT NULL");
            migrateAddColumnIfMissing(statement, "player_stats", "contract_xp", "INTEGER NOT NULL DEFAULT 0");
            migrateAddColumnIfMissing(statement, "generated_offers", "max_players", "INTEGER NOT NULL DEFAULT 1");
            migrateAddColumnIfMissing(statement, "generated_offers", "active_players", "INTEGER NOT NULL DEFAULT 0");
            migrateAddColumnIfMissing(statement, "player_contracts", "type", "TEXT NOT NULL DEFAULT 'DELIVERY'");
            migrateAddColumnIfMissing(statement, "player_contracts", "requirements_blob", "TEXT NULL");
        }
    }

    private void migrateAddColumnIfMissing(Statement statement, String table, String column, String definition) {
        try {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (SQLException ignored) {
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }

    public Map<String, ContractOffer> loadOffers() throws SQLException {
        Map<String, ContractOffer> offers = new HashMap<>();
        String sql = """
            SELECT DISTINCT go.*
            FROM generated_offers go
            LEFT JOIN player_contracts pc
                ON go.id = pc.offer_id
                AND pc.status IN ('ACTIVE', 'READY_TO_CLAIM')
            WHERE go.active = 1 OR pc.id IS NOT NULL
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                List<ContractRequirement> requirements = decodeRequirements(resultSet);
                if (requirements.isEmpty()) {
                    requirements = List.of(new ContractRequirement(
                        parseMaterial(firstNonBlank(resultSet, "required_material", "objective_key"), Material.PAPER),
                        getInt(resultSet, "required_amount", resultSet.getInt("objective_amount")),
                        getInt(resultSet, "delivered_amount", 0)
                    ));
                }
                ContractOffer offer = new ContractOffer(
                    resultSet.getString("id"),
                    resultSet.getString("template_key"),
                    resultSet.getString("board_id"),
                    ContractScope.valueOf(resultSet.getString("scope")),
                    resultSet.getString("source_board_id"),
                    resultSet.getString("target_board_id"),
                    parseUuid(resultSet.getString("owner_uuid")),
                    ContractType.valueOf(resultSet.getString("type")),
                    parseRank(resultSet),
                    resultSet.getString("difficulty"),
                    requirements,
                    decodeMetadata(resultSet),
                    ContractDataCodec.decodeSite(getString(resultSet, "site_blob")),
                    getBoolean(resultSet, "partial_delivery_allowed", true),
                    getBoolean(resultSet, "public_offer", true),
                    resultSet.getLong("contract_duration_seconds"),
                    resultSet.getLong("created_at"),
                    resultSet.getLong("offer_expires_at"),
                    parseMaterial(resultSet.getString("icon_material"), Material.PAPER),
                    resultSet.getString("title"),
                    resultSet.getString("description"),
                    decodeRewardBundle(resultSet),
                    RewardBundleCodec.decode(getString(resultSet, "bonus_reward_blob")),
                    new BonusConfig(resultSet.getDouble("bonus_no_death_multiplier")),
                    getInt(resultSet, "max_players", 1),
                    getInt(resultSet, "active_players", 0),
                    getBoolean(resultSet, "special_offer", false),
                    resultSet.getInt("active") == 1
                );
                offers.put(offer.id(), offer);
            }
        }
        return offers;
    }

    public void saveOffer(ContractOffer offer) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT OR REPLACE INTO generated_offers (
                id, template_key, board_id, scope, owner_uuid, source_board_id, target_board_id,
                type, rank, difficulty, objective_key, objective_amount, required_material, required_amount,
                delivered_amount, requirements_blob, metadata_blob, site_blob, partial_delivery_allowed, public_offer,
                contract_duration_seconds, created_at, offer_expires_at,
                icon_material, title, description,
                reward_money, reward_reputation, reward_item_material, reward_item_amount,
                reward_blob, bonus_reward_blob, bonus_no_death_multiplier, special_offer, active,
                max_players, active_players
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """)) {
            RewardItem legacyItem = offer.rewards().itemRewards().isEmpty()
                ? new RewardItem(Material.AIR, 0)
                : offer.rewards().itemRewards().getFirst();

            statement.setString(1, offer.id());
            statement.setString(2, offer.templateKey());
            statement.setString(3, offer.boardId());
            statement.setString(4, offer.scope().name());
            statement.setString(5, offer.ownerUuid() == null ? null : offer.ownerUuid().toString());
            statement.setString(6, offer.sourceBoardId());
            statement.setString(7, offer.targetBoardId());
            statement.setString(8, offer.type().name());
            statement.setString(9, offer.rank() == null ? null : offer.rank().name());
            statement.setString(10, offer.difficulty());
            statement.setString(11, offer.objectiveKey());
            statement.setInt(12, offer.objectiveAmount());
            statement.setString(13, offer.requiredMaterial().name());
            statement.setInt(14, offer.requiredAmount());
            statement.setInt(15, offer.deliveredAmount());
            statement.setString(16, ContractDataCodec.encodeRequirements(offer.requirements()));
            statement.setString(17, ContractDataCodec.encodeMetadata(offer.metadata()));
            statement.setString(18, ContractDataCodec.encodeSite(offer.site()));
            statement.setInt(19, offer.partialDeliveryAllowed() ? 1 : 0);
            statement.setInt(20, offer.publicOffer() ? 1 : 0);
            statement.setLong(21, offer.contractDurationSeconds());
            statement.setLong(22, offer.createdAtEpochSeconds());
            statement.setLong(23, offer.offerExpiresAtEpochSeconds());
            statement.setString(24, offer.iconMaterial().name());
            statement.setString(25, offer.title());
            statement.setString(26, offer.description());
            statement.setDouble(27, offer.rewards().money());
            statement.setInt(28, offer.rewards().reputation());
            statement.setString(29, legacyItem.material().name());
            statement.setInt(30, legacyItem.amount());
            statement.setString(31, RewardBundleCodec.encode(offer.rewards()));
            statement.setString(32, RewardBundleCodec.encode(offer.bonusReward()));
            statement.setDouble(33, offer.bonusConfig().noDeathMoneyMultiplier());
            statement.setInt(34, offer.specialOffer() ? 1 : 0);
            statement.setInt(35, offer.active() ? 1 : 0);
            statement.setInt(36, offer.maxPlayers());
            statement.setInt(37, offer.activePlayers());
            statement.executeUpdate();
        }
    }

    public void deactivatePendingOffers() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE generated_offers SET active = 0
            WHERE active = 1
            AND id NOT IN (
                SELECT DISTINCT offer_id FROM player_contracts
                WHERE status IN ('ACTIVE', 'READY_TO_CLAIM')
            )
            """)) {
            statement.executeUpdate();
        }
    }

    public Map<UUID, Map<String, PlayerContract>> loadOpenContracts() throws SQLException {
        Map<UUID, Map<String, PlayerContract>> contracts = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT * FROM player_contracts WHERE status IN ('ACTIVE', 'READY_TO_CLAIM')");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                UUID playerUuid = UUID.fromString(resultSet.getString("player_uuid"));
                PlayerContract contract = new PlayerContract(
                    resultSet.getString("id"),
                    resultSet.getString("offer_id"),
                    resultSet.getString("board_id"),
                    parseContractType(getString(resultSet, "type")),
                    playerUuid,
                    resultSet.getInt("progress"),
                    ContractDataCodec.decodeRequirements(getString(resultSet, "requirements_blob")),
                    resultSet.getLong("accepted_at"),
                    resultSet.getLong("expires_at"),
                    resultSet.getInt("deathless") == 1,
                    PlayerContractStatus.valueOf(resultSet.getString("status")),
                    resultSet.getObject("completed_at") == null ? null : resultSet.getLong("completed_at")
                );
                contracts.computeIfAbsent(playerUuid, ignored -> new HashMap<>()).put(contract.offerId(), contract);
            }
        }
        return contracts;
    }

    public boolean hasPlayerAcceptedOffer(UUID playerUuid, String offerId) {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT 1 FROM player_contracts WHERE player_uuid = ? AND offer_id = ? LIMIT 1")) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, offerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            return false;
        }
    }

    public void savePlayerContract(PlayerContract contract) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT OR REPLACE INTO player_contracts (
                id, offer_id, board_id, player_uuid, progress,
                accepted_at, expires_at, deathless, status, completed_at,
                type, requirements_blob
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """)) {
            statement.setString(1, contract.id());
            statement.setString(2, contract.offerId());
            statement.setString(3, contract.boardId());
            statement.setString(4, contract.playerUuid().toString());
            statement.setInt(5, contract.progress());
            statement.setLong(6, contract.acceptedAtEpochSeconds());
            statement.setLong(7, contract.expiresAtEpochSeconds());
            statement.setInt(8, contract.deathless() ? 1 : 0);
            statement.setString(9, contract.status().name());
            if (contract.completedAtEpochSeconds() == null) {
                statement.setObject(10, null);
            } else {
                statement.setLong(10, contract.completedAtEpochSeconds());
            }
            statement.setString(11, contract.type().name());
            statement.setString(12, ContractDataCodec.encodeRequirements(contract.requirements()));
            statement.executeUpdate();
        }
    }

    public Map<UUID, PlayerStats> loadStats() throws SQLException {
        Map<UUID, PlayerStats> stats = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM player_stats");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                UUID uuid = UUID.fromString(resultSet.getString("player_uuid"));
                stats.put(uuid, new PlayerStats(
                    uuid,
                    resultSet.getInt("reputation"),
                    resultSet.getInt("completed_contracts"),
                    resultSet.getInt("failed_contracts"),
                    getInt(resultSet, "contract_xp", 0)
                ));
            }
        }
        return stats;
    }

    public void saveStats(PlayerStats stats) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT OR REPLACE INTO player_stats
            (player_uuid, reputation, completed_contracts, failed_contracts, contract_xp)
            VALUES (?,?,?,?,?)
            """)) {
            statement.setString(1, stats.playerUuid().toString());
            statement.setInt(2, stats.reputation());
            statement.setInt(3, stats.completedContracts());
            statement.setInt(4, stats.failedContracts());
            statement.setInt(5, stats.contractXp());
            statement.executeUpdate();
        }
    }

    public Map<String, SettlementProgress> loadSettlementProgress() throws SQLException {
        Map<String, SettlementProgress> result = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM settlement_progress");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                SettlementProgress progress = new SettlementProgress(
                    resultSet.getString("board_id"),
                    resultSet.getInt("trust"),
                    resultSet.getInt("routine_completed"),
                    resultSet.getInt("project_completed"),
                    resultSet.getLong("project_cooldown_until"),
                    resultSet.getInt("ranked_without_high"),
                    resultSet.getInt("ranked_without_elite"),
                    ContractDataCodec.decodeStringList(getString(resultSet, "recent_variants_blob")),
                    resultSet.getLong("updated_at")
                );
                result.put(progress.boardId(), progress);
            }
        }
        return result;
    }

    public void saveSettlementProgress(SettlementProgress progress) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT OR REPLACE INTO settlement_progress
            (board_id, trust, routine_completed, project_completed, project_cooldown_until,
             ranked_without_high, ranked_without_elite, recent_variants_blob, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?)
            """)) {
            statement.setString(1, progress.boardId());
            statement.setInt(2, progress.trust());
            statement.setInt(3, progress.routineCompleted());
            statement.setInt(4, progress.projectCompleted());
            statement.setLong(5, progress.projectCooldownUntil());
            statement.setInt(6, progress.rankedWithoutHigh());
            statement.setInt(7, progress.rankedWithoutElite());
            statement.setString(8, ContractDataCodec.encodeStringList(progress.recentVariants()));
            statement.setLong(9, progress.updatedAt());
            statement.executeUpdate();
        }
    }

    public Map<String, SettlementReputation> loadSettlementReputation() throws SQLException {
        Map<String, SettlementReputation> result = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM settlement_reputation");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String boardId = resultSet.getString("board_id");
                UUID playerUuid = UUID.fromString(resultSet.getString("player_uuid"));
                SettlementReputation reputation = new SettlementReputation(
                    boardId,
                    playerUuid,
                    resultSet.getInt("reputation"),
                    resultSet.getLong("updated_at")
                );
                result.put(boardId + ":" + playerUuid, reputation);
            }
        }
        return result;
    }

    public void saveSettlementReputation(SettlementReputation reputation) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT OR REPLACE INTO settlement_reputation
            (board_id, player_uuid, reputation, updated_at)
            VALUES (?,?,?,?)
            """)) {
            statement.setString(1, reputation.boardId());
            statement.setString(2, reputation.playerUuid().toString());
            statement.setInt(3, reputation.reputation());
            statement.setLong(4, reputation.updatedAt());
            statement.executeUpdate();
        }
    }

    private RewardBundle decodeRewardBundle(ResultSet resultSet) throws SQLException {
        String encoded = getString(resultSet, "reward_blob");
        if (encoded != null && !encoded.isBlank()) {
            return RewardBundleCodec.decode(encoded);
        }
        return new RewardBundle(
            resultSet.getDouble("reward_money"),
            resultSet.getInt("reward_reputation"),
            List.of(new RewardItem(
                parseMaterial(resultSet.getString("reward_item_material"), Material.AIR),
                resultSet.getInt("reward_item_amount")
            )),
            List.of()
        );
    }

    private List<ContractRequirement> decodeRequirements(ResultSet resultSet) throws SQLException {
        String encoded = getString(resultSet, "requirements_blob");
        return ContractDataCodec.decodeRequirements(encoded);
    }

    private ConstructionMetadata decodeMetadata(ResultSet resultSet) throws SQLException {
        return ContractDataCodec.decodeMetadata(getString(resultSet, "metadata_blob"));
    }

    private String firstNonBlank(ResultSet resultSet, String primaryColumn, String fallbackColumn) throws SQLException {
        String primary = getString(resultSet, primaryColumn);
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return resultSet.getString(fallbackColumn);
    }

    private boolean getBoolean(ResultSet resultSet, String column, boolean fallback) throws SQLException {
        if (!hasColumn(resultSet, column)) {
            return fallback;
        }
        return resultSet.getInt(column) == 1;
    }

    private int getInt(ResultSet resultSet, String column, int fallback) throws SQLException {
        if (!hasColumn(resultSet, column)) {
            return fallback;
        }
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? fallback : value;
    }

    private String getString(ResultSet resultSet, String column) throws SQLException {
        if (!hasColumn(resultSet, column)) {
            return null;
        }
        return resultSet.getString(column);
    }

    private boolean hasColumn(ResultSet resultSet, String column) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        for (int index = 1; index <= metaData.getColumnCount(); index++) {
            if (column.equalsIgnoreCase(metaData.getColumnName(index))) {
                return true;
            }
        }
        return false;
    }

    private UUID parseUuid(String raw) {
        return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
    }

    private ContractRank parseRank(ResultSet resultSet) throws SQLException {
        String raw = getString(resultSet, "rank");
        ContractRank parsed = ContractRank.parseNullable(raw);
        if (parsed != null) {
            return parsed;
        }
        ContractType type = ContractType.valueOf(resultSet.getString("type"));
        return type == ContractType.CONSTRUCTION ? null : ContractRank.C;
    }

    private Material parseMaterial(String raw, Material fallback) {
        try {
            return raw == null ? fallback : Material.valueOf(raw);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private ContractType parseContractType(String raw) {
        try {
            return raw == null ? ContractType.DELIVERY : ContractType.valueOf(raw);
        } catch (IllegalArgumentException exception) {
            return ContractType.DELIVERY;
        }
    }
}
