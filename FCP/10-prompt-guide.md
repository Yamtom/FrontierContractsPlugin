# LLM Prompt Guide — Відтворення FrontierContractsPlugin

## Як користуватись цими промптами

Кожен промпт нижче є самодостатнім. Подавай їх у LLM послідовно, починаючи від №1.  
Технічні терміни — англійською. Пояснення — будь-якою зручною мовою.

---

## Prompt 1 — Project Scaffold

```
Create a new Paper Minecraft plugin project with the following specifications:

- Plugin name: FrontierContractsPlugin
- Main class: ua.grigo.frontiercontracts.FrontierContractsPlugin
- API version: 1.21
- Java version: 21
- Build system: Gradle with fatJar task (embed SQLite driver)

Dependencies:
- Paper API 1.21.4
- SQLite JDBC 3.46.1.3 (embedded)
- Vault 1.5.4 (soft dependency, compileOnly)
- PlaceholderAPI 2.11.6 (soft dependency, compileOnly)

Create the following package structure:
ua.grigo.frontiercontracts/
  command/
  construction/
  core/
    board/
  customorders/
  economy/
  gui/
  hook/
  listener/
  model/
    enums/
  storage/
  util/

Generate:
1. build.gradle with fatJar configuration
2. plugin.yml with main class, commands (contracts), permissions
3. Main plugin class with onEnable/onDisable stubs
```

---

## Prompt 2 — Board System

```
Implement a physical board system for a Paper Minecraft plugin.

A "Board" is a real in-world structure made of:
- 6 OAK_PLANKS blocks in a 3x2 grid (3 wide, 2 tall)
- 6 wall signs attached to the front face of each plank
- 1 OAK_FENCE post below the center of the bottom row
- 1 Bell block within 13 blocks radius

Classes to create:

1. Board (model): id, name, world, anchorPos, facing, columnSide, bellPos,
   bellDistance, regionTag, contractPools, difficultyModifier, rewardModifier,
   reputationModifier, localOfferSlots, refreshIntervalSeconds, nextRefreshAt, active

2. BoardLayout: calculates absolute Location objects for all 6 planks, 6 signs,
   and fence post based on anchor + facing + columnSide.
   Methods: headerSign(), taskSignBlocks(), allPlanks(), allSigns()

3. BoardStructureValidator: validates that real blocks in the world match the
   expected layout. Returns ValidationResult enum:
   WORLD_MISSING, INVALID_STRUCTURE, EMPTY_LOCAL_POOL, GENERATION_FAILED, OK

4. BoardSignService: updates physical wall signs with contract data.
   Max 15 chars per sign line. Use Paper dual-sided signs API (sign.getSide(Side.FRONT)).

5. BoardService: CRUD operations, loads/saves boards.yml, maintains:
   - Map<String, Board> boardsById
   - Map<String, String> locationIndex ("world:X:Y:Z" -> boardId)
   Method findBoardAtLocation(Block) uses the location index for O(1) lookup.
```

---

## Prompt 3 — Contract Data Model

```
Create the contract data model hierarchy for a Paper plugin:

1. ContractTemplate (YAML-loaded archetype):
   Fields: id, displayName, rank(ContractRank), scope(ContractScope), type(ContractType),
   contractPools(List<String>), regionTags(List<String>), requirements(List<ContractRequirement>),
   rewardBundle(RewardBundle), durationMinutes, maxSimultaneousPlayers, weight

2. ContractOffer (spawned DB instance):
   Fields: offerId(UUID), templateId, boardId, scope, rank, status(OfferStatus),
   requirementsBlob(JSON), rewardBlob(JSON), expiresAt(Instant), maxPlayers, activePlayers

3. PlayerContract (player-accepted instance):
   Fields: contractId(UUID), playerUuid, offerId, boardId, rank, type,
   requirementsBlob, rewardBlob, status(ContractStatus),
   acceptedAt, deadline, completedAt, deathsSinceAccept

4. ContractRequirement (record): material(Material), amount(int), deliveredAmount(int)
   Methods: isFulfilled(), remaining()

5. RewardBundle (record): baseMoney, bonusMoney, xp, rewardCommands(List<String>)

6. Enums:
   - ContractRank: F, E, D, C, B, A, S
   - ContractScope: LOCAL(slots=3, lifetime=15min), REGIONAL(slots=2, lifetime=60min), GLOBAL(slots=1, lifetime=4h)
   - ContractType: DELIVERY, CONSTRUCTION, PROJECT
   - ContractStatus: ACTIVE, COMPLETED, ABANDONED, EXPIRED
   - OfferStatus: ACTIVE, ACCEPTED, COMPLETED, EXPIRED

7. ContractTemplateRegistry: loads all *.yml files from contracts/ folder,
   validates and stores them by id and by pool name.
```

---

## Prompt 4 — Offer Pool Generation with Pity System

```
Implement the offer generation system with a weighted random + pity mechanism.

CoreContractService.generateOffersForBoard(Board board):

1. Determine player level range present at this board (or use board's difficulty modifier)
2. For each scope slot (LOCAL x3, REGIONAL x2, GLOBAL x1):
   a. Roll a ContractRank using ContractRankRoller
   b. Filter templates by: scope, rank, board.contractPools, region compatibility
   c. Apply recentVariants exclusion (templates in player's recent queue get weight 0)
   d. Weighted random select from remaining templates
   e. Create ContractOffer with UUID, set expiresAt from scope lifetime
   f. Save to DB via StorageService

ContractRankRoller.rollRank(int playerLevel, int pityCounter):
1. Look up rank-roll-table for playerLevel (interpolate between entries)
2. Apply pity bonuses:
   - if pityCounter >= highRankPityThreshold: add highRankPityBonus to A/B weights
   - if pityCounter >= eliteRankPityThreshold: add eliteRankPityBonus to S weight
3. Perform weighted random selection
4. Return ContractRank

After a high rank (B or above) is rolled, pityCounter resets to 0.

recentVariants queue:
- Stored as JSON array in player_stats.recent_variants_blob
- Size capped at recentVariantsQueueSize (config, default 20)
- Add templateId on contract completion, remove oldest on overflow
```

---

## Prompt 5 — Delivery Flow

```
Implement the item delivery and contract completion flow.

CoreContractService.tryDeliver(Player player, Board board):
1. Find all ACTIVE PlayerContracts for this player at this board
2. For each contract:
   a. Deserialize requirements from requirements_blob
   b. For each unfulfilled ContractRequirement:
      - Count matching items in player.getInventory()
      - Remove min(needed, available) items
      - Increment deliveredAmount
   c. Serialize updated requirements back to JSON
   d. Save contract to DB
   e. Update board signs via BoardSignService
   f. If all requirements fulfilled -> call completeOffer(player, contract, board)
3. Send player feedback message

CoreContractService.completeOffer(Player player, PlayerContract contract, Board board):
1. Deserialize reward_blob to RewardBundle
2. Calculate final money:
   - baseMoney * board.rewardModifier
   - if deathsSinceAccept == 0: multiply by deathlessBonusMultiplier (1.2)
3. economyGateway.deposit(player, finalMoney)
4. progressionService.addXp(player, reward.xp)
5. progressionService.adjustReputation(player, board, +completeRepDelta)
6. progressionService.updateRecentVariants(player, contract.templateId)
7. progressionService.incrementPityCounter(player) -- reset if high rank
8. Execute reward commands via Bukkit.dispatchCommand(console, formatted_cmd)
   (replace %player% with player.getName(), %board_id% with board.getId())
9. Check project triggers for settlement progress unlock
10. Update offer: activePlayers--, if activePlayers==0: status=COMPLETED
11. Update contract: status=COMPLETED, completedAt=now
12. Save both to DB
```

---

## Prompt 6 — Progression System

```
Implement the three-tier progression system.

PlayerStats (DB: player_stats):
- Fields: playerUuid, xp, level, contractsCompleted, recentVariantsBlob, pityCounter, lastHighRankAt
- Level formula: level = min(floor(xp / xpPerLevel), maxLevel)
- Load on first login, create default record if not exists

SettlementReputation (DB: settlement_reputation, PK: playerUuid + boardId):
- Fields: playerUuid, boardId, reputation, lifetimeCompleted
- Reputation minimum = 0
- Deltas from config: complete=+10, abandon=-5, expire=-3

SettlementProgress (DB: settlement_progress, PK: boardId):
- Fields: boardId, trustLevel, routineCompletions, unlockedProjects(JSON), activeProjectId

ProgressionService:
- addXp(player, amount): update xp, recalculate level, notify if levelUp
- adjustReputation(player, board, delta): clamp to min 0, save
- incrementPityCounter(player): pityCounter++, save
- resetPityCounter(player): pityCounter=0, save
- updateRecentVariants(player, templateId): FIFO queue update
- checkProjectTriggers(board): iterate project-triggers from config,
  if progress.trustLevel >= minTrust AND progress.routineCompletions >= minRoutineCompletions
  -> add projectId to unlockedProjects, save
```

---

## Prompt 7 — Construction Module

```
Implement the construction contract module for a Paper plugin.

ConstructionSite (record): siteId, boardId, projectId, type(ROAD|BUILDING),
anchorPos, width, depth, tiles(List<BlockPosition>), boundingBox(BoundingBox),
requiredMaterial, requiredCount, placedCount

ConstructionSitePlanner.findSite(Board board, ConstructionTemplate template):
For BUILDING:
1. Use bell position as center
2. Try 4 cardinal directions, offset by plannerSearchRadius (15 blocks)
3. For each candidate position, check NxN footprint (7x7 or 9x9):
   - Height variance <= maxHeightVariance (2 blocks)
   - No blacklisted blocks (logs, stone bricks, etc. indicating existing structure)
   - No existing ConstructionSite within siteSeparationRadius (30 blocks)
4. Return first valid position or empty Optional

ConstructionValidator.checkSite(ConstructionSite site, World world):
For BUILDING:
1. Iterate all blocks in site.boundingBox
2. Count blocks matching site.requiredMaterial -> placedCount
3. If placedCount >= requiredCount -> return COMPLETE
4. Update site in DB, return IN_PROGRESS

ConstructionWorldListener:
- BlockPlaceEvent (MONITOR, ignoreCancelled=true):
  If block position is inside any active site's boundingBox:
  Schedule 1-tick delay, then call constructionValidator.checkSite(site)

ConstructionContractService:
- generateConstructionOffer(board, template): plan site, save, spawn offer
- acceptConstruction(player, offer): assign player, notify location
- checkCompletion(site): validate + complete if done
```

---

## Prompt 8 — Custom Orders

```
Implement a P2P escrow marketplace for a Paper plugin.

CustomOrder (model):
Fields: orderId(UUID), creatorUuid, assignedUuid, material(Material), amount,
price, status(OrderStatus), createdAt, deadline, completedAt, boardId

OrderStatus enum: OPEN, ACCEPTED, COMPLETED, CANCELLED, EXPIRED, FAILED

CustomOrderService:

createOrder(Player creator, Material material, int amount, double price, int durationMinutes):
1. Validate: amount in [minItemAmount, maxItemAmount], price in [minPrice, maxPrice]
2. Validate: duration in [minDuration, maxDuration]
3. Validate: creator's open order count < maxOpenOrdersPerPlayer
4. Validate: creator has sufficient balance
5. economyGateway.withdraw(creator, price)  -- escrow
6. INSERT into custom_orders with status=OPEN
7. Notify creator

acceptOrder(Player assignee, String orderId):
1. Verify order exists and status=OPEN
2. Verify assignee != creator
3. UPDATE assigned_uuid=assignee, status=ACCEPTED, deadline=now+duration
4. Notify both parties

submitOrder(Player assignee, String orderId):
1. Verify assignee is the assigned player
2. Verify status=ACCEPTED and not expired
3. Check assignee has required items in inventory
4. player.getInventory().removeItem(ItemStack)
5. economyGateway.deposit(assignee, order.price)  -- pay
6. UPDATE status=COMPLETED, completedAt=now
7. Notify both parties

cancelOrder(Player requester, String orderId):
1. Verify requester is creator or assignee
2. economyGateway.depositOffline(creatorUuid, order.price)  -- refund
3. UPDATE status=CANCELLED

expireOverdueOrders() [called by scheduler]:
1. SELECT orders WHERE status=ACCEPTED AND deadline < now
2. For each: depositOffline(creatorUuid, price), status=EXPIRED
```

---

## Prompt 9 — Economy Gateway

```
Implement an adaptive dual-provider economy system.

EconomyProvider (interface):
- boolean deposit(Player player, double amount)
- boolean withdraw(Player player, double amount)  
- double balance(Player player)
- void depositOffline(UUID uuid, double amount)
- boolean has(Player player, double amount)
- String getCurrencyName()

VaultEconomyProvider implements EconomyProvider:
- Wraps Vault Economy API
- depositOffline uses Bukkit.getOfflinePlayer(uuid)

InternalWalletEconomyProvider implements EconomyProvider:
- Uses wallet_balances SQLite table
- All methods are synchronized
- depositOffline: direct SQL UPDATE wallet_balances SET balance=balance+? WHERE player_uuid=?
  If no row exists, INSERT with the amount.

VaultHook:
- setup(): RegisteredServiceProvider<Economy> via Bukkit.getServicesManager()
- isAvailable(), getEconomy()

AdaptiveEconomyGateway:
- refresh(): if VaultHook.isAvailable() use VaultEconomyProvider, else InternalWalletEconomyProvider
- Delegates all EconomyProvider methods to active provider
- Call refresh() in onEnable() and when PluginEnableEvent fires for Vault

FrontierPlaceholderExpansion extends PlaceholderExpansion:
- Identifier: "frontier"
- Placeholders: frontier_balance, frontier_level, frontier_xp,
  frontier_contracts_completed, frontier_reputation_<boardId>
```

---

## Prompt 10 — SQLite Storage Layer

```
Implement the storage layer for a Paper plugin using embedded SQLite.

StorageService:
- Constructor: takes plugin data folder path, creates frontier_data.db
- Uses org.sqlite.JDBC driver (embedded in fatJar)
- Single Connection with WAL mode enabled (PRAGMA journal_mode=WAL)
- All DB operations are synchronized

Create these tables in initializeSchema():
1. generated_offers (offer_id PK, template_id, board_id, scope, rank, status,
   requirements_blob, reward_blob, expires_at, max_players, active_players, created_at)
2. player_contracts (contract_id PK, player_uuid, offer_id, board_id, rank, type,
   requirements_blob, reward_blob, status, accepted_at, deadline, completed_at, deaths_since_accept)
3. player_stats (player_uuid PK, xp, level, contracts_completed, recent_variants_blob,
   pity_counter, last_high_rank_at)
4. settlement_reputation (player_uuid + board_id PK, reputation, lifetime_completed)
5. settlement_progress (board_id PK, trust_level, routine_completions, unlocked_projects, active_project_id, site_blob)
6. custom_orders (order_id PK, creator_uuid, assigned_uuid, material, amount, price,
   status, created_at, deadline, completed_at, board_id)
7. wallet_balances (player_uuid PK, balance REAL DEFAULT 0.0)

Key methods:
- saveOffer(ContractOffer): INSERT OR REPLACE
- getActiveOffersForBoard(String boardId): SELECT WHERE board_id=? AND status='ACTIVE'
- expireOffers(long now): UPDATE SET status='EXPIRED' WHERE expires_at < ? AND status='ACTIVE'
- saveContract(PlayerContract): INSERT OR REPLACE
- getActiveContractsForPlayer(UUID): SELECT WHERE player_uuid=? AND status='ACTIVE'
- getOrCreatePlayerStats(UUID): SELECT, if not found INSERT default row
- updatePlayerStats(PlayerStats): UPDATE
- upsertReputation(SettlementReputation): INSERT OR REPLACE
- getReputation(UUID playerUuid, String boardId): SELECT
- upsertProgress(SettlementProgress): INSERT OR REPLACE
- getProgress(String boardId): SELECT
- getOpenOrders(): SELECT WHERE status='OPEN'
- addToBalance(UUID, double): UPDATE wallet_balances SET balance=balance+? WHERE player_uuid=?

JSON serialization: use Gson for all blob fields. Create separate serializer classes.
```

---

## Prompt 11 — GUI System

```
Implement an inventory-based GUI system for a Paper plugin.

Create these InventoryHolder classes:

1. BoardMenuHolder (54 slots):
   - Constructor: Board board, List<ContractOffer> offers, Player viewer
   - Slot 4: Board info item (name, reputation, trust level in lore)
   - Slots 10-14: Up to 5 LOCAL contract offer items
   - Slots 19-21: Up to 3 REGIONAL contract offer items  
   - Slot 31: GLOBAL contract offer item (if any)
   - Slot 49: Close button (BARRIER)
   - Gray glass panes as decoration in remaining border slots
   - Each offer item: material icon = first requirement material,
     displayName = "[RankIcon] Template Name",
     lore = [requirements list, reward money, deadline countdown]

2. ContractDetailHolder (27 slots):
   - Constructor: ContractOffer offer, PlayerContract existingContract (nullable), Player viewer
   - Slot 13: Full contract details item
   - Slot 11: Accept button (LIME_WOOL) if offer.activePlayers < offer.maxPlayers
   - Slot 15: Abandon button (RED_WOOL) if player has active contract
   - Slot 22: Deliver button (CHEST) if player has active contract

3. CustomOrdersMenuHolder (54 slots):
   - Paginated list, 45 items per page
   - Navigation: slot 45 (prev), 49 (close), 53 (next)

4. CustomOrderDetailHolder (27 slots):
   - Slot 13: Order details item
   - Slot 11: Accept (LIME_WOOL) if OPEN and viewer != creator
   - Slot 15: Cancel (RED_WOOL) if creator and OPEN/ACCEPTED
   - Slot 22: Submit (GOLD_INGOT) if assignee and ACCEPTED

MenuListener handles InventoryClickEvent:
- Check holder type via instanceof
- Cancel event always (prevent item theft)
- Route clicks to appropriate service methods
```

---

## Prompt 12 — Commands & Tab Completion

```
Implement the command system for a Paper plugin.

Main command: /contracts (aliases: /fc, /frontier)
Register in plugin.yml with permission frontier.use

ContractsCommand implements CommandExecutor, TabCompleter:

Subcommands:
- active: list player's active contracts
- balance: show economy balance  
- submit <boardId>: trigger tryDeliver for specified board
- abandon <contractId>: abandon a contract (-5 reputation)
- order: delegate to OrderSubcommand
- admin: delegate to AdminSubcommand (requires frontier.admin)

OrderSubcommand:
- browse [boardId]: open CustomOrdersMenuHolder
- mine: open player's own orders
- create <material> <amount> <price> <durationMinutes>: create new order
- cancel <orderId>: cancel own order

AdminSubcommand (requires frontier.admin):
- scanboard: validate structure at player's looking-at block
- placeboard <id> <facing>: register new board at player location
- validateboard <id>: run BoardStructureValidator
- board list: list all boards
- board info <id>: show board details
- refresh <boardId>: force regenerate offers
- reputation <player> <boardId>: show reputation
- reputation set <player> <boardId> <value>: set reputation

TabCompleter:
- /contracts -> [active, balance, submit, abandon, order, admin]
- /contracts submit -> board IDs from BoardService
- /contracts abandon -> active contract IDs for the player
- /contracts order -> [browse, mine, create, cancel]
- /contracts admin -> [scanboard, placeboard, validateboard, board, refresh, reputation]
Filter suggestions with startsWith(currentArg).
```

---

## Prompt 13 — Configuration Layer

```
Implement the configuration loading system.

PluginConfig (loaded from config.yml):
- Use Bukkit FileConfiguration
- Fields as Java records or plain classes:
  - GeneralConfig: language, debug, autoRefreshBoards
  - EconomyConfig: preferVault, currencyName, currencySymbol
  - ProgressionConfig: xpPerLevel, maxLevel, recentVariantsQueueSize, PityConfig, ReputationConfig, deathlessBonusMultiplier
  - CustomOrdersConfig: maxOpenPerPlayer, minPrice, maxPrice, minDurationMinutes, maxDurationMinutes, minItemAmount, maxItemAmount
  - BoardConfig: bellSearchRadius, requireVillagePoi, signMaxLineLength
  - ConstructionConfig: plannerSearchRadius, siteSeparationRadius, maxHeightVariance
  - SchedulerConfig: offerExpireCheckTicks, orderExpireCheckTicks, constructionCheckTicks

ProgressionConfig.RankRollTable:
- Load from progression.yml
- List of (level, Map<ContractRank, Integer>) entries
- interpolate(int playerLevel, ContractRank rank): linear interpolation between two nearest entries

MessageService:
- Loads messages_en.yml or messages_<lang>.yml
- getMessage(String key, Map<String, String> placeholders): replace {key} patterns
- All messages support & color codes (use ChatColor.translateAlternateColorCodes)

ContractTemplateRegistry:
- loadTemplates(): scan contracts/ folder, load each *.yml as ContractTemplate
- getByPool(String pool): return templates in that pool
- getById(String id): single template lookup
```

---

## Prompt 14 — Localization System

```
Implement the localization system for a Paper plugin.

Create default message files:
1. messages_en.yml (English)
2. messages_uk.yml (Ukrainian)

Both files should have identical key structure. All values support:
- & color codes
- {placeholder} substitution tokens

Keys to include:
- prefix
- board.not-found, board.invalid-structure, board.refreshed, board.no-offers
- contract.accepted, contract.completed, contract.abandoned, contract.expired,
  contract.no-active, contract.delivery-partial, contract.delivery-complete,
  contract.level-up, contract.rank-icon.F/E/D/C/B/A/S
- order.created, order.accepted, order.completed, order.cancelled, order.expired,
  order.limit-reached, order.not-found, order.not-owner, order.insufficient-items
- economy.balance, economy.insufficient-funds
- admin.board-placed, admin.board-scan, admin.structure-valid, admin.structure-invalid,
  admin.board-list-header, admin.board-list-entry, admin.reputation-display

MessageService.send(Player player, String key, Map<String,String> placeholders):
- Get formatted message string
- Replace {token} patterns with values from placeholders map
- Translate & color codes
- Send via player.sendMessage()

MessageService.sendActionBar(Player player, String key, Map<String,String> placeholders):
- Same but via player.spigot().sendMessage(ChatMessageType.ACTION_BAR, ...)
```

---

## Prompt 15 — Plugin Hooks & Lifecycle

```
Implement the full plugin lifecycle and external hook system.

FrontierContractsPlugin (main class):
- Extend JavaPlugin
- Store all service references as private fields
- onEnable():
  1. Save default config, boards.yml, messages files
  2. new StorageService(getDataFolder()) -> initializeSchema()
  3. new ContractTemplateRegistry(this) -> loadTemplates()
  4. new BoardService(this, storageService) -> loadBoards()
  5. VaultHook.setup() -> log whether Vault found
  6. new AdaptiveEconomyGateway(this) -> refresh()
  7. new ProgressionService(storageService, config)
  8. new CoreContractService(this, ...) 
  9. new ConstructionContractService(this, ...)
  10. new CustomOrderService(this, ...)
  11. Register all event listeners
  12. Register PlaceholderAPI expansion if available
  13. Register /contracts command
  14. Start BukkitScheduler repeating tasks:
      - offer expire check (offerExpireCheckTicks interval)
      - order expire check (orderExpireCheckTicks interval)
      - construction site check (constructionCheckTicks interval)
      - board auto-refresh (check nextRefreshAt timestamps)

- onDisable():
  1. Cancel all scheduled tasks
  2. storageService.close() (close DB connection)
  3. Log shutdown

Provide static getInstance() for cross-class access.

VaultHook:
- setup() uses ServicesManager to find Economy provider
- isAvailable(), getEconomy()

PlaceholderAPI integration:
- Check if PlaceholderAPI plugin is enabled before registering
- FrontierPlaceholderExpansion.register() in onEnable if PAPI available
```

---

## Meta-Prompt (відтворення всього плагіна)

```
I want to create a Minecraft Paper plugin called FrontierContractsPlugin.

It's a physical contract board system where players interact with real in-world
wooden structures (3x2 OAK_PLANKS + fence + wall signs + nearby bell) to accept
and complete delivery/construction contracts.

Core features:
1. Physical boards registered by admin commands, validated against real world blocks
2. Three contract tiers: DELIVERY (bring items), CONSTRUCTION (build structures), PROJECT (large builds unlocked by trust)
3. Contract ranks F through S with weighted random generation + pity system for anti-frustration
4. Player progression: XP -> levels -> better contract rolls; per-board reputation system
5. Settlement trust: routine completions unlock PROJECT contracts
6. P2P custom orders with escrow (money locked until fulfilled)
7. Dual economy: Vault if available, internal SQLite wallet otherwise
8. PlaceholderAPI support for stats display

Tech stack: Paper 1.21.4, Java 21, Gradle fatJar, SQLite embedded, Vault soft-dep, PAPI soft-dep

Please implement this plugin step by step, starting with the project scaffold,
then the data models, then each service layer, and finally commands and GUI.
```

---

## Швидка довідка промптів

| # | Тема | Ключові класи |
|---|------|---------------|
| 1 | Project Scaffold | build.gradle, plugin.yml, main class |
| 2 | Board System | Board, BoardLayout, BoardStructureValidator, BoardSignService, BoardService |
| 3 | Contract Data Model | ContractTemplate, ContractOffer, PlayerContract, enums |
| 4 | Offer Generation + Pity | CoreContractService.generateOffersForBoard, ContractRankRoller |
| 5 | Delivery Flow | tryDeliver, completeOffer |
| 6 | Progression System | ProgressionService, PlayerStats, SettlementReputation, SettlementProgress |
| 7 | Construction Module | ConstructionSitePlanner, ConstructionValidator, ConstructionContractService |
| 8 | Custom Orders | CustomOrderService, OrderStatus state machine, escrow flow |
| 9 | Economy Gateway | AdaptiveEconomyGateway, VaultEconomyProvider, InternalWalletEconomyProvider |
| 10 | Storage Layer | StorageService, SQLite schema, JSON blobs |
| 11 | GUI System | BoardMenuHolder, ContractDetailHolder, CustomOrdersMenuHolder, MenuListener |
| 12 | Commands | ContractsCommand, AdminSubcommand, OrderSubcommand, TabCompleter |
| 13 | Configuration | PluginConfig records, ProgressionConfig.RankRollTable, MessageService |
| 14 | Localization | messages_en/uk.yml, MessageService.send/sendActionBar |
| 15 | Lifecycle & Hooks | FrontierContractsPlugin.onEnable, VaultHook, PAPI expansion |