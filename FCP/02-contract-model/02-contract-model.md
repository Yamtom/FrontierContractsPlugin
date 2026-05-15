# Contract Model — Ієрархія контрактів і delivery flow

## Три рівні ієрархії

```
ContractTemplate  (YAML config — статичний архетип)
       |
       | createOffer(board, scope)
       v
ContractOffer     (DB row — live інстанція, прив'язана до дошки)
       |
       | player accepts
       v
PlayerContract    (DB row — персоналізований, з дедлайном і прогресом)
```

---

## ContractTemplate (YAML)

Визначає **що є контрактом**: назву, ранг, матеріали, нагороди.

```yaml
id: "oak_logs_local"
display-name: "Oak Wood Supply"
rank: "C"
scope: "LOCAL"
type: "DELIVERY"              # DELIVERY | CONSTRUCTION | PROJECT
contract-pools: ["general"]
region-tags: []               # порожньо = доступний у всіх регіонах

requirements:
  - material: "OAK_LOG"
    amount: 64

rewards:
  base-money: 150.0
  bonus-money: 30.0
  xp: 45
  commands: []                # список команд виконання через консоль

duration-minutes: 60
max-simultaneous-players: 3   # скільки гравців можуть прийняти цей оффер одночасно
weight: 10                    # базова вага у лотереї офферів
```

---

## ContractScope

| Значення | Slots на board | Lifetime | Видимість |
|----------|---------------|----------|-----------|
| `LOCAL` | 3 | 15 хв | Тільки ця дошка |
| `REGIONAL` | 2 | 60 хв | Всі дошки регіону |
| `GLOBAL` | 1 | 4 год | Всі дошки сервера |

---

## ContractRank

Шкала: `F < E < D < C < B < A < S`

Визначає складність і базову нагороду. Ранг F — найлегший, S — найважчий і найрідкісніший (регулюється pity системою).

---

## ContractRequirement

```java
record ContractRequirement(
    Material material,
    int amount,
    int deliveredAmount   // поточний прогрес гравця
) {
    boolean isFulfilled() { return deliveredAmount >= amount; }
    int remaining() { return amount - deliveredAmount; }
}
```

---

## ContractOffer (spawned instance)

DB-рядок у таблиці `generated_offers`:

| Поле | Тип | Призначення |
|------|-----|-------------|
| `offer_id` | TEXT PK | UUID |
| `template_id` | TEXT | Посилання на template |
| `board_id` | TEXT | До якої дошки прив'язаний |
| `scope` | TEXT | LOCAL/REGIONAL/GLOBAL |
| `rank` | TEXT | F..S |
| `status` | TEXT | ACTIVE/ACCEPTED/COMPLETED/EXPIRED |
| `requirements_blob` | TEXT | JSON-масив вимог |
| `reward_blob` | TEXT | JSON нагороди |
| `expires_at` | INTEGER | Unix timestamp |
| `max_players` | INTEGER | Ліміт одночасних гравців |
| `active_players` | INTEGER | Поточна кількість |

---

## PlayerContract (accepted)

DB-рядок у таблиці `player_contracts`:

| Поле | Тип | Призначення |
|------|-----|-------------|
| `contract_id` | TEXT PK | UUID |
| `player_uuid` | TEXT | UUID гравця |
| `offer_id` | TEXT | FK до generated_offers |
| `board_id` | TEXT | Дошка de pickup |
| `rank` | TEXT | Ранг на момент прийняття |
| `type` | TEXT | DELIVERY/CONSTRUCTION |
| `requirements_blob` | TEXT | Поточний прогрес (JSON) |
| `reward_blob` | TEXT | RewardBundle (JSON) |
| `status` | TEXT | ACTIVE/COMPLETED/ABANDONED/EXPIRED |
| `accepted_at` | INTEGER | Unix timestamp |
| `deadline` | INTEGER | Unix timestamp |
| `completed_at` | INTEGER | Nullable |
| `deaths_since_accept` | INTEGER | Для deathless bonus |

---

## Delivery Flow (tryDeliver)

```
1. Гравець лівим кліком по плашці/блоку дошки
2. BlockInteractListener -> boardService.findBoardAtLocation(block)
3. contractService.tryDeliver(player, board)
4. Знайти всі ACTIVE PlayerContracts гравця для цієї дошки
5. Для кожного контракту:
   a. Перебрати requirements -> ті, що !isFulfilled()
   b. Порахувати скільки є у інвентарі гравця (player.getInventory())
   c. Зняти min(needed, inInventory) предметів
   d. Збільшити deliveredAmount
6. Зберегти в DB
7. Оновити знаки дошки
8. Якщо всі requirements fulfilled -> completeOffer()
```

---

## completeOffer()

```
1. Обчислити RewardBundle:
   - baseMoney * board.rewardModifier * player.bonusMultiplier
   - Якщо deaths_since_accept == 0 -> deathless bonus (1.2x)
   - Додати xp

2. economyGateway.deposit(player, finalMoney)

3. PlayerStats.addXp(xp) -> можливо levelUp

4. SettlementReputation.adjust(player, board, +10)

5. Виконати commands[] через Bukkit.dispatchCommand(console, cmd)
   (підстановка %player%, %board_id%)

6. Якщо trust досягнуто -> розблокувати PROJECT контракти

7. offer.activePlayersCount--
   contract.status = COMPLETED
   Зберегти в DB
```

---

## RewardBundle (record)

```java
record RewardBundle(
    double baseMoney,
    double bonusMoney,
    int xp,
    List<String> rewardCommands
) {}
```

---

## Ключові класи

| Клас | Пакет | Роль |
|------|-------|------|
| `ContractTemplate` | `model` | YAML-архетип |
| `ContractOffer` | `model` | Live spawned інстанція |
| `PlayerContract` | `model` | Прийнятий контракт гравця |
| `ContractRequirement` | `model` | Вимога з прогресом |
| `ContractScope` | `model.enums` | LOCAL/REGIONAL/GLOBAL |
| `ContractRank` | `model.enums` | F..S |
| `RewardBundle` | `model` | Record нагороди |
| `CoreContractService` | `core` | Генерація, delivery, completion |
| `ContractTemplateRegistry` | `core` | Завантаження YAML шаблонів |