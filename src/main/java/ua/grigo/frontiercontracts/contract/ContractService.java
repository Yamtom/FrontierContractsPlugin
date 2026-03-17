package ua.grigo.frontiercontracts.contract;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import ua.grigo.frontiercontracts.FrontierContractsPlugin;
import ua.grigo.frontiercontracts.board.BoardSignService;
import ua.grigo.frontiercontracts.board.BoardService;
import ua.grigo.frontiercontracts.config.ContractTemplateLoader;
import ua.grigo.frontiercontracts.config.PluginSettings;
import ua.grigo.frontiercontracts.hook.VaultHook;
import ua.grigo.frontiercontracts.model.Board;
import ua.grigo.frontiercontracts.model.ContractOffer;
import ua.grigo.frontiercontracts.model.ContractScope;
import ua.grigo.frontiercontracts.model.ContractTemplate;
import ua.grigo.frontiercontracts.model.ContractType;
import ua.grigo.frontiercontracts.model.PlayerContract;
import ua.grigo.frontiercontracts.model.PlayerContractStatus;
import ua.grigo.frontiercontracts.model.PlayerStats;
import ua.grigo.frontiercontracts.model.RewardBundle;
import ua.grigo.frontiercontracts.model.RewardItem;
import ua.grigo.frontiercontracts.model.SettlementReputation;
import ua.grigo.frontiercontracts.storage.StorageService;
import ua.grigo.frontiercontracts.util.ItemUtil;
import ua.grigo.frontiercontracts.util.MessageService;
import ua.grigo.frontiercontracts.util.TextUtil;

public final class ContractService {

    private final FrontierContractsPlugin plugin;
    private final StorageService storage;
    private final VaultHook vaultHook;
    private final MessageService messages;
    private final BoardService boardService;
    private final BoardSignService boardSignService;

    private PluginSettings settings;
    private List<ContractTemplate> templates = List.of();

    private final Map<String, Map<String, ContractOffer>> offersByBoard = new HashMap<>();
    private final Map<String, ContractOffer> globalOffers = new LinkedHashMap<>();
    private final Map<UUID, Map<String, PlayerContract>> openContracts = new HashMap<>();
    private final Map<UUID, PlayerStats> stats = new HashMap<>();
    private final Map<String, SettlementReputation> settlementRep = new HashMap<>();

    private boolean economyWarningShown;

    public ContractService(
        FrontierContractsPlugin plugin,
        StorageService storage,
        VaultHook vaultHook,
        MessageService messages,
        BoardService boardService,
        BoardSignService boardSignService,
        PluginSettings settings
    ) {
        this.plugin = plugin;
        this.storage = storage;
        this.vaultHook = vaultHook;
        this.messages = messages;
        this.boardService = boardService;
        this.boardSignService = boardSignService;
        this.settings = settings;
    }

    public void reload(FileConfiguration contractsConfig, PluginSettings settings) throws SQLException {
        this.settings = settings;
        this.templates = ContractTemplateLoader.load(contractsConfig, settings);
        offersByBoard.clear();
        globalOffers.clear();
        openContracts.clear();
        stats.clear();
        settlementRep.clear();

        for (ContractOffer offer : storage.loadOffers().values()) {
            placeOffer(offer);
        }
        openContracts.putAll(storage.loadOpenContracts());
        stats.putAll(storage.loadStats());
        settlementRep.putAll(storage.loadSettlementReputation());

        cleanupExpiredContent();
        ensureAllBoardPools();
    }

    public void cleanupExpiredContent() {
        long now = now();

        for (Map<String, ContractOffer> pool : offersByBoard.values()) {
            for (ContractOffer offer : pool.values()) {
                if (offer.active() && offer.isExpired(now)) {
                    offer.setActive(false);
                    persistOffer(offer);
                }
            }
            pool.entrySet().removeIf(entry -> !entry.getValue().active());
        }

        for (ContractOffer offer : globalOffers.values()) {
            if (offer.active() && offer.isExpired(now)) {
                offer.setActive(false);
                persistOffer(offer);
            }
        }
        globalOffers.entrySet().removeIf(entry -> !entry.getValue().active());

        List<PlayerContract> toFail = new ArrayList<>();
        for (Map<String, PlayerContract> perPlayer : openContracts.values()) {
            for (PlayerContract contract : perPlayer.values()) {
                if (contract.status() == PlayerContractStatus.ACTIVE && contract.isExpired(now)) {
                    contract.setStatus(PlayerContractStatus.FAILED);
                    contract.setCompletedAtEpochSeconds(now);
                    toFail.add(contract);
                }
            }
        }

        for (PlayerContract contract : toFail) {
            persistPlayerContract(contract);
            Map<String, PlayerContract> perPlayer = openContracts.get(contract.playerUuid());
            if (perPlayer != null) {
                perPlayer.remove(contract.offerId());
                if (perPlayer.isEmpty()) {
                    openContracts.remove(contract.playerUuid());
                }
            }

            applyReputationDelta(contract.playerUuid(), contract.boardId(), settings.reputationOnFail());
            PlayerStats playerStats = getStats(contract.playerUuid());
            playerStats.incrementFailedContracts();
            persistStats(playerStats);

            Player online = Bukkit.getPlayer(contract.playerUuid());
            if (online != null) {
                ContractOffer offer = findOffer(contract.offerId());
                if (offer != null) {
                    messages.send(online, "contracts.failed-expired", Map.of("%title%", offer.title()));
                }
            }
        }

        ensureAllBoardPools();
    }

    public void ensureAllBoardPools() {
        long now = now();
        for (Board board : boardService.allBoards()) {
            boardService.revalidateBoard(board, false);
            normalizePhysicalBoard(board);
            if (!board.active() || !board.isValidStructure()) {
                syncBoardDisplay(board);
                continue;
            }
            if (board.nextRefreshAtEpochSeconds() <= now) {
                refreshBoard(board);
            } else {
                ensureBoardPool(board);
                syncBoardDisplay(board);
            }
        }
        ensureGlobalPool();
    }

    public void refreshBoard(Board board) {
        boardService.revalidateBoard(board, false);
        normalizePhysicalBoard(board);
        if (!board.active() || !board.isValidStructure()) {
            syncBoardDisplay(board);
            return;
        }
        Map<String, ContractOffer> pool = offersByBoard.get(board.id());
        if (pool != null) {
            pool.values().forEach(offer -> {
                if (offer.scope() == ContractScope.LOCAL) {
                    if (!offer.active() || offer.deliveredAmount() <= 0) {
                        offer.setActive(false);
                        persistOffer(offer);
                    }
                } else if (!hasAcceptedActiveContract(offer.id())) {
                    offer.setActive(false);
                    persistOffer(offer);
                }
            });
            pool.entrySet().removeIf(entry -> !entry.getValue().active());
        }
        board.setNextRefreshAtEpochSeconds(now() + board.refreshIntervalSeconds());
        boardService.save();
        ensureBoardPool(board);
        syncBoardDisplay(board);
    }

    private void ensureBoardPool(Board board) {
        normalizePhysicalBoard(board);
        trimExtraLocalOffers(board);
        int localCurrent = countActiveOffersForBoard(board.id(), ContractScope.LOCAL);
        while (localCurrent < board.localOfferSlots()) {
            ContractOffer offer = generateOffer(board, ContractScope.LOCAL);
            if (offer == null) {
                break;
            }
            offersByBoard.computeIfAbsent(board.id(), ignored -> new LinkedHashMap<>()).put(offer.id(), offer);
            persistOffer(offer);
            localCurrent++;
        }

        int regionalCurrent = countActiveOffersForBoard(board.id(), ContractScope.REGIONAL);
        while (regionalCurrent < settings.regionalOfferSlots()) {
            ContractOffer offer = generateOffer(board, ContractScope.REGIONAL);
            if (offer == null) {
                break;
            }
            offersByBoard.computeIfAbsent(board.id(), ignored -> new LinkedHashMap<>()).put(offer.id(), offer);
            persistOffer(offer);
            regionalCurrent++;
        }

        if (board.nextRefreshAtEpochSeconds() <= 0L) {
            board.setNextRefreshAtEpochSeconds(now() + board.refreshIntervalSeconds());
            boardService.save();
        }
    }

    private void ensureGlobalPool() {
        long now = now();
        int current = (int) globalOffers.values().stream().filter(ContractOffer::active).count();
        while (current < settings.globalOfferSlots()) {
            ContractOffer offer = generateGlobalOffer(now);
            if (offer == null) {
                break;
            }
            globalOffers.put(offer.id(), offer);
            persistOffer(offer);
            current++;
        }
    }

    private int countActiveOffersForBoard(String boardId, ContractScope scope) {
        Map<String, ContractOffer> pool = offersByBoard.get(boardId);
        if (pool == null) {
            return 0;
        }
        return (int) pool.values().stream()
            .filter(offer -> offer.active() && offer.scope() == scope)
            .count();
    }

    private ContractOffer generateOffer(Board board, ContractScope scope) {
        long now = now();
        List<ContractTemplate> pool = eligibleTemplates(board, scope);
        if (pool.isEmpty()) {
            return null;
        }

        List<String> activeKeys = offersByBoard.getOrDefault(board.id(), Map.of()).values().stream()
            .filter(offer -> offer.active() && offer.scope() == scope)
            .map(ContractOffer::templateKey)
            .toList();

        List<ContractTemplate> preferred = pool.stream()
            .filter(template -> !activeKeys.contains(template.key()))
            .toList();
        List<ContractTemplate> selection = preferred.isEmpty() ? pool : preferred;
        ContractTemplate template = weightedPick(selection, board);
        if (template == null) {
            return null;
        }
        return template.createOffer(
            board.id(),
            board.difficultyModifier(),
            board.rewardModifier(),
            board.reputationModifier(),
            now,
            settings
        );
    }

    private ContractOffer generateGlobalOffer(long now) {
        List<ContractTemplate> globalTemplates = templates.stream()
            .filter(template -> template.scope() == ContractScope.GLOBAL)
            .toList();
        ContractTemplate template = weightedPickFlat(globalTemplates);
        if (template == null) {
            return null;
        }
        return template.createOffer(null, 1.0D, 1.0D, 1.0D, now, settings);
    }

    private List<ContractTemplate> eligibleTemplates(Board board, ContractScope scope) {
        return templates.stream()
            .filter(template -> template.scope() == scope)
            .filter(template -> template.isAvailableToBoard(board.contractPools()))
            .toList();
    }

    private ContractTemplate weightedPick(List<ContractTemplate> pool, Board board) {
        double totalWeight = 0.0D;
        for (ContractTemplate template : pool) {
            totalWeight += template.weight() * board.categoryWeight(template.type());
        }
        if (totalWeight <= 0.0D) {
            return null;
        }
        double roll = Math.random() * totalWeight;
        double cumulative = 0.0D;
        for (ContractTemplate template : pool) {
            cumulative += template.weight() * board.categoryWeight(template.type());
            if (roll < cumulative) {
                return template;
            }
        }
        return pool.getFirst();
    }

    private ContractTemplate weightedPickFlat(List<ContractTemplate> pool) {
        int total = pool.stream().mapToInt(ContractTemplate::weight).sum();
        if (total <= 0) {
            return null;
        }
        int roll = (int) (Math.random() * total);
        int cumulative = 0;
        for (ContractTemplate template : pool) {
            cumulative += template.weight();
            if (roll < cumulative) {
                return template;
            }
        }
        return pool.getFirst();
    }

    public List<ContractOffer> getLocalOffers(String boardId) {
        return offersByBoard.getOrDefault(boardId, Map.of()).values().stream()
            .filter(offer -> offer.active() && offer.scope() == ContractScope.LOCAL)
            .sorted(Comparator.comparingLong(ContractOffer::createdAtEpochSeconds))
            .toList();
    }

    public List<ContractOffer> getRegionalOffers(String boardId) {
        return offersByBoard.getOrDefault(boardId, Map.of()).values().stream()
            .filter(offer -> offer.active() && offer.scope() == ContractScope.REGIONAL)
            .sorted(Comparator.comparingLong(ContractOffer::createdAtEpochSeconds))
            .toList();
    }

    public List<ContractOffer> getGlobalOffers() {
        return globalOffers.values().stream()
            .filter(ContractOffer::active)
            .sorted(Comparator.comparingLong(ContractOffer::createdAtEpochSeconds))
            .limit(settings.globalOfferSlots())
            .toList();
    }

    public Optional<ContractOffer> getOffer(String offerId) {
        return Optional.ofNullable(findOffer(offerId));
    }

    public Optional<PlayerContract> getOpenContract(UUID playerUuid, String offerId) {
        return Optional.ofNullable(openContracts.getOrDefault(playerUuid, Map.of()).get(offerId));
    }

    public List<PlayerContract> getOpenContracts(UUID playerUuid) {
        return openContracts.getOrDefault(playerUuid, Map.of()).values().stream()
            .sorted(Comparator.comparingLong(PlayerContract::expiresAtEpochSeconds))
            .toList();
    }

    public boolean hasAcceptedOffer(UUID playerUuid, String offerId) {
        return storage.hasPlayerAcceptedOffer(playerUuid, offerId);
    }

    public int countActiveContracts(UUID playerUuid) {
        return openContracts.getOrDefault(playerUuid, Map.of()).size();
    }

    public String smallestRemainingTime(UUID playerUuid) {
        return openContracts.getOrDefault(playerUuid, Map.of()).values().stream()
            .mapToLong(contract -> contract.remainingSeconds(now()))
            .min()
            .stream()
            .mapToObj(TextUtil::formatDuration)
            .findFirst()
            .orElse("-");
    }

    public ActionResult accept(Player player, String offerId) {
        if (!canProgress(player)) {
            return ActionResult.failure("errors.creative-mode");
        }

        ContractOffer offer = findOffer(offerId);
        if (offer == null || !offer.active() || offer.isBoardLocal()) {
            return ActionResult.failure("errors.unavailable-offer");
        }
        if (hasAcceptedOffer(player.getUniqueId(), offerId)) {
            return ActionResult.failure("errors.already-accepted");
        }

        long now = now();
        PlayerContract contract = new PlayerContract(
            UUID.randomUUID().toString(),
            offer.id(),
            offer.boardId(),
            player.getUniqueId(),
            0,
            now,
            now + offer.contractDurationSeconds(),
            true,
            PlayerContractStatus.ACTIVE,
            null
        );
        openContracts.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).put(offer.id(), contract);
        persistPlayerContract(contract);

        return ActionResult.success("contracts.accepted", Map.of(
            "%title%", offer.title(),
            "%time_left%", TextUtil.formatDuration(contract.remainingSeconds(now))
        ));
    }

    public ActionResult submitSpecific(Player player, String offerId) {
        if (!canProgress(player)) {
            return ActionResult.failure("errors.creative-mode");
        }
        PlayerContract contract = getOpenContract(player.getUniqueId(), offerId).orElse(null);
        ContractOffer offer = findOffer(offerId);
        if (contract == null || offer == null) {
            return ActionResult.failure("errors.invalid-submit");
        }
        if (submitInternal(player, contract, offer)) {
            return ActionResult.success("", Map.of());
        }
        return ActionResult.failure(failureMessageFor(offer, player));
    }

    public int submitAllEligible(Player player) {
        if (!canProgress(player)) {
            return 0;
        }
        int claimed = 0;
        for (PlayerContract contract : new ArrayList<>(getOpenContracts(player.getUniqueId()))) {
            ContractOffer offer = findOffer(contract.offerId());
            if (offer != null && submitInternal(player, contract, offer)) {
                claimed++;
            }
        }
        return claimed;
    }

    public ActionResult deliverToBoard(Player player, Board board, EquipmentSlot hand) {
        if (!canProgress(player)) {
            return ActionResult.failure("errors.creative-mode");
        }

        ItemStack heldItem = ItemUtil.getHeldItem(player.getInventory(), hand);
        if (heldItem == null || heldItem.getType() == Material.AIR) {
            return ActionResult.failure("errors.no-matching-resource");
        }

        ContractOffer offer = selectBoardDeliveryOffer(board.id(), player.getUniqueId(), heldItem.getType());
        if (offer == null) {
            return ActionResult.failure("errors.no-matching-resource");
        }

        int remaining = offer.remainingAmount();
        int available = heldItem.getAmount();
        if (!offer.partialDeliveryAllowed() && available < remaining) {
            return ActionResult.failure("errors.full-delivery-required");
        }

        int delivered = Math.min(available, remaining);
        if (delivered <= 0) {
            return ActionResult.failure("errors.no-matching-resource");
        }

        ItemUtil.removeFromHand(player.getInventory(), hand, delivered);
        offer.setDeliveredAmount(offer.deliveredAmount() + delivered);
        persistOffer(offer);
        syncBoardDisplay(board);

        messages.send(player, "contracts.progress-delivery", Map.of(
            "%title%", offer.title(),
            "%progress%", Integer.toString(offer.deliveredAmount()),
            "%goal%", Integer.toString(offer.requiredAmount()),
            "%resource%", TextUtil.prettyToken(offer.requiredMaterial().name())
        ));

        if (offer.isComplete()) {
            completeBoardOffer(player, board, offer);
            return ActionResult.success("", Map.of());
        }

        return ActionResult.success("", Map.of());
    }

    public ActionResult abandon(Player player, String offerId) {
        PlayerContract contract = getOpenContract(player.getUniqueId(), offerId).orElse(null);
        if (contract == null) {
            return ActionResult.failure("errors.no-such-contract");
        }
        ContractOffer offer = findOffer(offerId);
        contract.setStatus(PlayerContractStatus.FAILED);
        contract.setCompletedAtEpochSeconds(now());
        persistPlayerContract(contract);

        Map<String, PlayerContract> perPlayer = openContracts.get(player.getUniqueId());
        if (perPlayer != null) {
            perPlayer.remove(offerId);
            if (perPlayer.isEmpty()) {
                openContracts.remove(player.getUniqueId());
            }
        }

        applyReputationDelta(player.getUniqueId(), contract.boardId(), settings.reputationOnAbandon());
        PlayerStats playerStats = getStats(player.getUniqueId());
        playerStats.incrementFailedContracts();
        persistStats(playerStats);

        String title = offer != null ? offer.title() : offerId;
        return ActionResult.success("contracts.abandoned", Map.of(
            "%title%", title,
            "%reputation_delta%", Integer.toString(settings.reputationOnAbandon())
        ));
    }

    public void applyReputationDelta(UUID playerUuid, String boardId, int delta) {
        if (!settings.reputationEnabled() || boardId == null || delta == 0) {
            return;
        }
        String key = boardId + ":" + playerUuid;
        SettlementReputation reputation = settlementRep.computeIfAbsent(
            key,
            ignored -> new SettlementReputation(boardId, playerUuid, 0, now())
        );
        reputation.add(delta);
        try {
            storage.saveSettlementReputation(reputation);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not save settlement reputation: " + exception.getMessage());
        }
    }

    public int getSettlementReputation(UUID playerUuid, String boardId) {
        SettlementReputation reputation = settlementRep.get(boardId + ":" + playerUuid);
        return reputation == null ? 0 : reputation.reputation();
    }

    public void markDeath(UUID playerUuid) {
        Map<String, PlayerContract> contracts = openContracts.get(playerUuid);
        if (contracts == null) {
            return;
        }
        for (PlayerContract contract : contracts.values()) {
            if (contract.deathless()) {
                contract.setDeathless(false);
                persistPlayerContract(contract);
            }
        }
    }

    public PlayerStats getStats(UUID playerUuid) {
        return stats.computeIfAbsent(playerUuid, uuid -> new PlayerStats(uuid, 0, 0, 0));
    }

    public String objectiveLabel(ContractOffer offer) {
        return offer.requiredAmount() + "x " + TextUtil.prettyToken(offer.requiredMaterial().name());
    }

    public int boardProgress(ContractOffer offer) {
        return offer.deliveredAmount();
    }

    public void regenerateOffers() {
        try {
            storage.deactivatePendingOffers();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not deactivate pending offers: " + exception.getMessage());
        }
        offersByBoard.values().forEach(pool -> pool.values().removeIf(offer ->
            offer.scope() != ContractScope.LOCAL && !hasAcceptedActiveContract(offer.id())));
        globalOffers.values().removeIf(offer -> !hasAcceptedActiveContract(offer.id()));
        ensureAllBoardPools();
    }

    public void syncBoardDisplay(Board board) {
        boardSignService.syncBoard(board, getLocalOffers(board.id()));
    }

    private boolean submitInternal(Player player, PlayerContract contract, ContractOffer offer) {
        if (!canProgress(player) || offer.type() != ContractType.DELIVERY) {
            return false;
        }
        if (!ItemUtil.removeMaterial(player.getInventory(), offer.requiredMaterial(), offer.requiredAmount())) {
            return false;
        }
        contract.setProgress(offer.requiredAmount());
        rewardAcceptedContract(player, contract, offer);
        return true;
    }

    private void rewardAcceptedContract(Player player, PlayerContract contract, ContractOffer offer) {
        RewardBundle totalReward = offer.rewards().combine(offer.bonusReward());
        double payout = totalReward.money();

        if (contract.deathless() && offer.bonusConfig().hasNoDeathBonus()) {
            payout *= offer.bonusConfig().noDeathMoneyMultiplier();
            messages.send(player, "contracts.bonus-applied", Map.of(
                "%bonus_percent%",
                Integer.toString((int) Math.round((offer.bonusConfig().noDeathMoneyMultiplier() - 1.0D) * 100.0D))
            ));
        }

        RewardBundle paidReward = new RewardBundle(payout, totalReward.reputation(), totalReward.itemRewards(), totalReward.commandRewards());
        applyRewardBundle(player, paidReward, offer, contract.boardId());

        contract.setStatus(PlayerContractStatus.CLAIMED);
        contract.setCompletedAtEpochSeconds(now());
        persistPlayerContract(contract);

        Map<String, PlayerContract> perPlayer = openContracts.get(player.getUniqueId());
        if (perPlayer != null) {
            perPlayer.remove(offer.id());
            if (perPlayer.isEmpty()) {
                openContracts.remove(player.getUniqueId());
            }
        }

        PlayerStats playerStats = getStats(player.getUniqueId());
        playerStats.incrementCompletedContracts();
        if (paidReward.reputation() > 0) {
            playerStats.addReputation(paidReward.reputation());
        }
        persistStats(playerStats);
        messages.send(player, "contracts.completed", Map.of("%title%", offer.title()));
    }

    private void completeBoardOffer(Player player, Board board, ContractOffer offer) {
        offer.setActive(false);
        persistOffer(offer);

        RewardBundle totalReward = offer.rewards().combine(offer.bonusReward());
        applyRewardBundle(player, totalReward, offer, board.id());

        PlayerStats playerStats = getStats(player.getUniqueId());
        playerStats.incrementCompletedContracts();
        if (totalReward.reputation() > 0) {
            playerStats.addReputation(totalReward.reputation());
        }
        persistStats(playerStats);

        messages.send(player, "contracts.completed", Map.of("%title%", offer.title()));
        messages.send(player, "contracts.complete-summary", Map.of(
            "%title%", offer.title(),
            "%money%", TextUtil.formatMoney(totalReward.money())
        ));

        board.setNextRefreshAtEpochSeconds(now() + board.refreshIntervalSeconds());
        boardService.save();
        ensureBoardPool(board);
        syncBoardDisplay(board);
    }

    private void applyRewardBundle(Player player, RewardBundle reward, ContractOffer offer, String boardId) {
        if (reward.money() > 0.0D) {
            if (vaultHook.hasEconomy()) {
                vaultHook.deposit(player, reward.money());
                messages.send(player, "contracts.reward-money", Map.of("%money%", TextUtil.formatMoney(reward.money())));
            } else if (!economyWarningShown && settings.warnMissingEconomyProvider()) {
                economyWarningShown = true;
                plugin.getLogger().warning("Vault economy provider missing. Money rewards skipped.");
                messages.send(player, "errors.economy-missing");
            }
        }

        if (reward.reputation() > 0 && boardId != null) {
            applyReputationDelta(player.getUniqueId(), boardId, reward.reputation());
            messages.send(player, "contracts.reward-reputation", Map.of("%reputation%", Integer.toString(reward.reputation())));
        }

        for (RewardItem itemReward : reward.itemRewards()) {
            if (!itemReward.isValid()) {
                continue;
            }
            ItemUtil.giveOrDrop(player, new ItemStack(itemReward.material(), itemReward.amount()));
            messages.send(player, "contracts.reward-item", Map.of(
                "%amount%", Integer.toString(itemReward.amount()),
                "%item%", TextUtil.prettyToken(itemReward.material().name())
            ));
        }

        ConsoleCommandSender console = Bukkit.getConsoleSender();
        for (String rawCommand : reward.commandRewards()) {
            if (rawCommand == null || rawCommand.isBlank()) {
                continue;
            }
            String command = rawCommand
                .replace("%player%", player.getName())
                .replace("%board%", offer.boardId() == null ? "" : offer.boardId())
                .replace("%settlement%", boardId == null ? "" : boardId);
            Bukkit.dispatchCommand(console, command);
            messages.send(player, "contracts.reward-command", Map.of("%command%", command));
        }
    }

    private ContractOffer selectBoardDeliveryOffer(String boardId, UUID playerUuid, Material material) {
        return offersByBoard.getOrDefault(boardId, Map.of()).values().stream()
            .filter(ContractOffer::active)
            .filter(ContractOffer::isBoardLocal)
            .filter(offer -> offer.acceptsMaterial(material))
            .filter(offer -> offer.publicOffer() || offer.isPersonalFor(playerUuid))
            .sorted(Comparator
                .comparing((ContractOffer offer) -> !offer.isPersonalFor(playerUuid))
                .thenComparingLong(ContractOffer::createdAtEpochSeconds)
                .thenComparingInt(ContractOffer::remainingAmount))
            .findFirst()
            .orElse(null);
    }

    private void trimExtraLocalOffers(Board board) {
        Map<String, ContractOffer> pool = offersByBoard.get(board.id());
        if (pool == null) {
            return;
        }

        List<ContractOffer> localOffers = pool.values().stream()
            .filter(ContractOffer::active)
            .filter(ContractOffer::isBoardLocal)
            .sorted(Comparator.comparingLong(ContractOffer::createdAtEpochSeconds))
            .toList();

        if (localOffers.size() <= BoardService.PHYSICAL_TASK_SIGN_SLOTS) {
            return;
        }

        for (int index = BoardService.PHYSICAL_TASK_SIGN_SLOTS; index < localOffers.size(); index++) {
            ContractOffer offer = localOffers.get(index);
            offer.setActive(false);
            persistOffer(offer);
        }
        pool.entrySet().removeIf(entry -> !entry.getValue().active());
    }

    private void normalizePhysicalBoard(Board board) {
        if (board.localOfferSlots() != BoardService.PHYSICAL_TASK_SIGN_SLOTS) {
            board.setLocalOfferSlots(BoardService.PHYSICAL_TASK_SIGN_SLOTS);
            boardService.save();
        }
    }

    private ContractOffer findOffer(String offerId) {
        ContractOffer global = globalOffers.get(offerId);
        if (global != null) {
            return global;
        }
        for (Map<String, ContractOffer> pool : offersByBoard.values()) {
            ContractOffer offer = pool.get(offerId);
            if (offer != null) {
                return offer;
            }
        }
        return null;
    }

    private void placeOffer(ContractOffer offer) {
        if (offer.scope() == ContractScope.GLOBAL && offer.boardId() == null) {
            globalOffers.put(offer.id(), offer);
        } else if (offer.boardId() != null) {
            offersByBoard.computeIfAbsent(offer.boardId(), ignored -> new LinkedHashMap<>()).put(offer.id(), offer);
        }
    }

    private boolean hasAcceptedActiveContract(String offerId) {
        for (Map<String, PlayerContract> perPlayer : openContracts.values()) {
            if (perPlayer.containsKey(offerId)) {
                return true;
            }
        }
        return false;
    }

    private boolean canProgress(Player player) {
        if (!settings.blockCreativeSubmit()) {
            return true;
        }
        return player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR;
    }

    private String failureMessageFor(ContractOffer offer, Player player) {
        return ItemUtil.countMaterial(player.getInventory(), offer.requiredMaterial()) < offer.requiredAmount()
            ? "errors.not-enough-items"
            : "errors.not-ready";
    }

    private long now() {
        return System.currentTimeMillis() / 1000L;
    }

    private void persistOffer(ContractOffer offer) {
        try {
            storage.saveOffer(offer);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not save offer: " + exception.getMessage());
        }
    }

    private void persistPlayerContract(PlayerContract contract) {
        try {
            storage.savePlayerContract(contract);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not save contract: " + exception.getMessage());
        }
    }

    private void persistStats(PlayerStats playerStats) {
        try {
            storage.saveStats(playerStats);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not save stats: " + exception.getMessage());
        }
    }
}
