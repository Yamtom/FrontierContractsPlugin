package ua.grigo.frontiercontracts.listener;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import ua.grigo.frontiercontracts.FrontierContractsPlugin;
import ua.grigo.frontiercontracts.contract.ContractService;
import ua.grigo.frontiercontracts.model.ContractOffer;

public final class ConstructionWorldListener implements Listener {
    private final FrontierContractsPlugin plugin;
    private final ContractService contractService;

    public ConstructionWorldListener(FrontierContractsPlugin plugin, ContractService contractService) {
        this.plugin = plugin;
        this.contractService = contractService;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getSettings().blockCreativeSubmit()
                && event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return;
        }
        Block placed = event.getBlockPlaced();
        plugin.getServer().getScheduler().runTask(plugin, () ->
            contractService.checkConstructionCompletions(event.getPlayer(), placed));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onShovelPath(PlayerInteractEvent event) {
        if (!event.getAction().name().startsWith("RIGHT_CLICK")) {
            return;
        }
        if (event.getItem() == null || !event.getItem().getType().name().endsWith("_SHOVEL")) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            contractService.checkConstructionCompletions(event.getPlayer(), clicked);
            contractService.findConstructionOfferAt(
                    clicked.getWorld().getName(), clicked.getX(), clicked.getY(), clicked.getZ())
                .ifPresent(offer -> {
                    int pct = (int) Math.round(offer.aggregateCompletionRatio() * 100);
                    event.getPlayer().sendActionBar(
                        LegacyComponentSerializer.legacySection().deserialize(
                            "§aBuild: §f" + pct + "%"
                        )
                    );
                });
        });
    }
}
