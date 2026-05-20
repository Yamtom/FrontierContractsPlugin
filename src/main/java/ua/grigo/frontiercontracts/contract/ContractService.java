package ua.grigo.frontiercontracts.contract;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import ua.grigo.frontiercontracts.FrontierContractsPlugin;
import ua.grigo.frontiercontracts.board.BoardService;
import ua.grigo.frontiercontracts.board.BoardSignService;
import ua.grigo.frontiercontracts.config.ContractTemplateLoader;
import ua.grigo.frontiercontracts.config.PluginSettings;
import ua.grigo.frontiercontracts.model.Board;
import ua.grigo.frontiercontracts.model.ConstructionMetadata;
import ua.grigo.frontiercontracts.model.ConstructionRules;
import ua.grigo.frontiercontracts.model.ConstructionSite;
import ua.grigo.frontiercontracts.model.AntiFrustrationSettings;
import ua.grigo.frontiercontracts.model.ContractCatalog;
import ua.grigo.frontiercontracts.model.ContractOffer;
import ua.grigo.frontiercontracts.model.ContractRank;
import ua.grigo.frontiercontracts.model.ContractRequirement;
import ua.grigo.frontiercontracts.model.ContractScope;
import ua.grigo.frontiercontracts.model.ContractTemplate;
import ua.grigo.frontiercontracts.model.ContractType;
import ua.grigo.frontiercontracts.model.PlayerContract;
import ua.grigo.frontiercontracts.model.PlayerContractStatus;
import ua.grigo.frontiercontracts.model.PlayerStats;
import ua.grigo.frontiercontracts.model.ProgressionSettings;
import ua.grigo.frontiercontracts.model.ProjectGenerationSettings;
import ua.grigo.frontiercontracts.model.RewardBundle;
import ua.grigo.frontiercontracts.model.RewardItem;
import ua.grigo.frontiercontracts.model.SettlementProgress;
import ua.grigo.frontiercontracts.model.SettlementReputation;
import ua.grigo.frontiercontracts.storage.StorageService;
import ua.grigo.frontiercontracts.util.ItemUtil;
import ua.grigo.frontiercontracts.util.MessageService;
import ua.grigo.frontiercontracts.util.TextUtil;

public final class ContractService {
    private final FrontierContractsPlugin plugin;
    private final StorageService storage;
    private final MessageService messages;
    private final BoardService boardService;
    private final BoardSignService boardSignService;
    private final ConstructionSitePlanner sitePlanner;

    private PluginSettings settings;
    private ContractCatalog catalog = new ContractCatalog(Map.of(), null, null, null, null, List.of(), List.of());

    private final Map<String, Map<String, ContractOffer>> offersByBoard = new HashMap<>();
    private final Map<String, ContractOffer> globalOffers = new LinkedHashMap<>();
    private final Map<UUID, Map<String, PlayerContract>> openContracts = new HashMap<>();
    private final Map<UUID, PlayerStats> stats = new HashMap<>();
    private final Map<String, SettlementReputation> settlementRep = new HashMap<>();
    private final Map<String, SettlementProgress> settlementProgress = new HashMap<>();

    public ContractService(
        FrontierContractsPlugin plugin,
        StorageService storage,
        MessageService messages,
        BoardService boardService,
        BoardSignService boardSignService,
        PluginSettings settings
    ) {
        this.plugin = plugin;
        this.storage = storage;
        this.messages = messages;
        this.boardService = boardService;
        this.boardSignService = boardSignService;
        this.sitePlanner = new ConstructionSitePlanner(plugin.getServer());
        this.settings = settings;
    }

    public void reload(FileConfiguration contractsConfig, PluginSettings settings) throws SQLException {
        this.settings = settings;
        this.catalog = ContractTemplateLoader.load(contractsConfig, settings);
        offersByBoard.clear();
        globalOffers.clear();
        openContracts.clear();
        stats.clear();
        settlementRep.clear();
        settlementProgress.clear();

        for (ContractOffer offer : storage.loadOffers().values()) {
            placeOffer(offer);
        }
        openContracts.putAll(storage.loadOpenContracts());
        stats.putAll(storage.loadStats());
        settlementRep.putAll(storage.loadSettlementReputation());
        settlementProgress.putAll(storage.loadSettlementProgress());

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
            ContractOffer failedOffer = findOffer(contract.offerId());
            persistPlayerContract(contract);
            Map<String, PlayerContract> perPlayer = openContracts.get(contract.playerUuid());
            if (perPlayer != null) {
                perPlayer.remove(contract.offerId());
                if (perPlayer.isEmpty()) {
                    openContracts.remove(contract.playerUuid());
                }
            }

            if (failedOffer != null && failedOffer.scope() != ContractScope.GLOBAL) {
                failedOffer.decrementActivePlayers();
                persistOffer(failedOffer);
            }

            applyReputationDelta(contract.playerUuid(), contract.boardId(), settings.reputationOnFail());
            PlayerStats playerStats = getStats(contract.playerUuid());
            playerStats.incrementFailedContracts();
            persistStats(playerStats);

            Player online = Bukkit.getPlayer(contract.playerUuid());
            if (online != null && failedOffer != null) {
                messages.send(online, "contracts.failed-expired", Map.of("%title%", failedOffer.title()));
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
                boolean keep = offer.scope() != ContractScope.GLOBAL
                    ? offer.totalDeliveredAmount() > 0
                    : hasAcceptedActiveContract(offer.id());
                if (!keep) {
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
        SettlementProgress progress = getSettlementProgress(board.id());
        int rankedLocalCurrent = countActiveOffersForBoard(board.id(), ContractScope.LOCAL, false);
        while (rankedLocalCurrent < 4) {
            ContractOffer offer = generateRankedOffer(board, ContractScope.LOCAL, localRankProfile(rankedLocalCurrent), false, progress);
            if (offer == null) {
                break;
            }
            registerGeneratedOffer(board, offer, progress);
            rankedLocalCurrent++;
        }

        int localSpecialCurrent = countActiveOffersForBoard(board.id(), ContractScope.LOCAL, true);
        while (localSpecialCurrent < 1) {
            ContractOffer offer = generateSpecialOffer(board, ContractScope.LOCAL, progress);
            if (offer == null) {
                break;
            }
            registerGeneratedOffer(board, offer, progress);
            localSpecialCurrent++;
        }

        int regionalCurrent = countActiveOffersForBoard(board.id(), ContractScope.REGIONAL);
        while (regionalCurrent < settings.regionalOfferSlots()) {
            ContractOffer offer = generateRegionalOffer(board, progress, regionalCurrent == 0);
            if (offer == null) {
                break;
            }
            registerGeneratedOffer(board, offer, progress);
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

    private ContractOffer generateGlobalOffer(long now) {
        ContractTemplate template = chooseRankedTemplate(
            null,
            ContractScope.GLOBAL,
            EnumSet.allOf(ContractRank.class),
            null,
            false
        );
        if (template == null) {
            return null;
        }
        return template.createOffer(null, 1.0D, 1.0D, 1.0D, now, settings, null, false);
    }

    private int countActiveOffersForBoard(String boardId, ContractScope scope, boolean specialOffer) {
        Map<String, ContractOffer> pool = offersByBoard.get(boardId);
        if (pool == null) {
            return 0;
        }
        return (int) pool.values().stream()
            .filter(offer -> offer.active() && offer.scope() == scope && offer.specialOffer() == specialOffer)
            .count();
    }

    private void registerGeneratedOffer(Board board, ContractOffer offer, SettlementProgress progress) {
        if (board != null && offer.isBoardBackedShared()) {
            offersByBoard.computeIfAbsent(board.id(), ignored -> new LinkedHashMap<>()).put(offer.id(), offer);
            if (!offer.isProjectOffer()) {
                progress.recordRankedGeneration(offer.rank(), offer.templateKey(), antiFrustration());
                progress.setUpdatedAt(now());
                persistSettlementProgress(progress);
            } else {
                progress.rememberVariant(offer.templateKey(), antiFrustration().recentVariantMemory());
                progress.setUpdatedAt(now());
                persistSettlementProgress(progress);
            }
        } else {
            globalOffers.put(offer.id(), offer);
        }
        persistOffer(offer);
    }

    private ContractOffer generateRegionalOffer(Board board, SettlementProgress progress, boolean preferProject) {
        if (preferProject) {
            ContractOffer project = generateProjectOffer(board, ContractScope.REGIONAL, progress, true);
            if (project != null) {
                return project;
            }
        }
        return generateRankedOffer(board, ContractScope.REGIONAL, EnumSet.allOf(ContractRank.class), false, progress);
    }

    private ContractOffer generateSpecialOffer(Board board, ContractScope scope, SettlementProgress progress) {
        if (projectSettings().preferProjectInSpecialSlot()) {
            ContractOffer project = generateProjectOffer(board, scope, progress, true);
            if (project != null) {
                return project;
            }
        }
        ContractOffer ranked = generateRankedOffer(board, scope, EnumSet.of(ContractRank.B, ContractRank.A, ContractRank.S), true, progress);
        if (ranked != null) {
            return ranked;
        }
        return generateRankedOffer(board, scope, EnumSet.allOf(ContractRank.class), true, progress);
    }

    private ContractOffer generateProjectOffer(Board board, ContractScope scope, SettlementProgress progress, boolean specialOffer) {
        long now = now();
        List<ContractTemplate> candidates = catalog.projectTemplates(scope).stream()
            .filter(template -> board == null || template.isAvailableToBoard(board.contractPools()))
            .filter(template -> board == null || isProjectEligible(template, progress, now))
            .filter(template -> board == null || !isTemplateActive(board.id(), scope, template.key()))
            .toList();
        if (candidates.isEmpty()) {
            return null;
        }

        List<ContractTemplate> preferred = board == null ? candidates : candidates.stream()
            .filter(template -> !progress.hasRecentVariant(template.key()))
            .toList();
        List<ContractTemplate> selection = new ArrayList<>(preferred.isEmpty() ? candidates : preferred);
        while (!selection.isEmpty()) {
            ContractTemplate template = weightedPick(selection, board);
            if (template == null) {
                return null;
            }
            ConstructionSite site = sitePlanner.plan(board, template);
            if (site == null) {
                selection.remove(template);
                continue;
            }
            return template.createOffer(
                board.id(),
                board.difficultyModifier(),
                board.rewardModifier(),
                board.reputationModifier(),
                now,
                settings,
                site,
                specialOffer
            );
        }
        return null;
    }

    private ContractOffer generateRankedOffer(
        Board board,
        ContractScope scope,
        EnumSet<ContractRank> allowedRanks,
        boolean specialOffer,
        SettlementProgress progress
    ) {
        long now = now();
        ContractTemplate template = chooseRankedTemplate(board, scope, allowedRanks, progress, specialOffer);
        if (template == null) {
            return null;
        }
        return template.createOffer(
            board == null ? null : board.id(),
            board == null ? 1.0D : board.difficultyModifier(),
            board == null ? 1.0D : board.rewardModifier(),
            board == null ? 1.0D : board.reputationModifier(),
            now,
            settings,
            null,
            specialOffer
        );
    }

    private ContractTemplate chooseRankedTemplate(
        Board board,
        ContractScope scope,
        EnumSet<ContractRank> allowedRanks,
        SettlementProgress progress,
        boolean specialOffer
    ) {
        List<ContractTemplate> pool = eligibleRankedTemplates(board, scope);
        if (pool.isEmpty()) {
            return null;
        }

        Map<ContractRank, Integer> weights = buildRankWeights(progress, allowedRanks);
        List<ContractRank> rankOrder = weightedRankOrder(weights, allowedRanks);
        for (ContractRank rank : rankOrder) {
            List<ContractTemplate> candidates = pool.stream()
                .filter(template -> template.rank() == rank)
                .filter(template -> board == null || !isTemplateActive(board.id(), scope, template.key()))
                .toList();
            if (candidates.isEmpty()) {
                continue;
            }
            List<ContractTemplate> preferred = (board == null || progress == null) ? candidates : candidates.stream()
                .filter(template -> !progress.hasRecentVariant(template.key()))
                .toList();
            List<ContractTemplate> selection = new ArrayList<>(preferred.isEmpty() ? candidates : preferred);
            ContractTemplate picked = weightedPick(selection, board);
            if (picked != null) {
                return picked;
            }
        }

        if (specialOffer && !allowedRanks.containsAll(EnumSet.allOf(ContractRank.class))) {
            return chooseRankedTemplate(board, scope, EnumSet.allOf(ContractRank.class), progress, false);
        }
        return null;
    }

    private List<ContractTemplate> eligibleRankedTemplates(Board board, ContractScope scope) {
        return catalog.rankedTemplates(scope).stream()
            .filter(template -> board == null || template.isAvailableToBoard(board.contractPools()))
            .toList();
    }

    private boolean isTemplateActive(String boardId, ContractScope scope, String templateKey) {
        return offersByBoard.getOrDefault(boardId, Map.of()).values().stream()
            .filter(ContractOffer::active)
            .filter(offer -> offer.scope() == scope)
            .anyMatch(offer -> offer.templateKey().equals(templateKey));
    }

    private ContractTemplate weightedPick(List<ContractTemplate> pool, Board board) {
        double totalWeight = 0.0D;
        for (ContractTemplate template : pool) {
            totalWeight += template.weight() * (board == null ? 1.0D : board.categoryWeight(template.type()));
        }
        if (totalWeight <= 0.0D) {
            return null;
        }
        double roll = Math.random() * totalWeight;
        double cumulative = 0.0D;
        for (ContractTemplate template : pool) {
            cumulative += template.weight() * (board == null ? 1.0D : board.categoryWeight(template.type()));
            if (roll < cumulative) {
                return template;
            }
        }
        return pool.getFirst();
    }

    public List<ContractOffer> getLocalOffers(String boardId) {
        return offersByBoard.getOrDefault(boardId, Map.of()).values().stream()
            .filter(offer -> offer.active() && offer.scope() == ContractScope.LOCAL)
            .sorted(Comparator
                .comparing(ContractOffer::specialOffer)
                .thenComparingLong(ContractOffer::createdAtEpochSeconds))
            .toList();
    }

    public List<ContractOffer> getRegionalOffers(String boardId) {
        return offersByBoard.getOrDefault(boardId, Map.of()).values().stream()
            .filter(offer -> offer.active() && offer.scope() == ContractScope.REGIONAL)
            .sorted(Comparator
                .comparing((ContractOffer offer) -> !offer.isProjectOffer())
                .thenComparingLong(ContractOffer::createdAtEpochSeconds))
            .toList();
    }

    public List<ContractOffer> getRegionalOffersForDirectory() {
        List<ContractOffer> offers = new ArrayList<>();
        for (Map<String, ContractOffer> pool : offersByBoard.values()) {
            pool.values().stream()
                .filter(offer -> offer.active() && offer.scope() == ContractScope.REGIONAL)
                .forEach(offers::add);
        }
        offers.sort(Comparator
            .comparing((ContractOffer offer) -> !offer.isProjectOffer())
            .thenComparingLong(ContractOffer::createdAtEpochSeconds));
        return offers;
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
        if (offer == null || !offer.active()) {
            return ActionResult.failure("errors.unavailable-offer");
        }
        if (offer.scope() == ContractScope.GLOBAL) {
            if (hasAcceptedOffer(player.getUniqueId(), offerId)) {
                return ActionResult.failure("errors.already-accepted");
            }
        } else {
            if (openContracts.getOrDefault(player.getUniqueId(), Map.of()).containsKey(offerId)) {
                return ActionResult.failure("errors.already-accepted");
            }
            if (!offer.canAccept()) {
                return ActionResult.failure("errors.contract-full");
            }
        }

        long now = now();
        List<ContractRequirement> contractReqs = new ArrayList<>(offer.requirements().stream().map(ContractRequirement::copy).toList());
        PlayerContract contract = new PlayerContract(
            UUID.randomUUID().toString(),
            offer.id(),
            offer.boardId(),
            offer.type(),
            player.getUniqueId(),
            0,
            contractReqs,
            now,
            now + offer.contractDurationSeconds(),
            true,
            PlayerContractStatus.ACTIVE,
            null
        );
        openContracts.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).put(offer.id(), contract);
        persistPlayerContract(contract);

        if (offer.scope() != ContractScope.GLOBAL) {
            offer.incrementActivePlayers();
            persistOffer(offer);
        }

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

        Material material = heldItem.getType();
        PlayerContract matchedContract = findBoardContract(player.getUniqueId(), board.id(), material);
        if (matchedContract == null) {
            return ActionResult.failure("errors.no-matching-resource");
        }

        ContractOffer offer = findOffer(matchedContract.offerId());
        if (offer == null) {
            return ActionResult.failure("errors.no-matching-resource");
        }

        ContractRequirement requirement = matchedContract.findRequirement(material);
        if (requirement == null) {
            return ActionResult.failure("errors.no-matching-resource");
        }

        int remaining = requirement.remainingAmount();
        int available = heldItem.getAmount();
        if (!offer.partialDeliveryAllowed() && available < remaining) {
            return ActionResult.failure("errors.full-delivery-required");
        }

        int delivered = Math.min(available, remaining);
        if (delivered <= 0) {
            return ActionResult.failure("errors.no-matching-resource");
        }

        ItemUtil.removeFromHand(player.getInventory(), hand, delivered);
        requirement.addDeliveredAmount(delivered);
        persistPlayerContract(matchedContract);
        syncBoardDisplay(board);

        messages.send(player, "contracts.progress-delivery", Map.of(
            "%title%", offer.title(),
            "%progress%", Integer.toString(requirement.deliveredAmount()),
            "%goal%", Integer.toString(requirement.amount()),
            "%resource%", TextUtil.prettyToken(requirement.material().name())
        ));

        if (matchedContract.isFulfilled()) {
            rewardBoardContract(player, board, matchedContract);
        }

        return ActionResult.success("", Map.of());
    }

    public int checkConstructionCompletions(Player player, Block changedBlock) {
        if (changedBlock == null || changedBlock.getWorld() == null) {
            return 0;
        }
        int completed = 0;
        String worldName = changedBlock.getWorld().getName();
        int x = changedBlock.getX();
        int y = changedBlock.getY();
        int z = changedBlock.getZ();
        for (Map<String, ContractOffer> pool : offersByBoard.values()) {
            for (ContractOffer offer : pool.values()) {
                if (!offer.active() || !offer.requiresWorldValidation() || !offer.isComplete()) {
                    continue;
                }
                ConstructionSite site = offer.site();
                if (site == null || !site.contains(worldName, x, y, z)) {
                    continue;
                }
                if (!validateRoadSite(offer)) {
                    continue;
                }
                Board board = offer.boardId() == null ? null : boardService.getBoard(offer.boardId()).orElse(null);
                if (board == null) {
                    continue;
                }
                completeBoardOffer(player, board, offer);
                completed++;
            }
        }
        return completed;
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

        if (offer != null && offer.scope() != ContractScope.GLOBAL) {
            offer.decrementActivePlayers();
            persistOffer(offer);
        }

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

    public int getSettlementTrust(String boardId) {
        return getSettlementProgress(boardId).trust();
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
        return stats.computeIfAbsent(playerUuid, uuid -> new PlayerStats(uuid, 0, 0, 0, 0));
    }

    public String objectiveLabel(ContractOffer offer) {
        if (offer.type() == ContractType.CONSTRUCTION) {
            return offer.completedRequirementCount() + "/" + offer.totalRequirementCount() + " requirements";
        }
        return offer.requiredAmount() + "x " + TextUtil.prettyToken(offer.requiredMaterial().name());
    }

    public int boardProgress(ContractOffer offer) {
        return offer.totalDeliveredAmount();
    }

    public String rankDescription(ContractRank rank) {
        return catalog.rankDescription(rank);
    }

    public String rankLabel(ContractOffer offer) {
        return offer.rank() == null ? "Project" : offer.rank().name();
    }

    public int contractorLevel(UUID playerUuid) {
        return progression().levelForXp(getStats(playerUuid).contractXp());
    }

    public int communityLevel() {
        return effectiveGenerationLevel(null);
    }

    public ConstructionRules constructionRules() {
        return catalog.constructionRules();
    }

    public void regenerateOffers() {
        try {
            storage.deactivatePendingOffers();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not deactivate pending offers: " + exception.getMessage());
        }
        offersByBoard.values().forEach(pool -> pool.values().removeIf(offer -> !offer.active()));
        globalOffers.values().removeIf(offer -> !hasAcceptedActiveContract(offer.id()));
        ensureAllBoardPools();
    }

    public void syncBoardDisplay(Board board) {
        boardSignService.syncBoard(board, getLocalOffers(board.id()), catalog.constructionRules().signProgressFormat());
    }

    public int inventoryProgress(Player player, ContractOffer offer) {
        int total = 0;
        for (ContractRequirement requirement : offer.requirements()) {
            total += Math.min(requirement.amount(), ItemUtil.countMaterial(player.getInventory(), requirement.material()));
        }
        return total;
    }

    private boolean submitInternal(Player player, PlayerContract contract, ContractOffer offer) {
        if (!canProgress(player) || offer.scope() != ContractScope.GLOBAL) {
            return false;
        }
        if (!inventoryHasAllRequirements(player.getInventory(), offer.requirements())) {
            return false;
        }
        removeRequirements(player.getInventory(), offer.requirements());
        contract.setProgress(offer.totalRequiredAmount());
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
        playerStats.addContractXp(progression().xpFor(offer.rank()));
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
        playerStats.addContractXp(progression().xpFor(offer.rank()));
        persistStats(playerStats);

        SettlementProgress progress = getSettlementProgress(board.id());
        int trustGain = Math.max(
            offer.isProjectOffer() ? projectSettings().trustPerProjectCompletion() : projectSettings().trustPerRoutineCompletion(),
            totalReward.reputation()
        );
        progress.addTrust(trustGain);
        if (offer.isProjectOffer()) {
            progress.incrementProjectCompleted();
            progress.setProjectCooldownUntil(now() + projectSettings().baseProjectCooldownSeconds());
        } else {
            progress.incrementRoutineCompleted();
        }
        progress.setUpdatedAt(now());
        persistSettlementProgress(progress);

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
            // Money rewards are displayed only; use command rewards to transfer actual currency.
            messages.send(player, "contracts.reward-money", Map.of("%money%", TextUtil.formatMoney(reward.money())));
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
            .filter(ContractOffer::isBoardBackedShared)
            .filter(offer -> offer.acceptsMaterial(material))
            .filter(offer -> offer.publicOffer() || offer.isPersonalFor(playerUuid))
            .sorted(Comparator
                .comparing((ContractOffer offer) -> !offer.isPersonalFor(playerUuid))
                .thenComparingLong(ContractOffer::createdAtEpochSeconds)
                .thenComparing(Comparator.comparingDouble(ContractOffer::aggregateCompletionRatio).reversed()))
            .findFirst()
            .orElse(null);
    }

    private PlayerContract findBoardContract(UUID playerUuid, String boardId, Material material) {
        Map<String, PlayerContract> perPlayer = openContracts.get(playerUuid);
        if (perPlayer == null) {
            return null;
        }
        for (PlayerContract contract : perPlayer.values()) {
            if (contract.status() != PlayerContractStatus.ACTIVE) {
                continue;
            }
            if (!boardId.equals(contract.boardId())) {
                continue;
            }
            if (contract.findRequirement(material) != null) {
                return contract;
            }
        }
        return null;
    }

    private void rewardBoardContract(Player player, Board board, PlayerContract contract) {
        ContractOffer offer = findOffer(contract.offerId());
        if (offer == null) {
            return;
        }
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
        applyRewardBundle(player, paidReward, offer, board.id());

        contract.setStatus(PlayerContractStatus.CLAIMED);
        contract.setCompletedAtEpochSeconds(now());
        persistPlayerContract(contract);

        Map<String, PlayerContract> perPlayer = openContracts.get(player.getUniqueId());
        if (perPlayer != null) {
            perPlayer.remove(contract.offerId());
            if (perPlayer.isEmpty()) {
                openContracts.remove(player.getUniqueId());
            }
        }

        offer.decrementActivePlayers();
        persistOffer(offer);

        PlayerStats playerStats = getStats(player.getUniqueId());
        playerStats.incrementCompletedContracts();
        if (paidReward.reputation() > 0) {
            playerStats.addReputation(paidReward.reputation());
        }
        playerStats.addContractXp(progression().xpFor(offer.rank()));
        persistStats(playerStats);

        SettlementProgress progress = getSettlementProgress(board.id());
        int trustGain = Math.max(
            offer.isProjectOffer() ? projectSettings().trustPerProjectCompletion() : projectSettings().trustPerRoutineCompletion(),
            paidReward.reputation()
        );
        progress.addTrust(trustGain);
        if (offer.isProjectOffer()) {
            progress.incrementProjectCompleted();
            progress.setProjectCooldownUntil(now() + projectSettings().baseProjectCooldownSeconds());
        } else {
            progress.incrementRoutineCompleted();
        }
        progress.setUpdatedAt(now());
        persistSettlementProgress(progress);

        messages.send(player, "contracts.completed", Map.of("%title%", offer.title()));
        messages.send(player, "contracts.complete-summary", Map.of(
            "%title%", offer.title(),
            "%money%", TextUtil.formatMoney(paidReward.money())
        ));
        syncBoardDisplay(board);
    }

    private void trimExtraLocalOffers(Board board) {
        Map<String, ContractOffer> pool = offersByBoard.get(board.id());
        if (pool == null) {
            return;
        }

        List<ContractOffer> rankedLocal = pool.values().stream()
            .filter(ContractOffer::active)
            .filter(ContractOffer::isBoardLocal)
            .filter(offer -> !offer.specialOffer())
            .sorted(Comparator.comparingLong(ContractOffer::createdAtEpochSeconds))
            .toList();
        for (int index = 4; index < rankedLocal.size(); index++) {
            ContractOffer offer = rankedLocal.get(index);
            offer.setActive(false);
            persistOffer(offer);
        }

        List<ContractOffer> specialLocal = pool.values().stream()
            .filter(ContractOffer::active)
            .filter(ContractOffer::isBoardLocal)
            .filter(ContractOffer::specialOffer)
            .sorted(Comparator.comparingLong(ContractOffer::createdAtEpochSeconds))
            .toList();
        for (int index = 1; index < specialLocal.size(); index++) {
            ContractOffer offer = specialLocal.get(index);
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
        return inventoryHasAllRequirements(player.getInventory(), offer.requirements())
            ? "errors.not-ready"
            : "errors.not-enough-items";
    }

    private boolean inventoryHasAllRequirements(PlayerInventory inventory, List<ContractRequirement> requirements) {
        for (ContractRequirement requirement : requirements) {
            if (ItemUtil.countMaterial(inventory, requirement.material()) < requirement.amount()) {
                return false;
            }
        }
        return true;
    }

    private void removeRequirements(PlayerInventory inventory, List<ContractRequirement> requirements) {
        for (ContractRequirement requirement : requirements) {
            ItemUtil.removeMaterial(inventory, requirement.material(), requirement.amount());
        }
    }

    private boolean canFinalizeSharedOffer(ContractOffer offer) {
        return !catalog.constructionRules().completeOnlyWhenAllRequirementsMet() || offer.isComplete();
    }

    private boolean meetsConstructionValidation(ContractOffer offer) {
        if (!offer.requiresWorldValidation()) {
            return true;
        }
        return validateRoadSite(offer);
    }

    private boolean validateRoadSite(ContractOffer offer) {
        ConstructionMetadata metadata = offer.metadata();
        ConstructionSite site = offer.site();
        if (!metadata.isRoadProject() || site == null || metadata.surface() == null) {
            return !offer.requiresWorldValidation();
        }
        org.bukkit.World world = plugin.getServer().getWorld(site.worldName());
        if (world == null) {
            return false;
        }
        for (ua.grigo.frontiercontracts.model.BlockPosition tile : site.roadTiles()) {
            if (world.getBlockAt(tile.x(), tile.y(), tile.z()).getType() != metadata.surface()) {
                return false;
            }
        }
        return true;
    }

    private EnumSet<ContractRank> localRankProfile(int slotIndex) {
        return switch (slotIndex) {
            case 0 -> EnumSet.of(ContractRank.F, ContractRank.E, ContractRank.D);
            case 1 -> EnumSet.of(ContractRank.F, ContractRank.E, ContractRank.D, ContractRank.C);
            case 2 -> EnumSet.of(ContractRank.E, ContractRank.D, ContractRank.C, ContractRank.B);
            default -> EnumSet.allOf(ContractRank.class);
        };
    }

    private Map<ContractRank, Integer> buildRankWeights(SettlementProgress progress, EnumSet<ContractRank> allowedRanks) {
        EnumMap<ContractRank, Integer> adjusted = new EnumMap<>(ContractRank.class);
        Map<ContractRank, Integer> base = progression().weightsForLevel(effectiveGenerationLevel(progress));
        for (ContractRank rank : ContractRank.values()) {
            adjusted.put(rank, allowedRanks.contains(rank) ? Math.max(0, base.getOrDefault(rank, 0)) : 0);
        }
        if (progress != null) {
            AntiFrustrationSettings antiFrustration = antiFrustration();
            if (progress.rankedWithoutHigh() >= antiFrustration.highRankPityThreshold()) {
                adjusted.computeIfPresent(ContractRank.B, (ignored, value) -> value + antiFrustration.highRankWeightBonus());
                adjusted.computeIfPresent(ContractRank.A, (ignored, value) -> value + antiFrustration.highRankWeightBonus());
                adjusted.computeIfPresent(ContractRank.S, (ignored, value) -> value + antiFrustration.highRankWeightBonus());
            }
            if (progress.rankedWithoutElite() >= antiFrustration.eliteRankPityThreshold()) {
                adjusted.computeIfPresent(ContractRank.A, (ignored, value) -> value + antiFrustration.eliteRankWeightBonus());
                adjusted.computeIfPresent(ContractRank.S, (ignored, value) -> value + antiFrustration.eliteRankWeightBonus());
            }
        }
        return adjusted;
    }

    private List<ContractRank> weightedRankOrder(Map<ContractRank, Integer> weights, EnumSet<ContractRank> allowedRanks) {
        List<ContractRank> order = new ArrayList<>();
        EnumMap<ContractRank, Integer> remaining = new EnumMap<>(ContractRank.class);
        for (ContractRank rank : ContractRank.values()) {
            int weight = allowedRanks.contains(rank) ? Math.max(0, weights.getOrDefault(rank, 0)) : 0;
            if (weight > 0) {
                remaining.put(rank, weight);
            }
        }
        while (!remaining.isEmpty()) {
            int total = remaining.values().stream().mapToInt(Integer::intValue).sum();
            if (total <= 0) {
                break;
            }
            int roll = (int) (Math.random() * total);
            int cumulative = 0;
            ContractRank picked = null;
            for (Map.Entry<ContractRank, Integer> entry : remaining.entrySet()) {
                cumulative += entry.getValue();
                if (roll < cumulative) {
                    picked = entry.getKey();
                    break;
                }
            }
            if (picked == null) {
                picked = remaining.keySet().iterator().next();
            }
            order.add(picked);
            remaining.remove(picked);
        }
        for (ContractRank rank : allowedRanks) {
            if (!order.contains(rank)) {
                order.add(rank);
            }
        }
        return order;
    }

    private int effectiveGenerationLevel(SettlementProgress progress) {
        int communityLevel = stats.values().stream()
            .mapToInt(playerStats -> progression().levelForXp(playerStats.contractXp()))
            .max()
            .orElse(1);
        if (progress == null) {
            return communityLevel;
        }
        int trustBonus = progress.trust() / projectSettings().trustLevelDivisor();
        return Math.min(progression().maxLevel(), communityLevel + trustBonus);
    }

    private boolean isProjectEligible(ContractTemplate template, SettlementProgress progress, long now) {
        if (progress == null || template == null || !template.isProjectTemplate()) {
            return false;
        }
        return progress.trust() >= template.projectTrigger().minTrust()
            && progress.routineCompleted() >= template.projectTrigger().minRoutineCompletions()
            && progress.projectCompleted() >= template.projectTrigger().minProjectCompletions()
            && now >= Math.max(progress.projectCooldownUntil(), 0L);
    }

    private SettlementProgress getSettlementProgress(String boardId) {
        return settlementProgress.computeIfAbsent(
            boardId,
            ignored -> new SettlementProgress(boardId, 0, 0, 0, 0L, 0, 0, List.of(), now())
        );
    }

    private ProgressionSettings progression() {
        return catalog.progressionSettings();
    }

    private ProjectGenerationSettings projectSettings() {
        return catalog.projectGenerationSettings();
    }

    private AntiFrustrationSettings antiFrustration() {
        return progression().antiFrustration();
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

    private void persistSettlementProgress(SettlementProgress progress) {
        try {
            storage.saveSettlementProgress(progress);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not save settlement progress: " + exception.getMessage());
        }
    }
}
