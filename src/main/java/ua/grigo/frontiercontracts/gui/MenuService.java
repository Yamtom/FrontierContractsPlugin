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
import ua.grigo.frontiercontracts.model.ConstructionSite;
import ua.grigo.frontiercontracts.model.ContractOffer;
import ua.grigo.frontiercontracts.model.ContractRequirement;
import ua.grigo.frontiercontracts.model.ContractScope;
import ua.grigo.frontiercontracts.model.ContractType;
import ua.grigo.frontiercontracts.model.PlayerContract;
import ua.grigo.frontiercontracts.model.RewardItem;
import ua.grigo.frontiercontracts.util.ItemUtil;
import ua.grigo.frontiercontracts.util.MessageService;
import ua.grigo.frontiercontracts.util.TextUtil;

public final class MenuService {
    private static final int[] BOARD_TASK_SLOTS = {11, 12, 13, 14, 15};
    private static final int[] REGIONAL_SLOTS = {10, 11, 12, 13, 14, 15, 16};
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
            "&7Local public requests on this physical board.",
            "&7Right-click this board with the requested resource to deliver.",
            "&7Construction tasks show compact requirement progress."
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

        inventory.setItem(4, ItemUtil.menuItem(Material.COMPASS, messages.raw("gui.sections.regional"), List.of(
            "&7Shared board-backed projects from nearby settlements.",
            "&7Regional tasks remain public and are completed via their linked board."
        )));
        inventory.setItem(22, ItemUtil.menuItem(Material.WRITABLE_BOOK, messages.raw("gui.sections.global"), List.of(
            "&7Hybrid accepted contracts.",
            "&7Only global offers use accept/submit flow."
        )));
        inventory.setItem(49, buildActiveContractsItem(player));

        bindDirectoryOffers(holder, inventory, contractService.getRegionalOffersForDirectory(), REGIONAL_SLOTS, player);
        bindDirectoryOffers(holder, inventory, contractService.getGlobalOffers(), GLOBAL_SLOTS, player);
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
                inventory.setItem(slots[index], ItemUtil.menuItem(offer.iconMaterial(), offer.title(), buildActiveContractLore(player, offer, contract), true));
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
        Inventory inventory = Bukkit.createInventory(holder, 45, TextUtil.colorize(plugin.getSettings().detailMenuTitle()));
        holder.setInventory(inventory);
        fillBackground(inventory);

        PlayerContract activeContract = contractService.getOpenContract(player.getUniqueId(), offerId).orElse(null);
        boolean acceptedBefore = contractService.hasAcceptedOffer(player.getUniqueId(), offerId);
        inventory.setItem(13, buildDetailOfferItem(player, offer, activeContract, acceptedBefore));
        inventory.setItem(40, ItemUtil.menuItem(Material.ARROW, messages.raw("gui.labels.back"), messages.list("gui.lore.click-back")));
        inventory.setItem(29, buildInfoItem(offer));
        inventory.setItem(31, buildRewardItem(offer));
        inventory.setItem(33, buildRequirementsItem(offer));

        if (offer.scope() == ContractScope.GLOBAL) {
            if (activeContract == null && !acceptedBefore && offer.active()) {
                inventory.setItem(20, ItemUtil.menuItem(Material.LIME_DYE, messages.raw("gui.labels.accept"), messages.list("gui.lore.click-accept"), true));
            } else if (activeContract != null) {
                inventory.setItem(20, buildActionItem(player, offer, activeContract));
                inventory.setItem(24, buildStatusItem(activeContract));
            } else {
                inventory.setItem(20, ItemUtil.menuItem(Material.BARRIER, messages.raw("gui.labels.unavailable"), List.of(
                    "&7This offer is no longer available to you."
                )));
            }
        } else {
            inventory.setItem(20, ItemUtil.menuItem(Material.CHEST, messages.raw("gui.labels.delivery"), buildBoardFlowLore(offer)));
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
            case 20 -> {
                ContractOffer offer = contractService.getOffer(detailHolder.offerId()).orElse(null);
                PlayerContract activeContract = contractService.getOpenContract(player.getUniqueId(), detailHolder.offerId()).orElse(null);
                boolean acceptedBefore = contractService.hasAcceptedOffer(player.getUniqueId(), detailHolder.offerId());
                if (offer == null) {
                    messages.send(player, "errors.unavailable-offer");
                    openMainMenu(player);
                    return;
                }
                if (offer.scope() == ContractScope.GLOBAL) {
                    if (activeContract == null && !acceptedBefore && offer.active()) {
                        dispatch(player, contractService.accept(player, offer.id()));
                    } else if (activeContract != null) {
                        dispatch(player, contractService.submitSpecific(player, offer.id()));
                    }
                    openDetailMenu(player, detailHolder.offerId());
                }
            }
            case 40 -> {
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

    private void bindDirectoryOffers(ContractsMenuHolder holder, Inventory inventory, List<ContractOffer> offers, int[] slots, Player player) {
        int limit = Math.min(offers.size(), slots.length);
        for (int index = 0; index < limit; index++) {
            ContractOffer offer = offers.get(index);
            PlayerContract activeContract = contractService.getOpenContract(player.getUniqueId(), offer.id()).orElse(null);
            boolean acceptedBefore = contractService.hasAcceptedOffer(player.getUniqueId(), offer.id());
            inventory.setItem(slots[index], buildOfferItem(player, offer, activeContract, acceptedBefore));
            holder.bindSlot(slots[index], offer.id());
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
        lore.add("&7Settlement trust: &a" + contractService.getSettlementTrust(board.id()));
        lore.add("&7Community level: &f" + contractService.communityLevel());
        lore.add("&7Regional pool: &f" + contractService.getRegionalOffers(board.id()).size());
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
        lore.add("&7Rank: &f" + contractService.rankLabel(offer) + " &8- &7" + contractService.rankDescription(offer.rank()));
        lore.add("&7Type: &f" + offer.type().displayName());
        lore.addAll(buildCompactProgressLore(offer));
        lore.add("&7Reward: &a" + summarizeReward(offer));
        lore.add("&7Expires: &f" + TextUtil.formatDuration(Math.max(0L, offer.offerExpiresAtEpochSeconds() - (System.currentTimeMillis() / 1000L))));
        lore.add("&eClick for details");
        return ItemUtil.menuItem(offer.iconMaterial(), offer.title(), lore, offer.totalDeliveredAmount() > 0);
    }

    private ItemStack buildOfferItem(Player player, ContractOffer offer, PlayerContract activeContract, boolean acceptedBefore) {
        List<String> lore = new ArrayList<>();
        lore.add(messages.plain("gui.lore.scope", Map.of("%scope%", offer.scope().displayName())));
        lore.add("&7Rank: &f" + contractService.rankLabel(offer) + " &8- &7" + contractService.rankDescription(offer.rank()));
        lore.add(messages.plain("gui.lore.difficulty", Map.of("%difficulty%", offer.difficulty())));
        lore.add(messages.plain("gui.lore.objective", Map.of("%objective%", contractService.objectiveLabel(offer))));
        lore.addAll(buildCompactProgressLore(offer));
        lore.add(messages.plain("gui.lore.reward-money", Map.of("%money%", TextUtil.formatMoney(offer.rewards().money()))));
        lore.add(messages.plain("gui.lore.reward-reputation", Map.of("%reputation%", Integer.toString(offer.rewards().reputation()))));
        lore.add(messages.plain("gui.lore.time-left", Map.of(
            "%time_left%",
            TextUtil.formatDuration(Math.max(0L, offer.offerExpiresAtEpochSeconds() - (System.currentTimeMillis() / 1000L)))
        )));
        if (offer.scope() == ContractScope.GLOBAL) {
            if (activeContract != null) {
                lore.add(messages.plain("gui.lore.progress", Map.of(
                    "%progress%", Integer.toString(contractService.inventoryProgress(player, offer)),
                    "%goal%", Integer.toString(offer.totalRequiredAmount())
                )));
                lore.addAll(messages.list("gui.lore.already-accepted"));
            } else if (acceptedBefore) {
                lore.addAll(messages.list("gui.lore.already-finished"));
            } else {
                lore.addAll(messages.list("gui.lore.click-details"));
            }
        } else {
            lore.add("&7Linked board: &f" + (offer.boardId() == null ? "-" : offer.boardId()));
            lore.add("&7Public regional project.");
            lore.addAll(messages.list("gui.lore.click-details"));
        }
        return ItemUtil.menuItem(offer.iconMaterial(), offer.title(), lore, activeContract != null || offer.totalDeliveredAmount() > 0);
    }

    private ItemStack buildDetailOfferItem(Player player, ContractOffer offer, PlayerContract activeContract, boolean acceptedBefore) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Scope: &f" + offer.scope().displayName());
        lore.add("&7Rank: &f" + contractService.rankLabel(offer) + " &8- &7" + contractService.rankDescription(offer.rank()));
        lore.add("&7Type: &f" + offer.type().displayName());
        lore.add("&7Difficulty: &f" + offer.difficulty());
        lore.add("");
        lore.addAll(TextUtil.colorize(TextUtil.splitLines(offer.description())));
        lore.add("");
        lore.addAll(buildRequirementLines(offer));
        if (offer.scope() == ContractScope.GLOBAL && activeContract != null) {
            lore.add("");
            lore.add(messages.plain("gui.lore.progress", Map.of(
                "%progress%", Integer.toString(contractService.inventoryProgress(player, offer)),
                "%goal%", Integer.toString(offer.totalRequiredAmount())
            )));
        } else if (offer.scope() == ContractScope.GLOBAL && acceptedBefore) {
            lore.add("");
            lore.add("&7This offer is already closed for you.");
        }
        return ItemUtil.menuItem(offer.iconMaterial(), offer.title(), lore, activeContract != null || offer.totalDeliveredAmount() > 0);
    }

    private ItemStack buildInfoItem(ContractOffer offer) {
        List<String> lore = new ArrayList<>();
        if (offer.scope() != ContractScope.GLOBAL) {
            lore.add("&7Linked board: &f" + (offer.boardId() == null ? "-" : offer.boardId()));
            ConstructionSite site = offer.site();
            if (site != null) {
                lore.add("&7Site anchor: &f" + site.anchorX() + ", " + site.anchorY() + ", " + site.anchorZ());
            }
            if (offer.type() == ContractType.CONSTRUCTION) {
                if (offer.metadata().isRoadProject()) {
                    lore.add("&7Road tiles: &f" + offer.metadata().roadTiles());
                    lore.add("&7Surface: &f" + TextUtil.prettyToken(offer.metadata().surface().name()));
                }
                if (offer.metadata().isBuildingProject()) {
                    lore.add("&7Building: &f" + offer.metadata().building());
                    lore.add("&7Floors: &f" + offer.metadata().floorCount());
                }
                if (!offer.metadata().recommendedTool().isBlank()) {
                    lore.add("&7Recommended tool: &f" + TextUtil.prettyToken(offer.metadata().recommendedTool()));
                }
            }
            lore.add("&7Turn-in and validation happen at the linked physical board.");
        } else {
            lore.add("&7Global hybrid contract.");
            lore.add("&7Accept here, then submit from inventory.");
        }
        return ItemUtil.menuItem(Material.MAP, "&6Project Info", lore);
    }

    private ItemStack buildRewardItem(ContractOffer offer) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Money: &a" + TextUtil.formatMoney(offer.rewards().money()));
        lore.add("&7Reputation: &b" + offer.rewards().reputation());
        if (offer.rewards().hasItemRewards()) {
            for (RewardItem rewardItem : offer.rewards().itemRewards()) {
                lore.add("&7Item: &f" + rewardItem.amount() + "x " + TextUtil.prettyToken(rewardItem.material().name()));
            }
        }
        if (offer.rewards().hasCommandRewards()) {
            lore.add("&7Includes command rewards");
        }
        return ItemUtil.menuItem(Material.EMERALD, "&6Rewards", lore);
    }

    private ItemStack buildRequirementsItem(ContractOffer offer) {
        return ItemUtil.menuItem(Material.CHEST, "&6Requirements", buildRequirementLines(offer));
    }

    private ItemStack buildActionItem(Player player, ContractOffer offer, PlayerContract activeContract) {
        boolean ready = contractService.inventoryProgress(player, offer) >= offer.totalRequiredAmount();
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
            "%progress%", Integer.toString(contractService.inventoryProgress(player, offer)),
            "%goal%", Integer.toString(offer.totalRequiredAmount())
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

    private List<String> buildCompactProgressLore(ContractOffer offer) {
        if (offer.type() == ContractType.CONSTRUCTION) {
            return List.of(
                "&7Requirements: &f" + offer.completedRequirementCount() + "/" + offer.totalRequirementCount(),
                "&7Delivered: &f" + offer.totalDeliveredAmount() + "/" + offer.totalRequiredAmount()
            );
        }
        return List.of("&7Progress: &f" + offer.deliveredAmount() + "/" + offer.requiredAmount());
    }

    private List<String> buildRequirementLines(ContractOffer offer) {
        List<String> lore = new ArrayList<>();
        for (ContractRequirement requirement : offer.requirements()) {
            lore.add("&7- &f" + TextUtil.prettyToken(requirement.material().name())
                + " &8" + requirement.deliveredAmount() + "/" + requirement.amount());
        }
        return lore;
    }

    private List<String> buildBoardFlowLore(ContractOffer offer) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Linked board: &f" + (offer.boardId() == null ? "-" : offer.boardId()));
        lore.add("&7Bring required resources to that board.");
        if (offer.type() == ContractType.CONSTRUCTION) {
            lore.add("&7Construction progress is shared publicly.");
            if (offer.metadata().isRoadProject()) {
                lore.add("&7Road completion also requires the site surface to match.");
            }
        }
        return lore;
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
