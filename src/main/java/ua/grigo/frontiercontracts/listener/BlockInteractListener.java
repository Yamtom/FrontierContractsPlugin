package ua.grigo.frontiercontracts.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import ua.grigo.frontiercontracts.board.BoardService;
import ua.grigo.frontiercontracts.contract.ActionResult;
import ua.grigo.frontiercontracts.contract.ContractService;
import ua.grigo.frontiercontracts.gui.MenuService;
import ua.grigo.frontiercontracts.model.Board;
import ua.grigo.frontiercontracts.util.ItemUtil;
import ua.grigo.frontiercontracts.util.MessageService;

public final class BlockInteractListener implements Listener {
    private final BoardService boardService;
    private final ContractService contractService;
    private final MenuService menuService;
    private final MessageService messages;

    public BlockInteractListener(
        BoardService boardService,
        ContractService contractService,
        MenuService menuService,
        MessageService messages
    ) {
        this.boardService = boardService;
        this.contractService = contractService;
        this.menuService = menuService;
        this.messages = messages;
    }

    @EventHandler
    public void onPlayerInteractBlock(PlayerInteractEvent event) {
        if (!event.getAction().name().startsWith("RIGHT_CLICK")) {
            return;
        }
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Player player = event.getPlayer();
        Board board = boardService.getBoardAt(block.getLocation()).orElse(null);
        if (board == null) {
            return;
        }

        event.setCancelled(true);
        boardService.revalidateBoard(board, false);
        contractService.syncBoardDisplay(board);

        if (!board.active() || !board.isValidStructure()) {
            messages.send(player, "errors.board-invalid");
            return;
        }

        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        ItemStack heldItem = ItemUtil.getHeldItem(player.getInventory(), hand);
        if (heldItem == null || heldItem.getType() == Material.AIR) {
            menuService.openBoardMenu(player, board);
            return;
        }

        ActionResult result = contractService.deliverToBoard(player, board, hand);
        if (result.messageKey() != null && !result.messageKey().isBlank()) {
            messages.send(player, result.messageKey(), result.placeholders());
        }
    }
}
