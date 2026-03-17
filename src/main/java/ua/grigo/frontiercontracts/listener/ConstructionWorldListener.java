package ua.grigo.frontiercontracts.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import ua.grigo.frontiercontracts.FrontierContractsPlugin;
import ua.grigo.frontiercontracts.contract.ContractService;

public final class ConstructionWorldListener implements Listener {
    private final FrontierContractsPlugin plugin;
    private final ContractService contractService;

    public ConstructionWorldListener(FrontierContractsPlugin plugin, ContractService contractService) {
        this.plugin = plugin;
        this.contractService = contractService;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        contractService.checkConstructionCompletions(event.getPlayer(), event.getBlockPlaced());
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
        plugin.getServer().getScheduler().runTask(plugin, () ->
            contractService.checkConstructionCompletions(event.getPlayer(), clicked));
    }
}
