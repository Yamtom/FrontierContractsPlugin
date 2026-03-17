package ua.grigo.frontiercontracts.util;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import ua.grigo.frontiercontracts.model.RewardBundle;
import ua.grigo.frontiercontracts.model.RewardItem;

public final class RewardBundleCodec {
    private RewardBundleCodec() {
    }

    public static String encode(RewardBundle bundle) {
        if (bundle == null || !bundle.hasAnyReward()) {
            return "";
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("money", bundle.money());
        yaml.set("reputation", bundle.reputation());

        int index = 0;
        for (RewardItem item : bundle.itemRewards()) {
            if (!item.isValid()) {
                continue;
            }
            String prefix = "items." + index++;
            yaml.set(prefix + ".material", item.material().name());
            yaml.set(prefix + ".amount", item.amount());
        }
        yaml.set("commands", bundle.commandRewards());
        return yaml.saveToString();
    }

    public static RewardBundle decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return RewardBundle.empty();
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(raw);
        } catch (InvalidConfigurationException exception) {
            return RewardBundle.empty();
        }

        List<RewardItem> items = new ArrayList<>();
        ConfigurationSection itemsSection = yaml.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection entry = itemsSection.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                RewardItem item = new RewardItem(
                    parseMaterial(entry.getString("material")),
                    Math.max(0, entry.getInt("amount", 0))
                );
                if (item.isValid()) {
                    items.add(item);
                }
            }
        }

        return new RewardBundle(
            Math.max(0.0D, yaml.getDouble("money", 0.0D)),
            Math.max(0, yaml.getInt("reputation", 0)),
            items,
            yaml.getStringList("commands")
        );
    }

    private static Material parseMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return Material.AIR;
        }
        try {
            return Material.valueOf(raw);
        } catch (IllegalArgumentException exception) {
            return Material.AIR;
        }
    }
}
