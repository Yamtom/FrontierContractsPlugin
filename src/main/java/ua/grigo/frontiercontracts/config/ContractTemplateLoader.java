package ua.grigo.frontiercontracts.config;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import ua.grigo.frontiercontracts.model.ConstructionMetadata;
import ua.grigo.frontiercontracts.model.ConstructionRules;
import ua.grigo.frontiercontracts.model.ContractCatalog;
import ua.grigo.frontiercontracts.model.ContractRank;
import ua.grigo.frontiercontracts.model.ContractRequirement;
import ua.grigo.frontiercontracts.model.ContractScope;
import ua.grigo.frontiercontracts.model.ContractTemplate;
import ua.grigo.frontiercontracts.model.ContractType;
import ua.grigo.frontiercontracts.model.RoadSettings;
import ua.grigo.frontiercontracts.model.RewardBundle;
import ua.grigo.frontiercontracts.model.RewardItem;
import ua.grigo.frontiercontracts.util.ContractDataCodec;

public final class ContractTemplateLoader {
    private static final Logger LOGGER = Logger.getLogger(ContractTemplateLoader.class.getName());

    private ContractTemplateLoader() {}

    public static ContractCatalog load(FileConfiguration contractsConfig, PluginSettings settings) {
        Map<ContractRank, String> rankDescriptions = parseRankScale(contractsConfig.getConfigurationSection("rank-scale"));
        RoadSettings roadSettings = parseRoadSettings(contractsConfig.getConfigurationSection("infrastructure-settings.roads"));
        ConstructionRules constructionRules = parseConstructionRules(contractsConfig.getConfigurationSection("construction-rules"));
        List<ContractTemplate> templates = new ArrayList<>();
        ConfigurationSection root = contractsConfig.getConfigurationSection("templates");
        if (root == null) {
            return new ContractCatalog(rankDescriptions, roadSettings, constructionRules, templates);
        }

        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null || !sec.getBoolean("enabled", true)) {
                continue;
            }

            ContractType type = parseType(sec.getString("type", "DELIVERY"));
            if (type == null || !type.isImplementedInV01()) {
                continue;
            }

            ContractScope scope = parseScope(sec.getString("scope", "LOCAL"));
            if (scope == null) {
                continue;
            }

            List<ContractRequirement> requirements = parseRequirements(sec);
            if (requirements.isEmpty()) {
                LOGGER.warning("Skipping template '" + key + "' because it has no valid requirements.");
                continue;
            }

            ConstructionMetadata metadata = parseMetadata(sec.getConfigurationSection("metadata"));
            if (!validateTemplate(key, type, metadata, requirements, roadSettings, constructionRules)) {
                continue;
            }

            Material icon = parseMaterial(sec.getString("icon"), requirements.getFirst().material());
            int durationMinutes = parseDurationMinutes(sec, scope, settings);
            boolean partialDeliveryAllowed = sec.getBoolean("partial-delivery", true);
            boolean publicOffer = sec.getBoolean("public", type == ContractType.CONSTRUCTION
                ? constructionRules.publicConstructionProjects()
                : true);

            List<String> description = parseDescription(sec);
            RewardBundle reward = parseRewardBundle(
                sec.getConfigurationSection("reward"),
                sec.getConfigurationSection("rewards")
            );
            RewardBundle bonusReward = parseRewardBundle(sec.getConfigurationSection("bonus-reward"), null);

            ConfigurationSection bonusSec = sec.getConfigurationSection("bonus");
            double noDeathMult = bonusSec == null
                ? settings.defaultNoDeathMoneyMultiplier()
                : Math.max(1.0D, bonusSec.getDouble("no-death-money-multiplier", settings.defaultNoDeathMoneyMultiplier()));

            templates.add(new ContractTemplate(
                key,
                scope,
                type,
                ContractRank.fromString(sec.getString("rank", "C")),
                sec.getString("difficulty", "SUPPLY"),
                Math.max(1, sec.getInt("weight", 1)),
                icon,
                sec.getString("title", key),
                description,
                requirements,
                metadata,
                durationMinutes,
                partialDeliveryAllowed,
                publicOffer,
                reward,
                bonusReward,
                new ua.grigo.frontiercontracts.model.BonusConfig(noDeathMult),
                sec.getStringList("pool-tags")
            ));
        }
        return new ContractCatalog(rankDescriptions, roadSettings, constructionRules, templates);
    }

    private static ContractType parseType(String raw) {
        try {
            return ContractType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static ContractScope parseScope(String raw) {
        try {
            return ContractScope.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static List<ContractRequirement> parseRequirements(ConfigurationSection sec) {
        List<ContractRequirement> requirements = new ArrayList<>();
        if (sec.isList("requirements")) {
            for (Map<?, ?> rawEntry : sec.getMapList("requirements")) {
                Material material = parseMaterial(String.valueOf(rawEntry.get("material")), Material.AIR);
                int amount = parseInt(rawEntry.get("amount"));
                if (material != Material.AIR && amount > 0) {
                    requirements.add(new ContractRequirement(material, amount));
                }
            }
        }

        if (!requirements.isEmpty()) {
            return requirements;
        }

        Material legacyMaterial = parseMaterial(
            firstNonBlank(sec.getString("material"), sec.getString("objective.material")),
            Material.AIR
        );
        int legacyAmount = parseLegacyAmount(sec);
        if (legacyMaterial != Material.AIR && legacyAmount > 0) {
            requirements.add(new ContractRequirement(legacyMaterial, legacyAmount));
        }
        return requirements;
    }

    private static int parseLegacyAmount(ConfigurationSection sec) {
        if (sec.contains("amount")) {
            return Math.max(1, sec.getInt("amount", 1));
        }
        if (sec.contains("objective.amount")) {
            return Math.max(1, sec.getInt("objective.amount", 1));
        }
        int min = Math.max(1, sec.getInt("objective.amount-min", 1));
        int max = Math.max(min, sec.getInt("objective.amount-max", min));
        return max;
    }

    private static ConstructionMetadata parseMetadata(ConfigurationSection sec) {
        if (sec == null) {
            return ConstructionMetadata.empty();
        }
        return new ConstructionMetadata(
            parseMaterial(sec.getString("surface"), null),
            sec.contains("road-tiles") ? Math.max(1, sec.getInt("road-tiles", 1)) : null,
            sec.getString("recommended-tool", ""),
            sec.getString("building", ""),
            sec.contains("floors") ? Math.max(1, sec.getInt("floors", 1)) : null
        );
    }

    private static int parseDurationMinutes(ConfigurationSection sec, ContractScope scope, PluginSettings settings) {
        if (sec.contains("expiration-minutes")) {
            return Math.max(1, sec.getInt("expiration-minutes", 60));
        }
        if (sec.contains("duration-minutes")) {
            ConfigurationSection durationSection = sec.getConfigurationSection("duration-minutes");
            if (durationSection != null) {
                int min = Math.max(1, durationSection.getInt("min", 15));
                int max = Math.max(min, durationSection.getInt("max", min));
                return max;
            }
        }

        return (int) switch (scope) {
            case LOCAL -> settings.localOfferLifetimeMinutes();
            case REGIONAL -> settings.regionalOfferLifetimeMinutes();
            case GLOBAL -> settings.globalOfferLifetimeMinutes();
        };
    }

    private static List<String> parseDescription(ConfigurationSection sec) {
        List<String> description = sec.getStringList("description");
        if (!description.isEmpty()) {
            return description;
        }
        String flavor = sec.getString("flavor", "");
        return flavor.isBlank() ? List.of() : List.of("&7" + flavor);
    }

    private static RewardBundle parseRewardBundle(ConfigurationSection preferred, ConfigurationSection legacy) {
        ConfigurationSection section = preferred != null ? preferred : legacy;
        if (section == null) {
            return RewardBundle.empty();
        }

        double money = firstPositive(section, "money", "money-max", "money-min");
        int reputation = firstPositiveInt(section, "reputation", "reputation-max", "reputation-min");

        List<RewardItem> items = new ArrayList<>();
        if (section.isList("items")) {
            for (Map<?, ?> rawEntry : section.getMapList("items")) {
                Object materialRaw = rawEntry.containsKey("material") ? rawEntry.get("material") : "";
                Material material = parseMaterial(String.valueOf(materialRaw), Material.AIR);
                int amount = parseInt(rawEntry.get("amount"));
                RewardItem item = new RewardItem(material, amount);
                if (item.isValid()) {
                    items.add(item);
                }
            }
        }

        ConfigurationSection singleItem = section.getConfigurationSection("item");
        if (singleItem != null) {
            RewardItem item = new RewardItem(
                parseMaterial(singleItem.getString("material"), Material.AIR),
                Math.max(0, singleItem.getInt("amount", 0))
            );
            if (item.isValid()) {
                items.add(item);
            }
        }

        ConfigurationSection legacyBonusItem = legacy == null ? null : legacy.getConfigurationSection("bonus-item");
        if (legacyBonusItem != null) {
            RewardItem item = new RewardItem(
                parseMaterial(legacyBonusItem.getString("material"), Material.AIR),
                Math.max(0, legacyBonusItem.getInt("amount", 0))
            );
            if (item.isValid()) {
                items.add(item);
            }
        }

        List<String> commands = section.getStringList("commands").stream()
            .filter(command -> command != null && !command.isBlank())
            .toList();

        return new RewardBundle(money, reputation, items, commands);
    }

    private static double firstPositive(ConfigurationSection section, String... keys) {
        for (String key : keys) {
            if (section.contains(key)) {
                return Math.max(0.0D, section.getDouble(key, 0.0D));
            }
        }
        return 0.0D;
    }

    private static int firstPositiveInt(ConfigurationSection section, String... keys) {
        for (String key : keys) {
            if (section.contains(key)) {
                return Math.max(0, section.getInt(key, 0));
            }
        }
        return 0;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Material parseMaterial(String raw, Material fallback) {
        return ContractDataCodec.parseMaterial(raw, fallback);
    }

    private static int parseInt(Object raw) {
        if (raw instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(raw)));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static Map<ContractRank, String> parseRankScale(ConfigurationSection section) {
        EnumMap<ContractRank, String> result = new EnumMap<>(ContractRank.class);
        for (ContractRank rank : ContractRank.values()) {
            result.put(rank, section == null ? rank.name() : section.getString(rank.name(), rank.name()));
        }
        return result;
    }

    private static RoadSettings parseRoadSettings(ConfigurationSection sec) {
        if (sec == null) {
            return RoadSettings.defaults();
        }
        java.util.Map<String, Integer> shovelCoverage = new java.util.LinkedHashMap<>();
        ConfigurationSection coverageSec = sec.getConfigurationSection("shovel-coverage");
        if (coverageSec != null) {
            for (String key : coverageSec.getKeys(false)) {
                if (key != null && !key.isBlank()) {
                    shovelCoverage.put(key.toUpperCase(Locale.ROOT), Math.max(0, coverageSec.getInt(key, 0)));
                }
            }
        }
        return new RoadSettings(Math.max(1, sec.getInt("dirt-per-tile", 1)), shovelCoverage);
    }

    private static ConstructionRules parseConstructionRules(ConfigurationSection sec) {
        if (sec == null) {
            return ConstructionRules.defaults();
        }
        return new ConstructionRules(
            sec.getBoolean("allow-multi-material-turn-in", true),
            sec.getBoolean("complete-only-when-all-requirements-met", true),
            sec.getBoolean("public-construction-projects", true),
            sec.getString("sign-progress-format", "{done}/{total} req"),
            Math.max(1, sec.getInt("wheat-per-floor", 3))
        );
    }

    private static boolean validateTemplate(
        String key,
        ContractType type,
        ConstructionMetadata metadata,
        List<ContractRequirement> requirements,
        RoadSettings roadSettings,
        ConstructionRules constructionRules
    ) {
        if (type != ContractType.CONSTRUCTION) {
            return true;
        }
        if (metadata.isRoadProject()) {
            int expectedDirt = metadata.roadTiles() * roadSettings.dirtPerTile();
            int actualDirt = requirementAmount(requirements, Material.DIRT);
            if (actualDirt != expectedDirt) {
                LOGGER.warning("Skipping road template '" + key + "' because DIRT requirement " + actualDirt
                    + " does not match road-tiles*dirt-per-tile (" + expectedDirt + ").");
                return false;
            }
        }
        if (metadata.isBuildingProject()) {
            int expectedWheat = metadata.floorCount() * constructionRules.wheatPerFloor();
            int actualWheat = requirementAmount(requirements, Material.WHEAT);
            if (expectedWheat > 0 && actualWheat != expectedWheat) {
                LOGGER.warning("Skipping building template '" + key + "' because WHEAT requirement " + actualWheat
                    + " does not match floors*wheat-per-floor (" + expectedWheat + ").");
                return false;
            }
        }
        return true;
    }

    private static int requirementAmount(List<ContractRequirement> requirements, Material material) {
        for (ContractRequirement requirement : requirements) {
            if (requirement.material() == material) {
                return requirement.amount();
            }
        }
        return 0;
    }
}
