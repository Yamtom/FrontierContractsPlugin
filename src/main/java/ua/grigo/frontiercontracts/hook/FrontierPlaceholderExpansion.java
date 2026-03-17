package ua.grigo.frontiercontracts.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ua.grigo.frontiercontracts.FrontierContractsPlugin;

public final class FrontierPlaceholderExpansion extends PlaceholderExpansion {
    private final FrontierContractsPlugin plugin;

    public FrontierPlaceholderExpansion(FrontierContractsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "frontiercontracts";
    }

    @Override
    public @NotNull String getAuthor() {
        return "grigo";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        return switch (params.toLowerCase()) {
            case "active" -> Integer.toString(plugin.getContractService().countActiveContracts(player.getUniqueId()));
            case "completed" -> Integer.toString(plugin.getContractService().getStats(player.getUniqueId()).completedContracts());
            case "reputation" -> Integer.toString(plugin.getContractService().getStats(player.getUniqueId()).reputation());
            case "time_left" -> plugin.getContractService().smallestRemainingTime(player.getUniqueId());
            default -> null;
        };
    }
}
