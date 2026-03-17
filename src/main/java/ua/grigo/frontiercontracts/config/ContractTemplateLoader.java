package ua.grigo.frontiercontracts.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import ua.grigo.frontiercontracts.model.BonusConfig;
import ua.grigo.frontiercontracts.model.ContractScope;
import ua.grigo.frontiercontracts.model.ContractTemplate;
import ua.grigo.frontiercontracts.model.ContractType;
import ua.grigo.frontiercontracts.model.RewardBundle;
import ua.grigo.frontiercontracts.model.RewardItem;

public final class ContractTemplateLoader {
    private ContractTemplateLoader() {}

    public static List<ContractTemplate> load(FileConfiguration contractsConfig, PluginSettings settings) {
        List<ContractTemplate> templates = new ArrayList<>();
        ConfigurationSection root = contractsConfig.getConfigurationSection("templates");
        if (root == null) {
            return templates;
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

            Material material = parseMaterial(
                firstNonBlank(sec.getString("material"), sec.getString("objective.material")),
                Material.CHEST
            );
            Material icon = parseMaterial(sec.getString("icon"), material);

            int amount = parseExactAmount(sec);
            int durationMinutes = parseDurationMinutes(sec, scope, settings);
            boolean partialDeliveryAllowed = sec.getBoolean("partial-delivery", true);
            boolean publicOffer = sec.getBoolean("public", true);

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
                sec.getString("difficulty", "SUPPLY"),
                Math.max(1, sec.getInt("weight", 1)),
                icon,
                sec.getString("title", key),
                description,
                material,
                amount,
                durationMinutes,
                partialDeliveryAllowed,
                publicOffer,
                reward,
                bonusReward,
                new BonusConfig(noDeathMult),
                sec.getStringList("pool-tags")
            ));
        }
        return templates;
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

    private static int parseExactAmount(ConfigurationSection sec) {
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
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            Material material = Material.valueOf(raw.toUpperCase(Locale.ROOT));
            return material.isItem() ? material : fallback;
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
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
}
