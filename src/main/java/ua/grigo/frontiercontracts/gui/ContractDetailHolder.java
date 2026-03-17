package ua.grigo.frontiercontracts.gui;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ContractDetailHolder implements InventoryHolder {
    private final UUID viewerUuid;
    private final String offerId;
    private Inventory inventory;

    public ContractDetailHolder(UUID viewerUuid, String offerId) {
        this.viewerUuid = viewerUuid;
        this.offerId = offerId;
    }

    public UUID viewerUuid() {
        return viewerUuid;
    }

    public String offerId() {
        return offerId;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
