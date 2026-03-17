package ua.grigo.frontiercontracts.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import ua.grigo.frontiercontracts.gui.MenuService;

public final class MenuListener implements Listener {
    private final MenuService menuService;

    public MenuListener(MenuService menuService) {
        this.menuService = menuService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        menuService.handleClick(event);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        menuService.handleDrag(event);
    }
}
