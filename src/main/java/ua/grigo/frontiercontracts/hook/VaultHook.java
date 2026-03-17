package ua.grigo.frontiercontracts.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import ua.grigo.frontiercontracts.FrontierContractsPlugin;

public final class VaultHook {
    private final FrontierContractsPlugin plugin;
    private Economy economy;

    public VaultHook(FrontierContractsPlugin plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            economy = null;
            return;
        }

        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        economy = registration == null ? null : registration.getProvider();
    }

    public boolean hasEconomy() {
        return economy != null;
    }

    public void deposit(Player player, double amount) {
        if (economy != null && amount > 0.0D) {
            economy.depositPlayer(player, amount);
        }
    }
}
