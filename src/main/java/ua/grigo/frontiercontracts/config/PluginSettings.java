package ua.grigo.frontiercontracts.config;

import org.bukkit.configuration.file.FileConfiguration;

public record PluginSettings(
    String databaseFile,
    int defaultLocalOfferSlots,
    int regionalOfferSlots,
    int globalOfferSlots,
    long localOfferLifetimeMinutes,
    long regionalOfferLifetimeMinutes,
    long globalOfferLifetimeMinutes,
    long cleanupIntervalSeconds,
    double defaultNoDeathMoneyMultiplier,
    long boardRefreshIntervalSeconds,
    double boardBellRadius,
    double boardVillagePoiRadius,
    String boardMenuTitle,
    String detailMenuTitle,
    String activeMenuTitle,
    boolean warnMissingEconomyProvider,
    boolean blockCreativeSubmit,
    boolean logSuspiciousCompletions,
    boolean reputationEnabled,
    int reputationOnComplete,
    int reputationOnFail,
    int reputationOnAbandon,
    double nearbyBoardRadius
) {
    public static PluginSettings from(FileConfiguration config) {
        return new PluginSettings(
            config.getString("database.file", "frontier-contracts.db"),
            Math.max(1, config.getInt("offers.local-slots", 5)),
            Math.max(1, config.getInt("offers.regional-slots", 3)),
            Math.max(1, config.getInt("offers.global-slots", 6)),
            Math.max(1L, config.getLong("offers.local-lifetime-minutes", 120L)),
            Math.max(1L, config.getLong("offers.regional-lifetime-minutes", 180L)),
            Math.max(1L, config.getLong("offers.global-lifetime-minutes", 240L)),
            Math.max(5L, config.getLong("contracts.cleanup-interval-seconds", 60L)),
            Math.max(1.0D, config.getDouble("contracts.default-no-death-money-multiplier", 1.2D)),
            Math.max(30L, config.getLong("boards.refresh-interval-seconds", 300L)),
            Math.max(1.0D, config.getDouble("boards.bell-radius", 13.0D)),
            Math.max(1.0D, config.getDouble("boards.village-poi-radius", 32.0D)),
            config.getString("ui.board-menu-title", "&8[ &6%board_name% &8]"),
            config.getString("ui.detail-title", "&8Деталі контракту"),
            config.getString("ui.active-title", "&8Активні контракти"),
            config.getBoolean("integration.warn-missing-economy-provider", true),
            config.getBoolean("anti-abuse.block-creative-submit", true),
            config.getBoolean("anti-abuse.log-suspicious-completions", true),
            config.getBoolean("settlements.reputation-enabled", true),
            config.getInt("settlements.reputation-increase-on-complete", 10),
            config.getInt("settlements.reputation-decrease-on-fail", -5),
            config.getInt("settlements.reputation-decrease-on-abandon", -3),
            Math.max(0.0D, config.getDouble("settlements.nearby-board-radius", 256.0D))
        );
    }
}
