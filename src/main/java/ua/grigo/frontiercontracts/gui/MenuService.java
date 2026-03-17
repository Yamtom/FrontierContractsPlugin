package ua.grigo.frontiercontracts.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import ua.grigo.frontiercontracts.FrontierContractsPlugin;
import ua.grigo.frontiercontracts.contract.ActionResult;
import ua.grigo.frontiercontracts.contract.ContractService;
import ua.grigo.frontiercontracts.model.Board;
import ua.grigo.frontiercontracts.model.ContractOffer;
import ua.grigo.frontiercontracts.model.PlayerContract;
import ua.grigo.frontiercontracts.model.RewardItem;
import ua.grigo.frontiercontracts.util.ItemUtil;
import ua.grigo.frontiercontracts.util.MessageService;
import ua.grigo.frontiercontracts.util.TextUtil;

public final class MenuService {
    private static final int[] BOARD_TASK_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private static final int[] GLOBAL_SLOTS = {28, 29, 30, 31, 32, 33};

    private final FrontierContractsPlugin plugin;
    private final ContractService contractService;
    private final MessageService messages;

    public MenuService(FrontierContractsPlugin plugin, ContractService contractService, MessageService messages) {
        this.plugin = plugin;
        this.contractService = contractService;
        this.messages = messages;
    }

    public void openBoardMenu(Player player, Board board) {
        contractService.cleanupExpiredContent();

        BoardMenuHolder holder = new BoardMenuHolder(player.getUniqueId(), board.id());
        String title = TextUtil.colorize(TextUtil.applyPlaceholders(
            plugin.getSettings().boardMenuTitle(),
            Map.of("%board_name%", board.name())
        ));
        Inventory inventory = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inventory);
        fillBackground(inventory);

        inventory.setItem(4, ItemUtil.menuItem(Material.WRITABLE_BOOK, board.name(), buildBoardHeaderLore(player, board)));
        inventory.setItem(5, ItemUtil.menuItem(Material.CHEST, messages.raw("gui.sections.local"), List.of(
            "&7Public settlement supply requests.",
            "&7Right-click this board with the requested resource to deliver.",
            "&7Empty hand opens this menu."
        )));
        inventory.setItem(49, buildActiveContractsItem(player));
        inventory.setItem(45, ItemUtil.menuItem(Material.ARROW, messages.raw("gui.labels.back"), messages.list("gui.lore.click-back")));

        bindBoardOffers(holder, inventory, contractService.getLocalOffers(board.id()));
        player.openInventory(inventory);
    }

    public void openMainMenu(Player player) {
        contractService.cleanupExpiredContent();

        ContractsMenuHolder holder = new ContractsMenuHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, 54, TextUtil.colorize(plugin.getSettings().boardMenuTitle()));
        holder.setInventory(inventory);
        fillBackground(inventory);

        inventory.setItem(4, ItemUtil.menuItem(Material.WRITABLE_BOOK, messages.raw("gui.sections.global"), List.of(
            "&7Hybrid fallback menu for non-local offers.",
            "&7Physical settlement boards handle local supply requests."
        )));
        inventory.setItem(22, ItemUtil.menuItem(Material.COMPASS, messages.raw("gui.sections.nearby"), List.of(
            "&7Walk to a physical board to see local tasks.",
            "&7Use empty hand to browse, item in hand to deliver."
        )));
        inventory.setItem(49, buildActiveContractsItem(player));
        bindGlobalOffers(holder, inventory, contractService.getGlobalOffers(), player);
        player.openInventory(inventory);
    }

    public void openActiveMenu(Player player) {
        ContractsMenuHolder holder = new ContractsMenuHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, 54, TextUtil.colorize(plugin.getSettings().activeMenuTitle()));
        holder.setInventory(inventory);
        fillBackground(inventory);

        List<PlayerContract> contracts = contractService.getOpenContracts(player.getUniqueId());
        if (contracts.isEmpty()) {
            inventory.setItem(22, ItemUtil.menuItem(Material.BARRIER, messages.raw("gui.labels.no-contracts"), messages.list("gui.lore.no-active-contracts")));
        } else {
            int[] slots = {10, 11, 12, 13, 14, 15, 19, 20, 21, 22, 23, 24, 28, 29, 30, 31, 32, 33};
            int limit = Math.min(contracts.size(), slots.length);
            for (int index = 0; index < limit; index++) {
                PlayerContract contract = contracts.get(index);
                ContractOffer offer = contractService.getOffer(contract.offerId()).orElse(null);
                if (offer == null) {
                    continue;
                }
                List<String> lore = buildActiveContractLore(player, offer, contract);
                inventory.setItem(slots[index], ItemUtil.menuItem(offer.iconMaterial(), offer.title(), lore, true));
                holder.bindSlot(slots[index], offer.id());
            }
        }

        inventory.setItem(49, ItemUtil.menuItem(Material.ARROW, messages.raw("gui.labels.back"), messages.list("gui.lore.click-back")));
        player.openInventory(inventory);
    }

    public void openDetailMenu(Player player, String offerId) {
        ContractOffer offer = contractService.getOffer(offerId).orElse(null);
        if (offer == null) {
            messages.send(player, "errors.unavailable-offer");
            openMainMenu(player);
            return;
        }

        ContractDetailHolder holder = new ContractDetailHolder(player.getUniqueId(), offerId);
        Inventory inventory = Bukkit.createInventory(holder, 27, TextUtil.colorize(plugin.getSettings().detailMenuTitle()));
        holder.setInventory(inventory);
        fillBackground(inventory);

        PlayerContract activeContract = contractService.getOpenContract(player.getUniqueId(), offerId).orElse(null);
        boolean acceptedBefore = contractService.hasAcceptedOffer(player.getUniqueId(), offerId);
        inventory.setItem(13, buildDetailOfferItem(player, offer, activeContract, acceptedBefore));
        inventory.setItem(22, ItemUtil.menuItem(Material.ARROW, messages.raw("gui.labels.back"), messages.list("gui.lore.click-back")));

        if (offer.isBoardLocal()) {
            inventory.setItem(11, ItemUtil.menuItem(Material.CHEST, messages.raw("gui.labels.delivery"), List.of(
                "&7Bring &f" + contractService.objectiveLabel(offer) + "&7 to the board.",
                "&7Right-click the physical board while holding the item.",
                "&7Progress is shared on the board."
            )));
        } else if (activeContract == null && !acceptedBefore && offer.active()) {
            inventory.setItem(11, ItemUtil.menuItem(Material.LIME_DYE, messages.raw("gui.labels.accept"), messages.list("gui.lore.click-accept"), true));
            inventory.setItem(15, ItemUtil.menuItem(Material.PAPER, messages.raw("gui.labels.active"), List.of(
                "&7After accepting, the timer starts immediately."
            )));
        } else if (activeContract != null) {
            inventory.setItem(11, buildActionItem(player, offer, activeContract));
            inventory.setItem(15, buildStatusItem(activeContract));
        } else {
            inventory.setItem(11, ItemUtil.menuItem(Material.BARRIER, messages.raw("gui.labels.unavailable"), List.of(
                "&7This offer is no longer available to you."
            )));
        }

        player.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();

        if (holder instanceof BoardMenuHolder boardHolder) {
            handleBoardMenuClick(event, boardHolder);
            return;
        }
        if (holder instanceof ContractsMenuHolder menuHolder) {
            handleMainMenuClick(event, menuHolder);
            return;
        }
        if (holder instanceof ContractDetailHolder detailHolder) {
            handleDetailMenuClick(event, detailHolder);
        }
    }

    public void handleDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof BoardMenuHolder || holder instanceof ContractsMenuHolder || holder instanceof ContractDetailHolder) {
            event.setCancelled(true);
        }
    }

    private void handleBoardMenuClick(InventoryClickEvent event, BoardMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (event.getRawSlot() == 45) {
            openMainMenu(player);
            return;
        }
        String offerId = holder.offerAt(event.getRawSlot());
        if (offerId != null) {
            openDetailMenu(player, offerId);
        }
    }

    private void handleMainMenuClick(InventoryClickEvent event, ContractsMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (event.getRawSlot() == 49) {
            openActiveMenu(player);
            return;
        }

        String offerId = holder.offerAt(event.getRawSlot());
        if (offerId != null) {
            openDetailMenu(player, offerId);
        }
    }

    private void handleDetailMenuClick(InventoryClickEvent event, ContractDetailHolder detailHolder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        switch (event.getRawSlot()) {
            case 11 -> {
                ContractOffer offer = contractService.getOffer(detailHolder.offerId()).orElse(null);
                PlayerContract activeContract = contractService.getOpenContract(player.getUniqueId(), detailHolder.offerId()).orElse(null);
                boolean acceptedBefore = contractService.hasAcceptedOffer(player.getUniqueId(), detailHolder.offerId());
                if (offer == null) {
                    messages.send(player, "errors.unavailable-offer");
                    openMainMenu(player);
                    return;
                }
                if (!offer.isBoardLocal()) {
                    if (activeContract == null && !acceptedBefore && offer.active()) {
                        dispatch(player, contractService.accept(player, offer.id()));
                    } else if (activeContract != null) {
                        dispatch(player, contractService.submitSpecific(player, offer.id()));
                    }
                }
                openDetailMenu(player, detailHolder.offerId());
            }
            case 22 -> {
                ContractOffer offer = contractService.getOffer(detailHolder.offerId()).orElse(null);
                if (offer != null && offer.isBoardLocal() && offer.boardId() != null) {
                    Board board = plugin.getBoardService().getBoard(offer.boardId()).orElse(null);
                    if (board != null) {
                        openBoardMenu(player, board);
                        return;
                    }
                }
                openMainMenu(player);
            }
        }
    }

    private void bindBoardOffers(BoardMenuHolder holder, Inventory inventory, List<ContractOffer> offers) {
        int limit = Math.min(offers.size(), BOARD_TASK_SLOTS.length);
        for (int index = 0; index < limit; index++) {
            ContractOffer offer = offers.get(index);
            inventory.setItem(BOARD_TASK_SLOTS[index], buildBoardOfferItem(offer));
            holder.bindSlot(BOARD_TASK_SLOTS[index], offer.id());
        }
    }

    private void bindGlobalOffers(ContractsMenuHolder holder, Inventory inventory, List<ContractOffer> offers, Player player) {
        int limit = Math.min(offers.size(), GLOBAL_SLOTS.length);
        for (int index = 0; index < limit; index++) {
            ContractOffer offer = offers.get(index);
            PlayerContract activeContract = contractService.getOpenContract(player.getUniqueId(), offer.id()).orElse(null);
            boolean acceptedBefore = contractService.hasAcceptedOffer(player.getUniqueId(), offer.id());
            inventory.setItem(GLOBAL_SLOTS[index], buildOfferItem(player, offer, activeContract, acceptedBefore));
            holder.bindSlot(GLOBAL_SLOTS[index], offer.id());
        }
    }

    private List<String> buildBoardHeaderLore(Player player, Board board) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Settlement: &f" + board.name());
        lore.add("&7Type: &f" + board.displayTypeName());
        lore.add("&7Bell link: " + (board.isValidStructure() ? "&avalid" : "&cinvalid"));
        if (board.bellDistance() >= 0.0D) {
            lore.add("&7Bell distance: &f" + String.format("%.1f", board.bellDistance()));
        }
        lore.add("&7Refresh: &f" + TextUtil.formatDuration(Math.max(0L, board.nextRefreshAtEpochSeconds() - (System.currentTimeMillis() / 1000L))));
        lore.add("&7Reputation: &b" + contractService.getSettlementReputation(player.getUniqueId(), board.id()));
        if (!board.validationErrors().isEmpty()) {
            lore.add("");
            lore.add("&cValidation issues:");
            board.validationErrors().stream().limit(3).forEach(error -> lore.add("&7- " + error));
        }
        if (!board.flavorText().isBlank()) {
            lore.add("");
            lore.addAll(TextUtil.splitLines(board.flavorText()));
        }
        return lore;
    }

    private ItemStack buildBoardOfferItem(ContractOffer offer) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Resource: &f" + TextUtil.prettyToken(offer.requiredMaterial().name()));
        lore.add("&7Progress: &f" + offer.deliveredAmount() + "/" + offer.requiredAmount());
        lore.add("&7Reward: &a" + summarizeReward(offer));
        lore.add("&7Expires: &f" + TextUtil.formatDuration(Math.max(0L, offer.offerExpiresAtEpochSeconds() - (System.currentTimeMillis() / 1000L))));
        lore.add("&7Delivery: " + (offer.partialDeliveryAllowed() ? "&apartial allowed" : "&cfull only"));
        lore.add("&eClick for details");
        return ItemUtil.menuItem(offer.iconMaterial(), offer.title(), lore, offer.deliveredAmount() > 0);
    }

    private ItemStack buildOfferItem(Player player, ContractOffer offer, PlayerContract activeContract, boolean acceptedBefore) {
        List<String> lore = new ArrayList<>();
        lore.add(messages.plain("gui.lore.scope", Map.of("%scope%", offer.scope().displayName())));
        lore.add(messages.plain("gui.lore.difficulty", Map.of("%difficulty%", offer.difficulty())));
        lore.add(messages.plain("gui.lore.objective", Map.of("%objective%", contractService.objectiveLabel(offer))));
        lore.add(messages.plain("gui.lore.reward-money", Map.of("%money%", TextUtil.formatMoney(offer.rewards().money()))));
        lore.add(messages.plain("gui.lore.reward-reputation", Map.of("%reputation%", Integer.toString(offer.rewards().reputation()))));
        lore.add(messages.plain("gui.lore.time-left", Map.of(
            "%time_left%",
            TextUtil.formatDuration(Math.max(0L, offer.offerExpiresAtEpochSeconds() - (System.currentTimeMillis() / 1000L)))
        )));
        if (offer.rewards().hasItemRewards()) {
            RewardItem rewardItem = offer.rewards().itemRewards().getFirst();
            lore.add(messages.plain("gui.lore.reward-item", Map.of(
                "%amount%", Integer.toString(rewardItem.amount()),
                "%item%", TextUtil.prettyToken(rewardItem.material().name())
            )));
        }
        if (activeContract != null) {
            lore.add(messages.plain("gui.lore.progress", Map.of(
                "%progress%", Integer.toString(displayProgress(player, offer, activeContract)),
                "%goal%", Integer.toString(offer.requiredAmount())
            )));
            lore.addAll(messages.list("gui.lore.already-accepted"));
        } else if (acceptedBefore) {
            lore.addAll(messages.list("gui.lore.already-finished"));
        } else {
            lore.addAll(messages.list("gui.lore.click-details"));
        }
        return ItemUtil.menuItem(offer.iconMaterial(), offer.title(), lore, activeContract != null);
    }

    private ItemStack buildDetailOfferItem(Player player, ContractOffer offer, PlayerContract activeContract, boolean acceptedBefore) {
        List<String> lore = new ArrayList<>();
        lore.addAll(TextUtil.colorize(TextUtil.splitLines(offer.description())));
        lore.add("");
        lore.add(messages.plain("gui.lore.objective", Map.of("%objective%", contractService.objectiveLabel(offer))));
        lore.add("&7Board progress: &f" + offer.deliveredAmount() + "/" + offer.requiredAmount());
        lore.add(messages.plain("gui.lore.reward-money", Map.of("%money%", TextUtil.formatMoney(offer.rewards().money()))));
        lore.add(messages.plain("gui.lore.reward-reputation", Map.of("%reputation%", Integer.toString(offer.rewards().reputation()))));
        if (offer.rewards().hasItemRewards()) {
            RewardItem rewardItem = offer.rewards().itemRewards().getFirst();
            lore.add(messages.plain("gui.lore.reward-item", Map.of(
                "%amount%", Integer.toString(rewardItem.amount()),
                "%item%", TextUtil.prettyToken(rewardItem.material().name())
            )));
        }

        if (offer.isBoardLocal()) {
            lore.add("&7This is a public board task.");
            lore.add("&7Deliver by right-clicking the physical board.");
        } else if (activeContract != null) {
            lore.add(messages.plain("gui.lore.progress", Map.of(
                "%progress%", Integer.toString(displayProgress(player, offer, activeContract)),
                "%goal%", Integer.toString(offer.requiredAmount())
            )));
            lore.add(messages.plain("gui.lore.time-left", Map.of(
                "%time_left%", TextUtil.formatDuration(activeContract.remainingSeconds(System.currentTimeMillis() / 1000L))
            )));
        } else if (acceptedBefore) {
            lore.add("&7This contract is already closed for you.");
        } else {
            lore.add(messages.plain("gui.lore.time-left", Map.of(
                "%time_left%",
                TextUtil.formatDuration(Math.max(0L, offer.offerExpiresAtEpochSeconds() - (System.currentTimeMillis() / 1000L)))
            )));
        }

        return ItemUtil.menuItem(offer.iconMaterial(), offer.title(), lore, activeContract != null || offer.deliveredAmount() > 0);
    }

    private ItemStack buildActionItem(Player player, ContractOffer offer, PlayerContract activeContract) {
        boolean ready = displayProgress(player, offer, activeContract) >= offer.requiredAmount();
        if (ready) {
            return ItemUtil.menuItem(Material.LIME_DYE, messages.raw("gui.labels.submit"), messages.list("gui.lore.click-submit"), true);
        }
        return ItemUtil.menuItem(Material.CLOCK, messages.raw("gui.labels.active"), List.of(
            "&7This contract is not ready yet.",
            "&7Bring the required items and return."
        ));
    }

    private ItemStack buildStatusItem(PlayerContract activeContract) {
        String label = activeContract.status().name().equals("READY_TO_CLAIM")
            ? messages.raw("gui.labels.ready")
            : messages.raw("gui.labels.active");
        return ItemUtil.menuItem(Material.KNOWLEDGE_BOOK, label, List.of(
            "&7Progress is stored in SQLite.",
            "&7Nothing is lost on restart."
        ));
    }

    private List<String> buildActiveContractLore(Player player, ContractOffer offer, PlayerContract contract) {
        List<String> lore = new ArrayList<>();
        lore.add(messages.plain("gui.lore.objective", Map.of("%objective%", contractService.objectiveLabel(offer))));
        lore.add(messages.plain("gui.lore.reward-money", Map.of("%money%", TextUtil.formatMoney(offer.rewards().money()))));
        lore.add(messages.plain("gui.lore.progress", Map.of(
            "%progress%", Integer.toString(displayProgress(player, offer, contract)),
            "%goal%", Integer.toString(offer.requiredAmount())
        )));
        lore.add(messages.plain("gui.lore.time-left", Map.of(
            "%time_left%", TextUtil.formatDuration(contract.remainingSeconds(System.currentTimeMillis() / 1000L))
        )));
        return lore;
    }

    private ItemStack buildActiveContractsItem(Player player) {
        List<PlayerContract> activeContracts = contractService.getOpenContracts(player.getUniqueId());
        if (activeContracts.isEmpty()) {
            return ItemUtil.menuItem(Material.BOOK, messages.raw("gui.sections.active"), messages.list("gui.lore.no-active-contracts"));
        }

        List<String> lore = new ArrayList<>();
        for (PlayerContract contract : activeContracts.stream().limit(5).toList()) {
            ContractOffer offer = contractService.getOffer(contract.offerId()).orElse(null);
            if (offer == null) {
                continue;
            }
            lore.add("&f" + offer.title());
            lore.add(" &7" + contractService.objectiveLabel(offer));
            lore.add(" &7" + TextUtil.formatDuration(contract.remainingSeconds(System.currentTimeMillis() / 1000L)));
        }
        return ItemUtil.menuItem(Material.BOOK, messages.raw("gui.sections.active"), lore);
    }

    private int displayProgress(Player player, ContractOffer offer, PlayerContract activeContract) {
        Material material = Material.matchMaterial(offer.objectiveKey());
        if (material == null) {
            return activeContract.progress();
        }
        return Math.min(offer.requiredAmount(), ItemUtil.countMaterial(player.getInventory(), material));
    }

    private String summarizeReward(ContractOffer offer) {
        List<String> parts = new ArrayList<>();
        if (offer.rewards().money() > 0.0D) {
            parts.add(TextUtil.formatMoney(offer.rewards().money()) + " coins");
        }
        if (offer.rewards().reputation() > 0) {
            parts.add("+" + offer.rewards().reputation() + " rep");
        }
        if (offer.rewards().hasItemRewards()) {
            RewardItem rewardItem = offer.rewards().itemRewards().getFirst();
            parts.add(rewardItem.amount() + "x " + TextUtil.prettyToken(rewardItem.material().name()));
        }
        if (offer.rewards().hasCommandRewards()) {
            parts.add("command reward");
        }
        return parts.isEmpty() ? "reward" : String.join(", ", parts);
    }

    private void dispatch(Player player, ActionResult result) {
        if (result.messageKey() != null && !result.messageKey().isBlank()) {
            messages.send(player, result.messageKey(), result.placeholders());
        }
    }

    private void fillBackground(Inventory inventory) {
        ItemStack filler = ItemUtil.menuItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }
}
