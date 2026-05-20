package ua.grigo.frontiercontracts.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ua.grigo.frontiercontracts.FrontierContractsPlugin;
import ua.grigo.frontiercontracts.board.BoardLayout;
import ua.grigo.frontiercontracts.board.BoardPlacementService;
import ua.grigo.frontiercontracts.board.BoardValidationResult;
import ua.grigo.frontiercontracts.contract.ActionResult;
import ua.grigo.frontiercontracts.model.Board;
import ua.grigo.frontiercontracts.model.BoardColumnSide;
import ua.grigo.frontiercontracts.model.BoardType;
import ua.grigo.frontiercontracts.util.MessageService;

public final class ContractsCommand implements CommandExecutor, TabCompleter {
    private final FrontierContractsPlugin plugin;
    private final MessageService messages;

    public ContractsCommand(FrontierContractsPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "errors.player-only");
                return true;
            }
            if (!sender.hasPermission("frontiercontracts.use")) {
                messages.send(sender, "errors.no-permission");
                return true;
            }
            plugin.getMenuService().openMainMenu(player);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "active" -> handleActive(sender);
            case "submit" -> handleSubmit(sender);
            case "abandon" -> handleAbandon(sender, args);
            case "admin" -> handleAdmin(sender, args);
            default -> {
                messages.send(sender, "errors.unknown-subcommand");
                yield true;
            }
        };
    }

    private boolean handleActive(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "errors.player-only");
            return true;
        }
        if (!sender.hasPermission("frontiercontracts.use")) {
            messages.send(sender, "errors.no-permission");
            return true;
        }
        plugin.getMenuService().openActiveMenu(player);
        return true;
    }

    private boolean handleSubmit(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "errors.player-only");
            return true;
        }
        if (!sender.hasPermission("frontiercontracts.use")) {
            messages.send(sender, "errors.no-permission");
            return true;
        }
        int claimed = plugin.getContractService().submitAllEligible(player);
        if (claimed > 0) {
            messages.send(player, "contracts.submit-summary", Map.of("%claimed%", Integer.toString(claimed)));
        } else {
            messages.send(player, "contracts.submit-summary-none");
        }
        return true;
    }

    private boolean handleAbandon(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "errors.player-only");
            return true;
        }
        if (!sender.hasPermission("frontiercontracts.use")) {
            messages.send(sender, "errors.no-permission");
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "errors.missing-argument");
            return true;
        }
        dispatch(sender, plugin.getContractService().abandon(player, args[1]));
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("frontiercontracts.admin")) {
            messages.send(sender, "errors.no-permission");
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "errors.unknown-subcommand");
            return true;
        }

        String adminSubcommand = args[1].toLowerCase(Locale.ROOT);
        return switch (adminSubcommand) {
            case "reload" -> handleReload(sender);
            case "regenerate" -> handleRegenerate(sender);
            case "scanboard" -> handleScanBoard(sender, args);
            case "placeboard" -> handlePlaceBoard(sender, args);
            case "validateboard" -> handleValidateBoard(sender, args);
            case "board" -> handleBoardAdminSubcommand(sender, args);
            default -> {
                messages.send(sender, "errors.unknown-subcommand");
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        if (plugin.reloadPluginState()) {
            messages.send(sender, "system.reloaded");
        } else {
            messages.send(sender, "system.reload-failed");
        }
        return true;
    }

    private boolean handleRegenerate(CommandSender sender) {
        plugin.getContractService().regenerateOffers();
        messages.send(sender, "system.regenerated");
        return true;
    }

    private boolean handleScanBoard(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "errors.player-only");
            return true;
        }
        if (args.length < 5) {
            messages.send(sender, "errors.missing-argument");
            return true;
        }

        String boardId = args[2];
        if (plugin.getBoardService().getBoard(boardId).isPresent()) {
            messages.send(sender, "errors.board-id-exists");
            return true;
        }

        Block target = resolveTargetBlock(player);
        if (target == null) {
            messages.send(sender, "errors.no-board-target");
            return true;
        }

        BoardValidationResult result = plugin.getBoardService().validator().scan(target);
        if (!result.valid()) {
            sendValidationErrors(sender, result);
            return true;
        }

        Board board = buildBoard(boardId, args[3], joinName(args, 4), result);
        if (!plugin.getBoardService().addBoard(board)) {
            messages.send(sender, "errors.board-id-exists");
            return true;
        }
        plugin.getContractService().refreshBoard(board);
        messages.send(sender, "system.board-registered", Map.of("%board%", boardId));
        return true;
    }

    private boolean handlePlaceBoard(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "errors.player-only");
            return true;
        }
        if (args.length < 5) {
            messages.send(sender, "errors.missing-argument");
            return true;
        }

        String boardId = args[2];
        if (plugin.getBoardService().getBoard(boardId).isPresent()) {
            messages.send(sender, "errors.board-id-exists");
            return true;
        }

        Block target = resolveTargetBlock(player);
        if (target == null) {
            messages.send(sender, "errors.no-board-target");
            return true;
        }

        Location anchor = target.getRelative(BlockFace.UP).getLocation();
        BlockFace facing = normalizeHorizontal(player.getFacing()).getOppositeFace();
        BoardPlacementService placementService = new BoardPlacementService(plugin.getBoardService().validator());
        BoardValidationResult result = placementService.placeDefault(player, anchor, facing, BoardColumnSide.RIGHT);
        if (!result.valid()) {
            sendValidationErrors(sender, result);
            return true;
        }

        Board board = buildBoard(boardId, args[3], joinName(args, 4), result);
        if (!plugin.getBoardService().addBoard(board)) {
            messages.send(sender, "errors.board-id-exists");
            return true;
        }
        plugin.getContractService().refreshBoard(board);
        messages.send(sender, "system.board-placed", Map.of("%board%", boardId));
        return true;
    }

    private boolean handleValidateBoard(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "errors.missing-argument");
            return true;
        }
        Board board = plugin.getBoardService().getBoard(args[2]).orElse(null);
        if (board == null) {
            messages.send(sender, "errors.board-not-found");
            return true;
        }

        BoardValidationResult result = plugin.getBoardService().validateBoard(board);
        plugin.getContractService().syncBoardDisplay(board);
        if (!result.valid()) {
            sendValidationErrors(sender, result);
        } else {
            messages.send(sender, "system.board-valid", Map.of(
                "%board%", board.id(),
                "%distance%", String.format("%.1f", result.bellDistance())
            ));
        }
        return true;
    }

    private boolean handleBoardAdminSubcommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "errors.unknown-subcommand");
            return true;
        }

        String boardSubcommand = args[2].toLowerCase(Locale.ROOT);
        return switch (boardSubcommand) {
            case "list" -> {
                var boards = plugin.getBoardService().allBoards();
                if (boards.isEmpty()) {
                    sender.sendMessage("§cNo boards found.");
                    yield true;
                }
                sender.sendMessage("§6=== boards ===");
                for (Board board : boards) {
                    sender.sendMessage(String.format(
                        Locale.ROOT,
                        " §f%s §7(%s, %d,%d,%d, %s, bell=%.1f)",
                        board.id(),
                        board.type().name(),
                        board.blockX(),
                        board.blockY(),
                        board.blockZ(),
                        board.isValidStructure() ? "VALID" : "INVALID",
                        board.bellDistance()
                    ));
                }
                yield true;
            }
            case "refresh" -> {
                if (args.length < 4) {
                    messages.send(sender, "errors.missing-argument");
                    yield true;
                }
                Board board = plugin.getBoardService().getBoard(args[3]).orElse(null);
                if (board == null) {
                    messages.send(sender, "errors.board-not-found");
                    yield true;
                }
                plugin.getContractService().refreshBoard(board);
                messages.send(sender, "system.board-refreshed", Map.of("%board%", board.id()));
                yield true;
            }
            case "reputation" -> {
                if (args.length < 6) {
                    messages.send(sender, "errors.missing-argument");
                    yield true;
                }
                try {
                    UUID playerUuid = UUID.fromString(args[3]);
                    String boardId = args[4];
                    int delta = Integer.parseInt(args[5]);
                    plugin.getContractService().applyReputationDelta(playerUuid, boardId, delta);
                    messages.send(sender, "system.reputation-applied", Map.of(
                        "%player%", args[3],
                        "%board%", boardId,
                        "%amount%", Integer.toString(delta)
                    ));
                } catch (IllegalArgumentException exception) {
                    messages.send(sender, "errors.invalid-argument");
                }
                yield true;
            }
            default -> {
                messages.send(sender, "errors.unknown-subcommand");
                yield true;
            }
        };
    }

    private Board buildBoard(String boardId, String typeArg, String name, BoardValidationResult result) {
        BoardType type = BoardType.fromString(typeArg);
        String customTypeName = type == BoardType.CUSTOM ? typeArg : null;
        long now = System.currentTimeMillis() / 1000L;
        long refreshInterval = plugin.getSettings().boardRefreshIntervalSeconds();
        return new Board(
            boardId,
            name,
            type,
            customTypeName,
            result.anchor().getWorld().getName(),
            result.anchor().getBlockX(),
            result.anchor().getBlockY(),
            result.anchor().getBlockZ(),
            result.facing(),
            result.columnSide(),
            result.bellLocation() == null ? null : result.bellLocation().getBlockX(),
            result.bellLocation() == null ? null : result.bellLocation().getBlockY(),
            result.bellLocation() == null ? null : result.bellLocation().getBlockZ(),
            result.bellDistance(),
            result.valid(),
            result.errors(),
            "",
            List.of(),
            List.of(),
            Map.of(ua.grigo.frontiercontracts.model.ContractType.DELIVERY.name(), 1.0D),
            1.0D,
            1.0D,
            1.0D,
            BoardLayout.TASK_SIGN_COUNT,
            "",
            refreshInterval,
            now + refreshInterval,
            true,
            now
        );
    }

    private void sendValidationErrors(CommandSender sender, BoardValidationResult result) {
        messages.send(sender, "errors.board-invalid");
        for (String error : result.errors()) {
            sender.sendMessage("§7- " + error);
        }
    }

    private String joinName(String[] args, int startIndex) {
        StringBuilder builder = new StringBuilder();
        for (int index = startIndex; index < args.length; index++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    private BlockFace normalizeHorizontal(BlockFace facing) {
        return switch (facing) {
            case SOUTH, SOUTH_EAST, SOUTH_SOUTH_EAST, SOUTH_SOUTH_WEST, SOUTH_WEST -> BlockFace.SOUTH;
            case EAST, EAST_NORTH_EAST, EAST_SOUTH_EAST -> BlockFace.EAST;
            case WEST, WEST_NORTH_WEST, WEST_SOUTH_WEST -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
    }

    private void dispatch(CommandSender sender, ActionResult result) {
        if (result.messageKey() != null && !result.messageKey().isBlank()) {
            messages.send(sender, result.messageKey(), result.placeholders());
        }
    }

    @SuppressWarnings("deprecation")
    private Block resolveTargetBlock(Player player) {
        return player.getTargetBlock(null, 10);
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        String[] args
    ) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("frontiercontracts.use")) {
                completions.add("active");
                completions.add("submit");
                completions.add("abandon");
            }
            if (sender.hasPermission("frontiercontracts.admin")) {
                completions.add("admin");
            }
            return completions;
        }
        if (args.length == 2 && "admin".equalsIgnoreCase(args[0]) && sender.hasPermission("frontiercontracts.admin")) {
            completions.add("reload");
            completions.add("regenerate");
            completions.add("scanboard");
            completions.add("placeboard");
            completions.add("validateboard");
            completions.add("board");
            return completions;
        }
        if (args.length == 3 && "admin".equalsIgnoreCase(args[0]) && "board".equalsIgnoreCase(args[1])) {
            completions.add("list");
            completions.add("refresh");
            completions.add("reputation");
            return completions;
        }
        if (args.length == 3 && "admin".equalsIgnoreCase(args[0]) && "validateboard".equalsIgnoreCase(args[1])) {
            completions.addAll(plugin.getBoardService().allBoards().stream().map(Board::id).toList());
            return completions;
        }
        if (args.length == 4 && "admin".equalsIgnoreCase(args[0]) && "board".equalsIgnoreCase(args[1]) && "refresh".equalsIgnoreCase(args[2])) {
            completions.addAll(plugin.getBoardService().allBoards().stream().map(Board::id).toList());
            return completions;
        }
        return completions;
    }
}
