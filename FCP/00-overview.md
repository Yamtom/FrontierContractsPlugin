# FrontierContractsPlugin — Overview

## Що таке цей плагін

**FrontierContracts** — це Minecraft Paper-плагін (v1.0.0), що реалізує **систему фізичних дошок оголошень** у світі гри. Гравці підходять до реальних дерев'яних конструкцій у грі, беруть контракти на постачання або будівництво, виконують їх — і отримують нагороди в ігровій валюті та репутації.

Відмінність від класичних квест-плагінів: всі взаємодії прив'язані до **фізичних об'єктів у світі** (дошка = реальна конструкція з блоків), а прогрес поселення (Settlement Trust) відкриває складніші контракти.

---

## Tech Stack

| Компонент | Технологія |
|-----------|-----------|
| Minecraft API | Paper 1.21.4 |
| Мова | Java 21 |
| База даних | SQLite 3.46.1 (embedded, JDBC) |
| Економіка | Vault 1.5.4 (опціонально) |
| Плейсхолдери | PlaceholderAPI 2.11.6 (опціонально) |
| Білд | Gradle (fatJar task — SQLite вбудований у JAR) |

---

## Три модулі архітектури

```
FrontierContractsPlugin
├── CORE MODULE            — Ranked контракти + Board система
│   ├── BoardService       — Фізичні дошки у світі
│   ├── CoreContractService — Генерація офферів, delivery, нагороди
│   └── ProgressionSystem  — PlayerStats, SettlementReputation, Trust
│
├── CONSTRUCTION MODULE    — Будівельні проекти, валідація світу
│   ├── ConstructionSitePlanner — Пошук місця для будівництва
│   └── ConstructionContractService — Генерація та перевірка
│
└── CUSTOM ORDERS MODULE   — P2P ринок з ескроу
    └── CustomOrderService — Створення, прийняття, виконання замовлень
```

---

## Потік даних (Data Flow)

```
ContractTemplate (YAML config)
        │  createOffer()
        ▼
ContractOffer (spawned instance, DB)
        │  player right-clicks board
        ▼
PlayerContract (accepted, DB)
        │  player delivers items / builds
        ▼
completeOffer() → RewardBundle → Economy + Reputation + Trust
```

---

## Ключові концепції

| Концепція | Пояснення |
|-----------|-----------|
| **Board** | Фізична дерев'яна конструкція 3x2 у світі; центр взаємодії |
| **ContractTemplate** | Шаблон контракту в YAML (архетип, не інстанція) |
| **ContractOffer** | Spawned інстанція шаблону, прив'язана до дошки або глобальна |
| **PlayerContract** | Прийнятий гравцем контракт із дедлайном і прогресом |
| **ContractScope** | LOCAL / REGIONAL / GLOBAL — де видно оффер |
| **ContractRank** | F -> S — складність контракту |
| **Settlement Trust** | Прогрес поселення -> розблокування PROJECT-контрактів |
| **Pity System** | Захист від невдачі RNG: після N контрактів без рідкісного — бонусна вага |
| **Deathless Bonus** | Виконав без смертей -> 1.2x до грошової нагороди |
| **Escrow** | Гроші замовника заблоковані до виконання custom order |

---

## Документи цієї серії

| Файл | Тема |
|------|------|
| `00-overview.md` | Цей файл — огляд архітектури |
| `01-board-system.md` | Фізична Board система |
| `02-contract-model.md` | Моделі контрактів і delivery flow |
| `03-progression-system.md` | Прогресія, репутація, pity система |
| `04-construction-module.md` | Будівельний модуль |
| `05-custom-orders.md` | P2P ринок з ескроу |
| `06-economy-system.md` | Економічний шар |
| `07-data-persistence.md` | SQLite схема і YAML persistence |
| `08-commands-and-events.md` | Команди, events, GUI |
| `09-config-reference.md` | Повний довідник конфігів |
| `10-prompt-guide.md` | LLM-промпти для відтворення плагіна |