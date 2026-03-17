package ua.grigo.frontiercontracts.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ContractsMenuHolder implements InventoryHolder {
    private final UUID viewerUuid;
    private final Map<Integer, String> offerSlots = new HashMap<>();
    private Inventory inventory;

    public ContractsMenuHolder(UUID viewerUuid) {
        this.viewerUuid = viewerUuid;
    }

    public UUID viewerUuid() {
        return viewerUuid;
    }

    public void bindSlot(int slot, String offerId) {
        offerSlots.put(slot, offerId);
    }

    public String offerAt(int slot) {
        return offerSlots.get(slot);
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
