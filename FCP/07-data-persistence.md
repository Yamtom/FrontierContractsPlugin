# Data Persistence — SQLite схема та YAML persistence

## SQLite база даних

Файл: `plugins/FrontierContractsPlugin/frontier_data.db`
Driver: `org.sqlite.JDBC` (embedded у fatJar)

Підключення через `StorageService` з connection pooling (HikariCP або простий single-connection з synchronized).

---

## Таблиці

### generated_offers
```sql
CREATE TABLE IF NOT EXISTS generated_offers (
    offer_id          TEXT PRIMARY KEY,
    template_id       TEXT NOT NULL,
    board_id          TEXT NOT NULL,
    scope             TEXT NOT NULL,
    rank              TEXT NOT NULL,
    status            TEXT NOT NULL DEFAULT 'ACTIVE',
    requirements_blob TEXT NOT NULL,
    reward_blob       TEXT NOT NULL,
    expires_at        INTEGER NOT NULL,
    max_players       INTEGER NOT NULL DEFAULT 1,
    active_players    INTEGER NOT NULL DEFAULT 0,
    created_at        INTEGER NOT NULL
);
```

### player_contracts
```sql
CREATE TABLE IF NOT EXISTS player_contracts (
    contract_id          TEXT PRIMARY KEY,
    player_uuid          TEXT NOT NULL,
    offer_id             TEXT NOT NULL,
    board_id             TEXT NOT NULL,
    rank                 TEXT NOT NULL,
    type                 TEXT NOT NULL,
    requirements_blob    TEXT NOT NULL,
    reward_blob          TEXT NOT NULL,
    status               TEXT NOT NULL DEFAULT 'ACTIVE',
    accepted_at          INTEGER NOT NULL,
    deadline             INTEGER NOT NULL,
    completed_at         INTEGER,
    deaths_since_accept  INTEGER NOT NULL DEFAULT 0
);
```

### player_stats
```sql
CREATE TABLE IF NOT EXISTS player_stats (
    player_uuid          TEXT PRIMARY KEY,
    xp                   INTEGER NOT NULL DEFAULT 0,
    level                INTEGER NOT NULL DEFAULT 0,
    contracts_completed  INTEGER NOT NULL DEFAULT 0,
    recent_variants_blob TEXT NOT NULL DEFAULT '[]',
    pity_counter         INTEGER NOT NULL DEFAULT 0,
    last_high_rank_at    INTEGER
);
```

### settlement_reputation
```sql
CREATE TABLE IF NOT EXISTS settlement_reputation (
    player_uuid         TEXT NOT NULL,
    board_id            TEXT NOT NULL,
    reputation          INTEGER NOT NULL DEFAULT 0,
    lifetime_completed  INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid, board_id)
);
```

### settlement_progress
```sql
CREATE TABLE IF NOT EXISTS settlement_progress (
    board_id              TEXT PRIMARY KEY,
    trust_level           INTEGER NOT NULL DEFAULT 0,
    routine_completions   INTEGER NOT NULL DEFAULT 0,
    unlocked_projects     TEXT NOT NULL DEFAULT '[]',
    active_project_id     TEXT,
    site_blob             TEXT
);
```

### custom_orders
```sql
CREATE TABLE IF NOT EXISTS custom_orders (
    order_id       TEXT PRIMARY KEY,
    creator_uuid   TEXT NOT NULL,
    assigned_uuid  TEXT,
    material       TEXT NOT NULL,
    amount         INTEGER NOT NULL,
    price          REAL NOT NULL,
    status         TEXT NOT NULL DEFAULT 'OPEN',
    created_at     INTEGER NOT NULL,
    deadline       INTEGER,
    completed_at   INTEGER,
    board_id       TEXT
);
```

### wallet_balances
```sql
CREATE TABLE IF NOT EXISTS wallet_balances (
    player_uuid  TEXT PRIMARY KEY,
    balance      REAL NOT NULL DEFAULT 0.0
);
```

---

## JSON Blob формати

### requirements_blob
```json
[
  { "material": "OAK_LOG", "amount": 64, "deliveredAmount": 10 },
  { "material": "STONE", "amount": 32, "deliveredAmount": 0 }
]
```

### reward_blob
```json
{
  "baseMoney": 150.0,
  "bonusMoney": 30.0,
  "xp": 45,
  "rewardCommands": ["give %player% diamond 1"]
}
```

### recent_variants_blob
```json
["oak_logs_local", "stone_supply", "wheat_harvest", "iron_ingots_c"]
```

### site_blob (ConstructionSite)
```json
{
  "siteId": "site_abc123",
  "type": "BUILDING",
  "anchorPos": { "world": "world", "x": 100, "y": 64, "z": -200 },
  "width": 7,
  "depth": 7,
  "requiredMaterial": "OAK_PLANKS",
  "requiredCount": 49,
  "placedCount": 12,
  "tiles": []
}
```

---

## YAML Persistence (boards.yml)

Зберігається у `plugins/FrontierContractsPlugin/boards.yml`.

```yaml
boards:
  central_hub:
    name: "Central Hub"
    type: "SETTLEMENT"
    world: "world"
    anchor: { x: 100, y: 64, z: -200 }
    facing: "NORTH"
    column-side: "RIGHT"
    bell: { x: 105, y: 64, z: -200 }
    bell-distance: 5.0
    region-tag: "spawn-region"
    contract-pools: ["general", "construction"]
    difficulty-modifier: 1.0
    reward-modifier: 1.0
    reputation-modifier: 1.0
    local-offer-slots: 5
    refresh-interval-seconds: 300
    next-refresh-at: 0
    active: true
```

Завантаження: `BoardService.loadBoards()` при `onEnable()`.
Збереження: `BoardService.saveBoard(board)` при будь-якій зміні.

---

## StorageService — ключові методи

| Метод | SQL операція |
|-------|-------------|
| `saveOffer(offer)` | INSERT OR REPLACE |
| `getActiveOffers(boardId)` | SELECT WHERE board_id=? AND status='ACTIVE' |
| `expireOffers()` | UPDATE SET status='EXPIRED' WHERE expires_at < ? |
| `saveContract(contract)` | INSERT OR REPLACE |
| `getActiveContracts(playerUuid)` | SELECT WHERE player_uuid=? AND status='ACTIVE' |
| `getPlayerStats(uuid)` | SELECT + INSERT IF NOT EXISTS |
| `updatePlayerStats(stats)` | UPDATE |
| `getReputation(uuid, boardId)` | SELECT |
| `updateReputation(rep)` | INSERT OR REPLACE |
| `getSettlementProgress(boardId)` | SELECT |
| `updateProgress(progress)` | UPDATE |
| `getOpenOrders(boardId)` | SELECT WHERE status='OPEN' |
| `addToBalance(uuid, amount)` | UPDATE wallet_balances SET balance=balance+? |

---

## Lifecycle — порядок завантаження в onEnable()

```
1. Ініціалізувати StorageService (відкрити DB, створити таблиці)
2. Завантажити ContractTemplateRegistry (YAML контракти)
3. Завантажити BoardService (boards.yml)
4. Ініціалізувати VaultHook (setup())
5. Ініціалізувати AdaptiveEconomyGateway (refresh())
6. Завантажити ProgressionService
7. Завантажити CoreContractService
8. Завантажити ConstructionContractService
9. Завантажити CustomOrderService
10. Зареєструвати всі listeners
11. Зареєструвати PlaceholderAPI expansion (якщо доступна)
12. Запустити BukkitScheduler tasks (refresh, expire checks)
```

---

## Правило "no delete"

Плагін ніколи не видаляє записи з БД:
- Контракти: тільки status = EXPIRED / ABANDONED / COMPLETED / FAILED
- Оффери: тільки status = EXPIRED / COMPLETED
- Дошки: тільки active = 0
- Construction sites: status = FAILED

Це забезпечує аудит і можливість відновлення даних.