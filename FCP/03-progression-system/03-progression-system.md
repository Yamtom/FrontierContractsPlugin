# Progression System — Прогресія, репутація, pity система

## Три рівні прогресії

```
PlayerStats             — Особистий рівень та XP гравця
SettlementReputation    — Репутація гравця у конкретному поселенні (per board)
SettlementProgress      — Прогрес самого поселення (trust + project unlocks)
```

---

## PlayerStats

DB-таблиця `player_stats`. Зберігає інформацію про рівень і XP гравця.

| Поле | Тип | Опис |
|------|-----|------|
| `player_uuid` | TEXT PK | UUID гравця |
| `xp` | INTEGER | Накопичений досвід |
| `level` | INTEGER | Поточний рівень |
| `contracts_completed` | INTEGER | Всього виконаних контрактів |
| `recent_variants_blob` | TEXT | JSON-черга останніх N варіантів (anti-repeat) |
| `pity_counter` | INTEGER | Лічильник для pity системи |
| `last_high_rank_at` | INTEGER | Unix timestamp останнього high-rank контракту |

### Формула рівня

```
level = min(floor(totalXp / xpPerLevel), maxLevel)
```

- `xpPerLevel` — config (default 500)
- `maxLevel` — config (default 20)

### Rank Roll Table (за рівнем)

Таблиця ймовірностей рангів контрактів залежно від рівня гравця:

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
```

Проміжні рівні — лінійна інтерполяція між двома найближчими записами.

---

## Pity System (Anti-Frustration)

Захищає гравця від тривалих серій без рідкісних рангів.

```
Після кожного виконаного контракту:
  pityCounter++

Якщо pityCounter >= highRankPityThreshold (default 12):
  -> додати +12 до ваги рангів A/B при наступному roll

Якщо pityCounter >= eliteRankPityThreshold (default 25):
  -> додати +8 до ваги рангу S при наступному roll

При випаданні рідкісного рангу (B або вище):
  pityCounter = 0
```

Значення зберігаються у `player_stats.pity_counter`.

---

## Deathless Bonus

Якщо гравець виконав контракт **без жодної смерті** (deaths_since_accept == 0):
- Грошова нагорода множиться на `1.2`

`deaths_since_accept` скидається в 0 при прийнятті нового контракту.
Слухач `PlayerDeathEvent` збільшує лічильник для всіх ACTIVE контрактів гравця.

---

## recentVariants Queue (Anti-Repeat)

Запобігає повторенню одних і тих самих офферів.

- Зберігається як JSON-масив рядків у `recent_variants_blob`
- Розмір черги: config `recentVariantsQueueSize` (default 20)
- При генерації нового оффера: шаблони з черги отримують нульову вагу
- При виконанні контракту: template_id додається до кінця черги, найстаріший видаляється

---

## SettlementReputation

DB-таблиця `settlement_reputation`. **Per-player, per-board** репутація.

| Поле | Тип | Опис |
|------|-----|------|
| `player_uuid` | TEXT | UUID гравця |
| `board_id` | TEXT | ID дошки |
| `reputation` | INTEGER | Поточна репутація (min 0) |
| `lifetime_completed` | INTEGER | Всього виконаних контрактів на цій дошці |

### Зміни репутації

| Подія | Delta |
|-------|-------|
| Виконано контракт | +10 |
| Відмова від контракту | -5 |
| Прострочений контракт | -3 |

Репутація не може бути нижче 0.

---

## SettlementProgress

DB-таблиця `settlement_progress`. **Per-board** прогрес поселення.

| Поле | Тип | Опис |
|------|-----|------|
| `board_id` | TEXT PK | ID дошки |
| `trust_level` | INTEGER | Поточний рівень довіри |
| `routine_completions` | INTEGER | Всього рутинних контрактів виконано |
| `unlocked_projects` | TEXT | JSON-масив розблокованих PROJECT IDs |
| `active_project_id` | TEXT | Nullable, поточний активний проект |

### ProjectTrigger (розблокування PROJECT контрактів)

```yaml
project-triggers:
  - project-id: "bridge_project"
    min-trust: 50
    min-routine-completions: 100
    cooldown-minutes: 1440
    region-tag: "north-region"
```

Коли `trust_level >= minTrust` AND `routine_completions >= minRoutineCompletions`:
-> PROJECT-контракт стає доступним на відповідних дошках.

---

## Ключові класи

| Клас | Пакет | Роль |
|------|-------|------|
| `PlayerStats` | `model` | POJO статистики гравця |
| `SettlementReputation` | `model` | POJO репутації per-board |
| `SettlementProgress` | `model` | POJO прогресу поселення |
| `ProgressionService` | `core` | Логіка XP, leveling, pity, reputation |
| `ContractRankRoller` | `core` | Зважений roll рангу з pity |
| `ProjectTrigger` | `model` | Умови розблокування PROJECT |