package ua.grigo.frontiercontracts.util;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemUtil {
    private ItemUtil() {
    }

    public static ItemStack menuItem(Material material, String name, List<String> lore) {
        return menuItem(material, name, lore, false);
    }

    public static ItemStack menuItem(Material material, String name, List<String> lore, boolean highlighted) {
        ItemStack stack = new ItemStack(material == null ? Material.BARRIER : material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize(name));
            meta.setLore(TextUtil.colorize(lore));
            if (highlighted) {
                meta.addEnchant(Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static int countMaterial(PlayerInventory inventory, Material material) {
        int total = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }

    public static boolean removeMaterial(PlayerInventory inventory, Material material, int amount) {
        if (countMaterial(inventory, material) < amount) {
            return false;
        }

        int remaining = amount;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() != material) {
                continue;
            }

            int taken = Math.min(item.getAmount(), remaining);
            item.setAmount(item.getAmount() - taken);
            remaining -= taken;

            if (item.getAmount() <= 0) {
                inventory.setItem(slot, null);
            } else {
                inventory.setItem(slot, item);
            }

            if (remaining <= 0) {
                return true;
            }
        }
        return remaining <= 0;
    }

    public static ItemStack getHeldItem(PlayerInventory inventory, EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND ? inventory.getItemInOffHand() : inventory.getItemInMainHand();
    }

    public static void removeFromHand(PlayerInventory inventory, EquipmentSlot hand, int amount) {
        ItemStack heldItem = getHeldItem(inventory, hand);
        if (heldItem == null || heldItem.getType() == Material.AIR) {
            return;
        }

        int remaining = Math.max(0, heldItem.getAmount() - Math.max(0, amount));
        if (remaining <= 0) {
            if (hand == EquipmentSlot.OFF_HAND) {
                inventory.setItemInOffHand(null);
            } else {
                inventory.setItemInMainHand(null);
            }
            return;
        }

        heldItem.setAmount(remaining);
        if (hand == EquipmentSlot.OFF_HAND) {
            inventory.setItemInOffHand(heldItem);
        } else {
            inventory.setItemInMainHand(heldItem);
        }
    }

    public static void giveOrDrop(Player player, ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
            return;
        }

        Inventory inventory = player.getInventory();
        inventory.addItem(stack).values().forEach(leftover ->
            player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    public static List<String> lore(String... lines) {
        List<String> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(line);
        }
        return lore;
    }
}
