# Commands, Events & GUI

## Команди (plugin.yml)

Головна команда: `/contracts` (aliases: `/fc`, `/frontier`)
Permission root: `frontier.`

### Дерево команд

```
/contracts
  active                     — показати активні контракти гравця
  balance                    — перевірити баланс
  submit <board_id>          — здати предмети на дошку (альтернатива до ліво-кліку)
  abandon <contract_id>      — відмовитись від контракту (-5 репутація)

  order
    browse [board_id]        — відкрити GUI всіх OPEN замовлень
    mine                     — відкрити GUI власних замовлень
    create <mat> <amt> <price> <min> — створити нове замовлення
    cancel <order_id>        — скасувати своє замовлення

  admin                      (permission: frontier.admin)
    scanboard                — сканувати структуру дошки під курсором
    placeboard <id> <facing> — зареєструвати нову дошку
    validateboard <id>       — запустити validation конкретної дошки
    board list               — показати всі зареєстровані дошки
    board info <id>          — деталі конкретної дошки
    refresh <board_id>       — примусово оновити оффери на дошці
    reputation <player> <board_id> — показати репутацію гравця
    reputation set <player> <board_id> <value> — встановити репутацію
```

### Permissions

| Permission | Default | Опис |
|------------|---------|------|
| `frontier.use` | true | Базовий доступ до /contracts |
| `frontier.order` | true | Доступ до /contracts order |
| `frontier.admin` | op | Всі admin команди |
| `frontier.bypass.limit` | op | Обійти ліміти замовлень |

---

## Tab Completion

Реалізується через `TabCompleter` у `ContractsCommand`:

```
/contracts <tab>          -> [active, balance, submit, abandon, order, admin]
/contracts order <tab>    -> [browse, mine, create, cancel]
/contracts admin <tab>    -> [scanboard, placeboard, validateboard, board, refresh, reputation]
/contracts admin board <tab> -> [list, info]
/contracts submit <tab>   -> [list board IDs from BoardService]
/contracts abandon <tab>  -> [list active contract IDs of player]
```

---

## Event Listeners

### BlockInteractListener

- `PlayerInteractEvent` (Action.RIGHT_CLICK_BLOCK)
- Перевіряє: чи є block у `locationIndex` BoardService?
- Правий клік: відкрити `BoardMenuHolder` GUI
- `PlayerInteractEvent` (Action.LEFT_CLICK_BLOCK)
- Лівий клік на знак дошки: запустити `tryDeliver(player, board)`

### BoardProtectionListener

- `BlockBreakEvent`, `BlockPlaceEvent`, `BlockExplodeEvent`
- Якщо block у `locationIndex`: cancel event (якщо не admin)
- `EntityExplodeEvent`: фільтрувати список блоків

### ContractProgressListener

- `PlayerDeathEvent`: збільшити `deaths_since_accept` для всіх ACTIVE контрактів гравця
- `PlayerQuitEvent`: зберегти поточний стан контрактів у DB

### ConstructionWorldListener

- `BlockPlaceEvent` (MONITOR priority): перевірити чи блок у bounding box активного сайту -> delayed check
- `PlayerInteractEvent` з лопатою: показати actionbar прогрес + check

### MenuListener

- `InventoryClickEvent`: перевірити `event.getInventory().getHolder()`
- Якщо `BoardMenuHolder`: обробити клік на контракт -> відкрити `ContractDetailHolder`
- Якщо `ContractDetailHolder`: кнопки Accept/Abandon
- Якщо `CustomOrdersMenuHolder`: клік на замовлення -> відкрити `CustomOrderDetailHolder`
- Якщо `CustomOrderDetailHolder`: кнопки Accept/Submit/Cancel
- `InventoryCloseEvent`: cleanup holders

---

## GUI Система

### BoardMenuHolder (54 slots)

```
Slot  4: Board info item (назва, репутація, trust level)
Slots 11-15: 5 Task Sign previews (контракти LOCAL)
Slots 20-22: REGIONAL контракти (3 slots)
Slot 31: GLOBAL контракт (1 slot)
Slot 45: [Попередня сторінка] або декор
Slot 49: [Закрити]
Slot 53: [Наступна сторінка] або декор
```

Кожен item = `ContractOffer` preview:
- displayName: `[Rank Icon] Contract Name`
- lore: матеріали, кількість, нагорода, дедлайн

### ContractDetailHolder (27 slots)

```
Slot 9:  Info item (повні деталі контракту)
Slot 11: [Прийняти] — GREEN_WOOL (якщо offer ACTIVE і offer.activePlayers < max)
Slot 13: [Здати предмети] — CHEST (якщо player має ACTIVE contract)
Slot 15: [Відмовитись] — RED_WOOL (якщо player має ACTIVE contract)
```

### CustomOrdersMenuHolder (54 slots)

```
Slots 0-44: Список замовлень (пагінація)
Slot 45: [<< Попередня]
Slot 49: [Закрити]
Slot 53: [Наступна >>]
```

### CustomOrderDetailHolder (27 slots)

```
Slot 13: Товар (Material icon + lore: amount, price, creator, deadline)
Slot 11: [Прийняти] — LIME_WOOL (якщо OPEN і не creator)
Slot 15: [Скасувати] — RED_WOOL (якщо creator і OPEN/ACCEPTED)
Slot 22: [Виконати] — GOLD_INGOT (якщо assignee і ACCEPTED)
```

---

## Ключові класи

| Клас | Пакет | Роль |
|------|-------|------|
| `ContractsCommand` | `command` | Main command executor + tab completer |
| `AdminSubcommand` | `command` | /contracts admin sub-tree |
| `OrderSubcommand` | `command` | /contracts order sub-tree |
| `BlockInteractListener` | `listener` | Board interact |
| `BoardProtectionListener` | `listener` | Block protection |
| `ContractProgressListener` | `listener` | Death + quit tracking |
| `ConstructionWorldListener` | `listener` | Build event tracking |
| `MenuListener` | `listener` | GUI interactions |
| `BoardMenuHolder` | `gui` | Inventory holder дошки |
| `ContractDetailHolder` | `gui` | Inventory holder деталей |
| `CustomOrdersMenuHolder` | `gui` | Inventory holder замовлень |
| `CustomOrderDetailHolder` | `gui` | Inventory holder деталі замовлення |