package ua.grigo.frontiercontracts.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import ua.grigo.frontiercontracts.contract.ContractService;

public final class ContractProgressListener implements Listener {
    private final ContractService contractService;

    public ContractProgressListener(ContractService contractService) {
        this.contractService = contractService;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        contractService.markDeath(event.getEntity().getUniqueId());
    }
}
