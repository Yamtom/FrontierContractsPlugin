package ua.grigo.frontiercontracts.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class BoardMenuHolder implements InventoryHolder {
    private final UUID viewerUuid;
    private final String boardId;
    private final Map<Integer, String> offerSlots = new HashMap<>();
    private Inventory inventory;

    public BoardMenuHolder(UUID viewerUuid, String boardId) {
        this.viewerUuid = viewerUuid;
        this.boardId = boardId;
    }

    public UUID viewerUuid() {
        return viewerUuid;
    }

    public String boardId() {
        return boardId;
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
