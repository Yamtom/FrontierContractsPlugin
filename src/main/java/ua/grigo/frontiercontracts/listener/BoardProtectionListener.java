package ua.grigo.frontiercontracts.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import ua.grigo.frontiercontracts.board.BoardService;
import ua.grigo.frontiercontracts.model.Board;
import ua.grigo.frontiercontracts.util.MessageService;

public final class BoardProtectionListener implements Listener {
    private final BoardService boardService;
    private final MessageService messages;

    public BoardProtectionListener(BoardService boardService, MessageService messages) {
        this.boardService = boardService;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Board board = boardService.getBoardAt(event.getBlock().getLocation()).orElse(null);
        if (board == null || hasBypass(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        messages.send(event.getPlayer(), "errors.board-protected");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSignChange(SignChangeEvent event) {
        Board board = boardService.getBoardAt(event.getBlock().getLocation()).orElse(null);
        Player player = event.getPlayer();
        if (board == null || player == null || hasBypass(player)) {
            return;
        }
        event.setCancelled(true);
        messages.send(player, "errors.board-protected");
    }

    private boolean hasBypass(Player player) {
        return player.hasPermission("frontiercontracts.admin");
    }
}
