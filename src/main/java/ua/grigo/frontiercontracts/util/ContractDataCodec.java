package ua.grigo.frontiercontracts.util;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import ua.grigo.frontiercontracts.model.BlockPosition;
import ua.grigo.frontiercontracts.model.ConstructionMetadata;
import ua.grigo.frontiercontracts.model.ConstructionSite;
import ua.grigo.frontiercontracts.model.ContractRequirement;

public final class ContractDataCodec {
    private ContractDataCodec() {
    }

    public static String encodeRequirements(List<ContractRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return "";
        }
        YamlConfiguration yaml = new YamlConfiguration();
        int index = 0;
        for (ContractRequirement requirement : requirements) {
            String prefix = "requirements." + index++;
            yaml.set(prefix + ".material", requirement.material().name());
            yaml.set(prefix + ".amount", requirement.amount());
            yaml.set(prefix + ".delivered", requirement.deliveredAmount());
        }
        return yaml.saveToString();
    }

    public static List<ContractRequirement> decodeRequirements(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(raw);
        } catch (InvalidConfigurationException exception) {
            return List.of();
        }
        ConfigurationSection section = yaml.getConfigurationSection("requirements");
        if (section == null) {
            return List.of();
        }
        List<ContractRequirement> requirements = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            Material material = parseMaterial(entry.getString("material"), Material.AIR);
            int amount = Math.max(1, entry.getInt("amount", 1));
            int delivered = Math.max(0, entry.getInt("delivered", 0));
            requirements.add(new ContractRequirement(material, amount, delivered));
        }
        return requirements;
    }

    public static String encodeMetadata(ConstructionMetadata metadata) {
        if (metadata == null || (!metadata.isRoadProject() && !metadata.isBuildingProject())) {
            return "";
        }
        YamlConfiguration yaml = new YamlConfiguration();
        if (metadata.surface() != null) {
            yaml.set("surface", metadata.surface().name());
        }
        if (metadata.roadTiles() != null) {
            yaml.set("road-tiles", metadata.roadTiles());
        }
        if (metadata.recommendedTool() != null && !metadata.recommendedTool().isBlank()) {
            yaml.set("recommended-tool", metadata.recommendedTool());
        }
        if (!metadata.building().isBlank()) {
            yaml.set("building", metadata.building());
        }
        if (metadata.floors() != null) {
            yaml.set("floors", metadata.floors());
        }
        return yaml.saveToString();
    }

    public static ConstructionMetadata decodeMetadata(String raw) {
        if (raw == null || raw.isBlank()) {
            return ConstructionMetadata.empty();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(raw);
        } catch (InvalidConfigurationException exception) {
            return ConstructionMetadata.empty();
        }
        return new ConstructionMetadata(
            parseMaterial(yaml.getString("surface"), null),
            yaml.contains("road-tiles") ? Math.max(1, yaml.getInt("road-tiles", 1)) : null,
            yaml.getString("recommended-tool", ""),
            yaml.getString("building", ""),
            yaml.contains("floors") ? Math.max(1, yaml.getInt("floors", 1)) : null
        );
    }

    public static String encodeSite(ConstructionSite site) {
        if (site == null) {
            return "";
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world", site.worldName());
        yaml.set("anchor.x", site.anchorX());
        yaml.set("anchor.y", site.anchorY());
        yaml.set("anchor.z", site.anchorZ());
        yaml.set("bounds.min.x", site.minX());
        yaml.set("bounds.min.y", site.minY());
        yaml.set("bounds.min.z", site.minZ());
        yaml.set("bounds.max.x", site.maxX());
        yaml.set("bounds.max.y", site.maxY());
        yaml.set("bounds.max.z", site.maxZ());
        int index = 0;
        for (BlockPosition position : site.roadTiles()) {
            String prefix = "road-tiles." + index++;
            yaml.set(prefix + ".x", position.x());
            yaml.set(prefix + ".y", position.y());
            yaml.set(prefix + ".z", position.z());
        }
        return yaml.saveToString();
    }

    public static ConstructionSite decodeSite(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(raw);
        } catch (InvalidConfigurationException exception) {
            return null;
        }
        String world = yaml.getString("world");
        if (world == null || world.isBlank()) {
            return null;
        }
        List<BlockPosition> roadTiles = new ArrayList<>();
        ConfigurationSection tilesSection = yaml.getConfigurationSection("road-tiles");
        if (tilesSection != null) {
            for (String key : tilesSection.getKeys(false)) {
                ConfigurationSection entry = tilesSection.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                roadTiles.add(new BlockPosition(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")));
            }
        }
        return new ConstructionSite(
            world,
            yaml.getInt("anchor.x"),
            yaml.getInt("anchor.y"),
            yaml.getInt("anchor.z"),
            yaml.getInt("bounds.min.x"),
            yaml.getInt("bounds.min.y"),
            yaml.getInt("bounds.min.z"),
            yaml.getInt("bounds.max.x"),
            yaml.getInt("bounds.max.y"),
            yaml.getInt("bounds.max.z"),
            roadTiles
        );
    }

    public static Material parseMaterial(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    public static String encodeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("values", values);
        return yaml.saveToString();
    }

    public static List<String> decodeStringList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(raw);
        } catch (InvalidConfigurationException exception) {
            return List.of();
        }
        return List.copyOf(yaml.getStringList("values"));
    }
}
