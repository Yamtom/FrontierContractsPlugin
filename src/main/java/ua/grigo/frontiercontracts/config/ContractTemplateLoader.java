package ua.grigo.frontiercontracts.config;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import ua.grigo.frontiercontracts.model.AntiFrustrationSettings;
import ua.grigo.frontiercontracts.model.BonusConfig;
import ua.grigo.frontiercontracts.model.ConstructionMetadata;
import ua.grigo.frontiercontracts.model.ConstructionRules;
import ua.grigo.frontiercontracts.model.ContractCatalog;
import ua.grigo.frontiercontracts.model.ContractRank;
import ua.grigo.frontiercontracts.model.ContractRequirement;
import ua.grigo.frontiercontracts.model.ContractScope;
import ua.grigo.frontiercontracts.model.ContractTemplate;
import ua.grigo.frontiercontracts.model.ContractType;
import ua.grigo.frontiercontracts.model.ProgressionSettings;
import ua.grigo.frontiercontracts.model.ProjectGenerationSettings;
import ua.grigo.frontiercontracts.model.ProjectTrigger;
import ua.grigo.frontiercontracts.model.RoadSettings;
import ua.grigo.frontiercontracts.model.RewardBundle;
import ua.grigo.frontiercontracts.model.RewardItem;
import ua.grigo.frontiercontracts.util.ContractDataCodec;
import ua.grigo.frontiercontracts.util.TextUtil;

public final class ContractTemplateLoader {
    private static final Logger LOGGER = Logger.getLogger(ContractTemplateLoader.class.getName());
    private static final DecimalFormat MONEY_FMT = new DecimalFormat("0.##");

    private ContractTemplateLoader() {
    }

    public static ContractCatalog load(FileConfiguration contractsConfig, PluginSettings settings) {
        Map<ContractRank, String> rankDescriptions = parseRankScale(contractsConfig.getConfigurationSection("rank-scale"));
        RoadSettings roadSettings = parseRoadSettings(contractsConfig.getConfigurationSection("infrastructure-settings.roads"));
        ConstructionRules constructionRules = parseConstructionRules(contractsConfig.getConfigurationSection("construction-rules"));
        ProgressionSettings progressionSettings = parseProgressionSettings(contractsConfig);
        ProjectGenerationSettings projectGenerationSettings = parseProjectGenerationSettings(
            contractsConfig.getConfigurationSection("project-generation")
        );

        List<ContractTemplate> rankedTemplates = expandRankedTemplates(
            contractsConfig.getConfigurationSection("ranked-contracts"),
            settings
        );
        validateVariantTargets(rankedTemplates, progressionSettings);

        List<ContractTemplate> projectTemplates = parseProjectTemplates(
            contractsConfig.getConfigurationSection("project-templates"),
            settings,
            roadSettings,
            constructionRules,
            projectGenerationSettings
        );
        if (projectTemplates.isEmpty()) {
            projectTemplates = parseProjectTemplates(
                contractsConfig.getConfigurationSection("templates"),
                settings,
                roadSettings,
                constructionRules,
                projectGenerationSettings
            );
        }

        return new ContractCatalog(
            rankDescriptions,
            roadSettings,
            constructionRules,
            progressionSettings,
            projectGenerationSettings,
            rankedTemplates,
            projectTemplates
        );
    }

    private static List<ContractTemplate> expandRankedTemplates(ConfigurationSection root, PluginSettings settings) {
        if (root == null) {
            return List.of();
        }

        Map<String, List<MaterialLayer>> materialGroups = parseMaterialGroups(root.getConfigurationSection("material-groups"));
        Map<String, QuantityLayer> quantityTiers = parseQuantityTiers(root.getConfigurationSection("quantity-tiers"));
        Map<String, ContextLayer> contexts = parseContexts(root.getConfigurationSection("settlement-contexts"));
        Map<String, ModifierLayer> modifiers = parseModifiers(root.getConfigurationSection("modifiers"));
        Map<String, RewardLayer> rewardPackages = parseRewardPackages(root.getConfigurationSection("reward-packages"));
        ConfigurationSection archetypesSection = root.getConfigurationSection("archetypes");
        if (archetypesSection == null) {
            return List.of();
        }

        List<ContractTemplate> templates = new ArrayList<>();
        for (String key : archetypesSection.getKeys(false)) {
            ConfigurationSection section = archetypesSection.getConfigurationSection(key);
            if (section == null || !section.getBoolean("enabled", true)) {
                continue;
            }
            ArchetypeLayer archetype = parseArchetype(key, section, settings);
            if (archetype == null) {
                continue;
            }

            for (ContractRank rank : archetype.ranks()) {
                for (ContractScope scope : archetype.scopes()) {
                    for (MaterialLayer material : flattenMaterials(archetype.materialGroups(), materialGroups, rank)) {
                        for (QuantityLayer quantity : selectQuantities(archetype.quantityTiers(), quantityTiers, rank)) {
                            for (ContextLayer context : selectContexts(archetype.contexts(), contexts)) {
                                for (ModifierLayer modifier : selectModifiers(archetype.modifiers(), modifiers, rank)) {
                                    for (RewardLayer reward : selectRewards(archetype.rewardPackages(), rewardPackages, rank)) {
                                        ContractTemplate template = buildRankedTemplate(
                                            archetype,
                                            scope,
                                            rank,
                                            material,
                                            quantity,
                                            context,
                                            modifier,
                                            reward
                                        );
                                        if (template != null) {
                                            templates.add(template);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        templates.sort(Comparator.comparing(ContractTemplate::key));
        return List.copyOf(templates);
    }

    private static ContractTemplate buildRankedTemplate(
        ArchetypeLayer archetype,
        ContractScope scope,
        ContractRank rank,
        MaterialLayer material,
        QuantityLayer quantity,
        ContextLayer context,
        ModifierLayer modifier,
        RewardLayer reward
    ) {
        int amount = Math.max(1, (int) Math.round(material.unitAmount() * quantity.multiplier()));
        double rankRewardMultiplier = switch (rank) {
            case F -> 1.0D;
            case E -> 1.1D;
            case D -> 1.25D;
            case C -> 1.45D;
            case B -> 1.7D;
            case A -> 2.05D;
            case S -> 2.6D;
        };
        double money = (reward.flatMoney() + reward.moneyPerItem() * amount) * modifier.rewardMultiplier() * rankRewardMultiplier;
        int reputation = Math.max(0, (int) Math.round(reward.reputation() * modifier.rewardMultiplier()));

        String modifierPrefix = modifier.prefix().isBlank() ? "" : modifier.prefix().strip() + " ";
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("%material_name%", material.displayName());
        placeholders.put("%material_token%", TextUtil.prettyToken(material.material().name()));
        placeholders.put("%quantity_label%", quantity.label());
        placeholders.put("%context_name%", context.name());
        placeholders.put("%context_flavor%", context.flavor());
        placeholders.put("%modifier_prefix%", modifierPrefix);
        placeholders.put("%modifier_name%", modifier.displayName());
        placeholders.put("%archetype_name%", archetype.displayName());
        placeholders.put("%amount%", Integer.toString(amount));
        placeholders.put("%money%", MONEY_FMT.format(money));
        placeholders.put("%reputation%", Integer.toString(reputation));
        placeholders.put("%rank%", rank.name());

        String title = normalizeWhitespace(TextUtil.applyPlaceholders(archetype.titlePattern(), placeholders));
        String flavor = normalizeWhitespace(TextUtil.applyPlaceholders(archetype.flavorPattern(), placeholders));
        String variantKey = String.join(":",
            "ranked",
            scope.name().toLowerCase(Locale.ROOT),
            rank.name().toLowerCase(Locale.ROOT),
            archetype.key(),
            material.material().name().toLowerCase(Locale.ROOT),
            quantity.key(),
            context.key(),
            modifier.key(),
            reward.key()
        );

        RewardBundle rewardBundle = new RewardBundle(
            money,
            reputation,
            reward.itemRewards(),
            reward.commandRewards()
        );
        return new ContractTemplate(
            variantKey,
            scope,
            archetype.type(),
            rank,
            archetype.difficulty(),
            multiplyWeights(archetype.weight(), material.weight(), quantity.weight(), context.weight(), modifier.weight(), reward.weight()),
            material.material(),
            title,
            flavor.isBlank() ? List.of() : List.of("&7" + flavor),
            List.of(new ContractRequirement(material.material(), amount)),
            ConstructionMetadata.empty(),
            archetype.durationMinutes(),
            archetype.partialDeliveryAllowed(),
            archetype.publicOffer(),
            rewardBundle,
            RewardBundle.empty(),
            new BonusConfig(1.0D),
            archetype.poolTags(),
            ProjectTrigger.none()
        );
    }

    private static void validateVariantTargets(List<ContractTemplate> rankedTemplates, ProgressionSettings progressionSettings) {
        for (ContractRank rank : ContractRank.values()) {
            long available = rankedTemplates.stream()
                .filter(template -> rank == template.rank())
                .count();
            int target = progressionSettings.targetFor(rank);
            if (available < target) {
                LOGGER.warning("Rank " + rank.name() + " has only " + available + " generated variants; target is " + target + ".");
            }
        }
    }

    private static List<ContractTemplate> parseProjectTemplates(
        ConfigurationSection root,
        PluginSettings settings,
        RoadSettings roadSettings,
        ConstructionRules constructionRules,
        ProjectGenerationSettings projectGenerationSettings
    ) {
        if (root == null) {
            return List.of();
        }

        List<ContractTemplate> templates = new ArrayList<>();
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null || !sec.getBoolean("enabled", true)) {
                continue;
            }

            ContractType type = parseType(sec.getString("type", "CONSTRUCTION"));
            if (type != ContractType.CONSTRUCTION) {
                continue;
            }

            ContractScope scope = parseScope(sec.getString("scope", "LOCAL"));
            if (scope == null) {
                continue;
            }

            List<ContractRequirement> requirements = parseRequirements(sec);
            if (requirements.isEmpty()) {
                LOGGER.warning("Skipping project template '" + key + "' because it has no valid requirements.");
                continue;
            }

            ConstructionMetadata metadata = parseMetadata(sec.getConfigurationSection("metadata"));
            if (!validateTemplate(key, type, metadata, requirements, roadSettings, constructionRules)) {
                continue;
            }

            ProjectTrigger trigger = parseProjectTrigger(sec.getConfigurationSection("trigger"), projectGenerationSettings);
            Material icon = parseMaterial(sec.getString("icon"), requirements.getFirst().material());
            RewardBundle reward = parseRewardBundle(sec.getConfigurationSection("reward"), sec.getConfigurationSection("rewards"));
            RewardBundle bonusReward = parseRewardBundle(sec.getConfigurationSection("bonus-reward"), null);
            List<String> description = parseDescription(sec);
            ConfigurationSection bonusSec = sec.getConfigurationSection("bonus");
            double noDeathMult = bonusSec == null
                ? settings.defaultNoDeathMoneyMultiplier()
                : Math.max(1.0D, bonusSec.getDouble("no-death-money-multiplier", settings.defaultNoDeathMoneyMultiplier()));

            templates.add(new ContractTemplate(
                key,
                scope,
                type,
                null,
                sec.getString("difficulty", "INFRASTRUCTURE"),
                Math.max(1, sec.getInt("weight", 1)),
                icon,
                sec.getString("title", key),
                description,
                requirements,
                metadata,
                parseDurationMinutes(sec, scope, settings),
                sec.getBoolean("partial-delivery", true),
                sec.getBoolean("public", constructionRules.publicConstructionProjects()),
                reward,
                bonusReward,
                new BonusConfig(noDeathMult),
                sec.getStringList("pool-tags"),
                trigger
            ));
        }
        templates.sort(Comparator.comparing(ContractTemplate::key));
        return List.copyOf(templates);
    }

    private static ProgressionSettings parseProgressionSettings(FileConfiguration config) {
        Map<ContractRank, Integer> targets = parseRankIntMap(config.getConfigurationSection("rank-variant-targets"));
        Map<ContractRank, Integer> xpTable = parseRankIntMap(config.getConfigurationSection("contract-xp"));
        NavigableMap<Integer, Map<ContractRank, Integer>> rollTable = parseRankRollTable(config.getConfigurationSection("rank-roll-table"));
        AntiFrustrationSettings antiFrustration = parseAntiFrustration(config.getConfigurationSection("anti-frustration"));
        ConfigurationSection progressionSec = config.getConfigurationSection("progression");
        int xpPerLevel = progressionSec == null ? 10 : Math.max(1, progressionSec.getInt("xp-per-level", 10));
        int maxLevel = progressionSec == null ? 20 : Math.max(1, progressionSec.getInt("max-level", 20));
        return new ProgressionSettings(targets, xpTable, rollTable, antiFrustration, xpPerLevel, maxLevel);
    }

    private static AntiFrustrationSettings parseAntiFrustration(ConfigurationSection sec) {
        if (sec == null) {
            return AntiFrustrationSettings.defaults();
        }
        return new AntiFrustrationSettings(
            sec.getInt("high-rank-pity-threshold", 12),
            sec.getInt("elite-rank-pity-threshold", 25),
            sec.getInt("high-rank-weight-bonus", 12),
            sec.getInt("elite-rank-weight-bonus", 8),
            sec.getInt("recent-variant-memory", 20)
        );
    }

    private static ProjectGenerationSettings parseProjectGenerationSettings(ConfigurationSection sec) {
        if (sec == null) {
            return ProjectGenerationSettings.defaults();
        }
        return new ProjectGenerationSettings(
            sec.getBoolean("prefer-project-in-special-slot", true),
            sec.getInt("default-min-trust", 8),
            sec.getInt("default-min-routine-completions", 10),
            sec.getInt("default-min-project-completions", 0),
            Math.max(0L, sec.getLong("base-project-cooldown-minutes", 120L)) * 60L,
            sec.getInt("trust-per-routine-completion", 2),
            sec.getInt("trust-per-project-completion", 5),
            sec.getInt("trust-level-divisor", 25)
        );
    }

    private static ProjectTrigger parseProjectTrigger(ConfigurationSection sec, ProjectGenerationSettings defaults) {
        if (sec == null) {
            return new ProjectTrigger(
                defaults.defaultMinTrust(),
                defaults.defaultMinRoutineCompletions(),
                defaults.defaultMinProjectCompletions(),
                defaults.baseProjectCooldownSeconds()
            );
        }
        return new ProjectTrigger(
            sec.getInt("min-trust", defaults.defaultMinTrust()),
            sec.getInt("min-routine-completions", defaults.defaultMinRoutineCompletions()),
            sec.getInt("min-project-completions", defaults.defaultMinProjectCompletions()),
            Math.max(0L, sec.getLong("cooldown-minutes", defaults.baseProjectCooldownSeconds() / 60L)) * 60L
        );
    }

    private static Map<String, List<MaterialLayer>> parseMaterialGroups(ConfigurationSection sec) {
        Map<String, List<MaterialLayer>> groups = new LinkedHashMap<>();
        if (sec == null) {
            return groups;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection groupSec = sec.getConfigurationSection(key);
            if (groupSec == null) {
                continue;
            }
            List<MaterialLayer> materials = new ArrayList<>();
            for (Map<?, ?> entry : groupSec.getMapList("materials")) {
                Material material = parseMaterial(String.valueOf(entry.get("material")), Material.AIR);
                if (material == Material.AIR) {
                    continue;
                }
                String name = stringValue(entry.get("name"), TextUtil.prettyToken(material.name()));
                int unitAmount = parseInt(entry.get("unit-amount"), 16);
                int weight = parseInt(entry.get("weight"), 1);
                List<ContractRank> ranks = parseRanks(entry.get("ranks"));
                materials.add(new MaterialLayer(material, name, unitAmount, weight, ranks));
            }
            groups.put(key, List.copyOf(materials));
        }
        return groups;
    }

    private static Map<String, QuantityLayer> parseQuantityTiers(ConfigurationSection sec) {
        Map<String, QuantityLayer> result = new LinkedHashMap<>();
        if (sec == null) {
            return result;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection entry = sec.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            result.put(key, new QuantityLayer(
                key,
                entry.getString("label", ""),
                Math.max(0.25D, entry.getDouble("multiplier", 1.0D)),
                Math.max(1, entry.getInt("weight", 1)),
                parseRanks(entry.getStringList("ranks"))
            ));
        }
        return result;
    }

    private static Map<String, ContextLayer> parseContexts(ConfigurationSection sec) {
        Map<String, ContextLayer> result = new LinkedHashMap<>();
        if (sec == null) {
            return result;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection entry = sec.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            result.put(key, new ContextLayer(
                key,
                entry.getString("name", TextUtil.prettyToken(key)),
                entry.getString("flavor", entry.getString("name", TextUtil.prettyToken(key))),
                Math.max(1, entry.getInt("weight", 1))
            ));
        }
        return result;
    }

    private static Map<String, ModifierLayer> parseModifiers(ConfigurationSection sec) {
        Map<String, ModifierLayer> result = new LinkedHashMap<>();
        if (sec == null) {
            return result;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection entry = sec.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            result.put(key, new ModifierLayer(
                key,
                entry.getString("name", TextUtil.prettyToken(key)),
                entry.getString("prefix", ""),
                entry.getString("flavor", entry.getString("name", TextUtil.prettyToken(key))),
                Math.max(0.25D, entry.getDouble("reward-multiplier", 1.0D)),
                Math.max(1, entry.getInt("weight", 1)),
                parseRanks(entry.getStringList("ranks"))
            ));
        }
        return result;
    }

    private static Map<String, RewardLayer> parseRewardPackages(ConfigurationSection sec) {
        Map<String, RewardLayer> result = new LinkedHashMap<>();
        if (sec == null) {
            return result;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection entry = sec.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            RewardBundle bundle = parseRewardBundle(entry, null);
            result.put(key, new RewardLayer(
                key,
                Math.max(0.0D, entry.getDouble("money-per-item", 1.5D)),
                Math.max(0.0D, entry.getDouble("flat-money", bundle.money())),
                Math.max(0, entry.getInt("reputation", bundle.reputation())),
                bundle.itemRewards(),
                bundle.commandRewards(),
                Math.max(1, entry.getInt("weight", 1)),
                parseRanks(entry.getStringList("ranks"))
            ));
        }
        return result;
    }

    private static ArchetypeLayer parseArchetype(String key, ConfigurationSection sec, PluginSettings settings) {
        ContractType type = parseType(sec.getString("type", "DELIVERY"));
        if (type == null || type == ContractType.CONSTRUCTION) {
            return null;
        }
        List<ContractScope> scopes = parseScopes(sec.getStringList("scopes"));
        if (scopes.isEmpty()) {
            scopes = List.of(ContractScope.LOCAL);
        }
        List<ContractRank> ranks = parseRanks(sec.getStringList("ranks"));
        if (ranks.isEmpty()) {
            ranks = List.of(ContractRank.C);
        }
        int durationMinutes = sec.contains("expiration-minutes")
            ? Math.max(1, sec.getInt("expiration-minutes", 180))
            : (int) switch (scopes.getFirst()) {
                case LOCAL -> settings.localOfferLifetimeMinutes();
                case REGIONAL -> settings.regionalOfferLifetimeMinutes();
                case GLOBAL -> settings.globalOfferLifetimeMinutes();
            };
        return new ArchetypeLayer(
            key,
            type,
            sec.getString("display-name", TextUtil.prettyToken(key)),
            sec.getString("difficulty", "SUPPLY"),
            Math.max(1, sec.getInt("weight", 1)),
            scopes,
            ranks,
            sec.getString("title-pattern", "&6%modifier_prefix%%material_name% %archetype_name%"),
            sec.getString("flavor-pattern", "%context_name% needs %material_name% for %modifier_name% work"),
            sec.getStringList("material-groups"),
            sec.getStringList("quantity-tiers"),
            sec.getStringList("settlement-contexts"),
            sec.getStringList("modifiers"),
            sec.getStringList("reward-packages"),
            durationMinutes,
            sec.getBoolean("partial-delivery", true),
            sec.getBoolean("public", true),
            sec.getStringList("pool-tags")
        );
    }

    private static List<MaterialLayer> flattenMaterials(List<String> groupKeys, Map<String, List<MaterialLayer>> groups, ContractRank rank) {
        List<MaterialLayer> result = new ArrayList<>();
        for (String key : groupKeys) {
            for (MaterialLayer layer : groups.getOrDefault(key, List.of())) {
                if (layer.supports(rank)) {
                    result.add(layer);
                }
            }
        }
        return result;
    }

    private static List<QuantityLayer> selectQuantities(List<String> keys, Map<String, QuantityLayer> quantities, ContractRank rank) {
        List<QuantityLayer> result = new ArrayList<>();
        for (String key : keys) {
            QuantityLayer layer = quantities.get(key);
            if (layer != null && layer.supports(rank)) {
                result.add(layer);
            }
        }
        return result;
    }

    private static List<ContextLayer> selectContexts(List<String> keys, Map<String, ContextLayer> contexts) {
        List<ContextLayer> result = new ArrayList<>();
        for (String key : keys) {
            ContextLayer layer = contexts.get(key);
            if (layer != null) {
                result.add(layer);
            }
        }
        return result;
    }

    private static List<ModifierLayer> selectModifiers(List<String> keys, Map<String, ModifierLayer> modifiers, ContractRank rank) {
        List<ModifierLayer> result = new ArrayList<>();
        for (String key : keys) {
            ModifierLayer layer = modifiers.get(key);
            if (layer != null && layer.supports(rank)) {
                result.add(layer);
            }
        }
        return result;
    }

    private static List<RewardLayer> selectRewards(List<String> keys, Map<String, RewardLayer> rewards, ContractRank rank) {
        List<RewardLayer> result = new ArrayList<>();
        for (String key : keys) {
            RewardLayer layer = rewards.get(key);
            if (layer != null && layer.supports(rank)) {
                result.add(layer);
            }
        }
        return result;
    }

    private static int multiplyWeights(int... values) {
        long total = 1L;
        for (int value : values) {
            total *= Math.max(1, value);
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static String normalizeWhitespace(String input) {
        return input == null ? "" : input.trim().replaceAll("\\s{2,}", " ");
    }

    private static Map<ContractRank, String> parseRankScale(ConfigurationSection section) {
        EnumMap<ContractRank, String> result = new EnumMap<>(ContractRank.class);
        for (ContractRank rank : ContractRank.values()) {
            result.put(rank, section == null ? rank.name() : section.getString(rank.name(), rank.name()));
        }
        return result;
    }

    private static Map<ContractRank, Integer> parseRankIntMap(ConfigurationSection section) {
        EnumMap<ContractRank, Integer> result = new EnumMap<>(ContractRank.class);
        for (ContractRank rank : ContractRank.values()) {
            int fallback = 0;
            result.put(rank, section == null ? fallback : Math.max(0, section.getInt(rank.name(), fallback)));
        }
        return result;
    }

    private static NavigableMap<Integer, Map<ContractRank, Integer>> parseRankRollTable(ConfigurationSection section) {
        NavigableMap<Integer, Map<ContractRank, Integer>> result = new TreeMap<>();
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection row = section.getConfigurationSection(key);
            if (row == null) {
                continue;
            }
            int level = parseRollLevel(key);
            if (level > 0) {
                result.put(level, parseRankIntMap(row));
            }
        }
        return result;
    }

    private static int parseRollLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return -1;
        }
        String normalized = raw.toLowerCase(Locale.ROOT).replace("level-", "").trim();
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static List<ContractRank> parseRanks(Object raw) {
        if (raw instanceof List<?> list) {
            List<ContractRank> result = new ArrayList<>();
            for (Object entry : list) {
                ContractRank rank = ContractRank.parseNullable(String.valueOf(entry));
                if (rank != null) {
                    result.add(rank);
                }
            }
            return List.copyOf(result);
        }
        if (raw instanceof String value && !value.isBlank()) {
            List<ContractRank> result = new ArrayList<>();
            for (String part : value.split(",")) {
                ContractRank rank = ContractRank.parseNullable(part);
                if (rank != null) {
                    result.add(rank);
                }
            }
            return List.copyOf(result);
        }
        return List.of();
    }

    private static List<ContractScope> parseScopes(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<ContractScope> result = new ArrayList<>();
        for (String value : values) {
            ContractScope scope = parseScope(value);
            if (scope != null) {
                result.add(scope);
            }
        }
        return List.copyOf(result);
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
                int amount = parseInt(rawEntry.get("amount"), 0);
                if (material != Material.AIR && amount > 0) {
                    requirements.add(new ContractRequirement(material, amount));
                }
            }
        }
        if (!requirements.isEmpty()) {
            return requirements;
        }
        Material legacyMaterial = parseMaterial(firstNonBlank(sec.getString("material"), sec.getString("objective.material")), Material.AIR);
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
                Material material = parseMaterial(String.valueOf(rawEntry.get("material")), Material.AIR);
                int amount = parseInt(rawEntry.get("amount"), 0);
                RewardItem item = new RewardItem(material, amount);
                if (item.isValid()) {
                    items.add(item);
                }
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

    private static String stringValue(Object raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = String.valueOf(raw);
        return value.isBlank() ? fallback : value;
    }

    private static Material parseMaterial(String raw, Material fallback) {
        return ContractDataCodec.parseMaterial(raw, fallback);
    }

    private static int parseInt(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (raw == null) {
            return fallback;
        }
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(raw)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static RoadSettings parseRoadSettings(ConfigurationSection sec) {
        if (sec == null) {
            return RoadSettings.defaults();
        }
        Map<String, Integer> shovelCoverage = new LinkedHashMap<>();
        ConfigurationSection coverageSec = sec.getConfigurationSection("shovel-coverage");
        if (coverageSec != null) {
            for (String key : coverageSec.getKeys(false)) {
                shovelCoverage.put(key.toUpperCase(Locale.ROOT), Math.max(0, coverageSec.getInt(key, 0)));
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
                LOGGER.warning("Skipping road project '" + key + "' because DIRT requirement " + actualDirt
                    + " does not match road-tiles*dirt-per-tile (" + expectedDirt + ").");
                return false;
            }
        }
        if (metadata.isBuildingProject()) {
            int expectedWheat = metadata.floorCount() * constructionRules.wheatPerFloor();
            int actualWheat = requirementAmount(requirements, Material.WHEAT);
            if (expectedWheat > 0 && actualWheat != expectedWheat) {
                LOGGER.warning("Skipping building project '" + key + "' because WHEAT requirement " + actualWheat
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

    private record MaterialLayer(Material material, String displayName, int unitAmount, int weight, List<ContractRank> ranks) {
        boolean supports(ContractRank rank) {
            return ranks.isEmpty() || ranks.contains(rank);
        }
    }

    private record QuantityLayer(String key, String label, double multiplier, int weight, List<ContractRank> ranks) {
        boolean supports(ContractRank rank) {
            return ranks.isEmpty() || ranks.contains(rank);
        }
    }

    private record ContextLayer(String key, String name, String flavor, int weight) {
    }

    private record ModifierLayer(
        String key,
        String displayName,
        String prefix,
        String flavor,
        double rewardMultiplier,
        int weight,
        List<ContractRank> ranks
    ) {
        boolean supports(ContractRank rank) {
            return ranks.isEmpty() || ranks.contains(rank);
        }
    }

    private record RewardLayer(
        String key,
        double moneyPerItem,
        double flatMoney,
        int reputation,
        List<RewardItem> itemRewards,
        List<String> commandRewards,
        int weight,
        List<ContractRank> ranks
    ) {
        boolean supports(ContractRank rank) {
            return ranks.isEmpty() || ranks.contains(rank);
        }
    }

    private record ArchetypeLayer(
        String key,
        ContractType type,
        String displayName,
        String difficulty,
        int weight,
        List<ContractScope> scopes,
        List<ContractRank> ranks,
        String titlePattern,
        String flavorPattern,
        List<String> materialGroups,
        List<String> quantityTiers,
        List<String> contexts,
        List<String> modifiers,
        List<String> rewardPackages,
        int durationMinutes,
        boolean partialDeliveryAllowed,
        boolean publicOffer,
        List<String> poolTags
    ) {
    }
}
