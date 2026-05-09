# Config Reference — Повний довідник конфігурацій

## config.yml

```yaml
# ═══════════════════════════════════════════════
# FrontierContractsPlugin — config.yml
# ═══════════════════════════════════════════════

# Загальні налаштування
general:
  language: "en"               # Мова: en | uk
  debug: false                 # Логувати debug info
  auto-refresh-boards: true    # Автоматично оновлювати оффери

# Економіка
economy:
  prefer-vault: true           # true = Vault якщо доступний
  currency-name: "coins"       # Назва валюти (для internal wallet)
  currency-symbol: "$"

# Прогресія гравців
progression:
  xp-per-level: 500            # XP для переходу на наступний рівень
  max-level: 20                # Максимальний рівень
  recent-variants-queue-size: 20  # Черга anti-repeat
  
  # Pity system
  pity:
    high-rank-threshold: 12    # Контрактів до high-rank bonus
    elite-rank-threshold: 25   # Контрактів до elite-rank bonus
    high-rank-bonus: 12        # Додаткова вага A/B рангів
    elite-rank-bonus: 8        # Додаткова вага S рангу

  # Деятлесс бонус
  deathless-bonus-multiplier: 1.2

  # Зміни репутації
  reputation:
    complete-delta: 10
    abandon-delta: -5
    expire-delta: -3

# Межі Custom Orders
custom-orders:
  max-open-per-player: 5
  min-price: 5.0
  max-price: 5000.0
  min-duration-minutes: 15
  max-duration-minutes: 1440
  min-item-amount: 1
  max-item-amount: 1728        # 27 стаків

# Board налаштування
board:
  bell-search-radius: 13       # Радіус пошуку bell
  require-village-poi: false   # Bell має бути village POI
  sign-max-line-length: 15     # Макс. символів на рядок знаку

# Планувальник будівництва
construction:
  planner-search-radius: 15    # Радіус пошуку місця для сайту
  site-separation-radius: 30   # Мін. відстань між сайтами
  max-height-variance: 2       # Макс. відхилення висоти footprint

# Scheduler інтервали (тіки, 20 ticks = 1 секунда)
scheduler:
  offer-expire-check-ticks: 1200    # Перевірка прострочених офферів (60s)
  order-expire-check-ticks: 1200    # Перевірка прострочених замовлень
  construction-check-ticks: 400     # Перевірка будівельних сайтів (20s)
```

---

## boards.yml

Зберігається і завантажується `BoardService`. Не редагуйте вручну під час роботи сервера.

| Поле | Тип | Опис |
|------|-----|------|
| `name` | String | Відображувана назва дошки |
| `type` | String | `SETTLEMENT` (поки єдиний тип) |
| `world` | String | Ім'я Bukkit-світу |
| `anchor.x/y/z` | int | Координати лівого-нижнього блоку дошки |
| `facing` | String | `NORTH/SOUTH/EAST/WEST` — напрямок дошки |
| `column-side` | String | `LEFT/RIGHT` — з якого боку стовп fence |
| `bell.x/y/z` | int | Координати bell |
| `bell-distance` | double | Відстань bell від anchor |
| `region-tag` | String | Тег регіону для REGIONAL офферів |
| `contract-pools` | List | Пули шаблонів для цієї дошки |
| `difficulty-modifier` | double | Множник складності |
| `reward-modifier` | double | Множник нагороди |
| `reputation-modifier` | double | Множник репутації |
| `local-offer-slots` | int | Кількість LOCAL слотів (max 5) |
| `refresh-interval-seconds` | int | Інтервал оновлення офферів |
| `next-refresh-at` | long | Unix timestamp наступного оновлення |
| `active` | boolean | Чи активна дошка |

---

## Contract Template YAML (contracts/*.yml)

```yaml
id: "iron_ingots_c"
display-name: "Iron Supply Contract"
rank: "C"
scope: "LOCAL"
type: "DELIVERY"
contract-pools:
  - "general"
  - "metalwork"
region-tags: []                # Порожньо = всі регіони

requirements:
  - material: "IRON_INGOT"
    amount: 128
  - material: "COAL"          # Опціонально другий матеріал
    amount: 32

rewards:
  base-money: 300.0
  bonus-money: 60.0
  xp: 80
  commands:
    - "give %player% experience_bottle 3"

duration-minutes: 90
max-simultaneous-players: 2
weight: 8                      # Базова вага в roll-таблиці
```

---

## Progression Config (progression.yml)

```yaml
rank-roll-table:
  - level: 1
    weights: { F: 70, E: 25, D: 5, C: 0, B: 0, A: 0, S: 0 }
  - level: 5
    weights: { F: 40, E: 35, D: 18, C: 5, B: 2, A: 0, S: 0 }
  - level: 10
    weights: { F: 15, E: 25, D: 30, C: 20, B: 8, A: 2, S: 0 }
  - level: 15
    weights: { F: 5, E: 10, D: 20, C: 30, B: 20, A: 10, S: 5 }
  - level: 20
    weights: { F: 0, E: 5, D: 10, C: 25, B: 30, A: 20, S: 10 }

contract-xp:
  F: 10
  E: 20
  D: 35
  C: 55
  B: 80
  A: 110
  S: 150

project-triggers:
  - project-id: "first_road"
    min-trust: 30
    min-routine-completions: 50
    cooldown-minutes: 720
    region-tag: ""             # Порожньо = всі регіони
  - project-id: "market_stall"
    min-trust: 75
    min-routine-completions: 150
    cooldown-minutes: 1440
    region-tag: "spawn-region"
```

---

## messages_en.yml (структура ключів)

```yaml
prefix: "&8[&6Frontier&8] &r"

board:
  not-found: "Board not found."
  invalid-structure: "Board structure is damaged."
  refreshed: "Board {board} has been refreshed."

contract:
  accepted: "Contract accepted! Deliver to {board} before {deadline}."
  completed: "Contract completed! You earned {money} and {xp} XP."
  abandoned: "Contract abandoned."
  expired: "Your contract has expired."
  no-active: "You have no active contracts."
  delivery-partial: "Delivered {amount} items. {remaining} more needed."

order:
  created: "Order created for {amount}x {material} at {price} coins."
  accepted: "Order accepted. Deliver within {minutes} minutes."
  completed: "Order completed! You received {price} coins."
  cancelled: "Order cancelled. Funds returned."
  expired: "Your order has expired."
  limit-reached: "You have reached the maximum number of open orders."

economy:
  balance: "Your balance: {balance} {currency}"
  insufficient-funds: "Insufficient funds."

admin:
  board-placed: "Board {id} registered successfully."
  board-scan: "Scanning block at your location..."
  structure-valid: "Structure is valid."
  structure-invalid: "Structure is invalid: {reason}"
```