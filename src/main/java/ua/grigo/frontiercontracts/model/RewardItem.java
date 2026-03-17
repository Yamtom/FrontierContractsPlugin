package ua.grigo.frontiercontracts.model;

import org.bukkit.Material;

public record RewardItem(Material material, int amount) {
    public boolean isValid() {
        return material != null && material != Material.AIR && amount > 0;
    }
}
