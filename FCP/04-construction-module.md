# Construction Module — Будівельні проекти та валідація світу

## Призначення

Construction Module реалізує **проектні контракти** (PROJECT type): великі будівельні замовлення, де гравець або група гравців мають **збудувати реальну конструкцію** у визначеному місці.

На відміну від DELIVERY (здати предмети), тут перевіряється **реальний стан блоків у світі**.

---

## Типи проектів

| Тип | Опис | Приклад |
|-----|------|---------|
| `ROAD` | Прокласти дорогу між двома точками | 20 блоків кам'яної бруківки |
| `BUILDING` | Збудувати будівлю на виділеній ділянці | Будинок 7x7 |

---

## ConstructionSitePlanner

**Де будувати?** Планувальник знаходить відповідне місце навколо дошки.

### Алгоритм пошуку BUILDING площадки

```
1. Взяти позицію bell дошки як центр
2. Перебрати 4 кардинальних напрямки (NORTH, SOUTH, EAST, WEST)
3. Для кожного напрямку:
   a. Відступити на plannerSearchRadius (default 15) блоків
   b. Перевірити footprint NxN (7x7 для малих, 9x9 для великих проектів)
   c. Footprint вважається валідним якщо:
      - Всі блоки footprint достатньо плоскі (відхилення висоти <= 2)
      - Немає блокуючих структур (перевірити blacklist блоків)
      - Немає вже існуючого ConstructionSite в радіусі siteSeparationRadius
4. Повернути перше валідне місце, або FAILED якщо жодного не знайдено
```

### Алгоритм пошуку ROAD маршруту

```
1. Взяти позицію bell як старт
2. Знайти кінцеву точку через waypoint з config або рандомно
3. Прокласти шлях A* (спрощений) по поверхні між точками
4. Зберегти список позицій тайлів дороги як JSON blob
```

---

## ConstructionSite (модель)

DB-таблиця `settlement_progress` (поле `site_blob`).

```java
record ConstructionSite(
    String siteId,
    String boardId,
    String projectId,
    ConstructionType type,      // ROAD | BUILDING
    BlockPosition anchorPos,    // нижній лівий кут або старт дороги
    int width, int depth,       // для BUILDING
    List<BlockPosition> tiles,  // для ROAD — список блоків
    BoundingBox boundingBox,    // 3D bounding box усього сайту
    Material requiredMaterial,
    int requiredCount,
    int placedCount
) {}
```

`tiles` і `boundingBox` серіалізуються у JSON blob у БД.

---

## Валідація стану (ConstructionValidator)

Кожного разу коли гравець робить `PlayerInteractEvent` лопатою (shovel) на сайті або виконується scheduled check:

### BUILDING validation

```
1. Знайти ConstructionSite за координатами (просторовий lookup)
2. Для кожного блоку в boundingBox:
   a. Отримати block.getType() з world
   b. Якщо == requiredMaterial -> placedCount++
3. Якщо placedCount >= requiredCount -> проект завершено
4. Оновити прогрес у DB
```

### ROAD validation

```
1. Перебрати всі позиції tiles[]
2. Для кожного tile:
   a. block.getType() == requiredMaterial AND block нижче є твердим -> valid tile
3. validTiles / totalTiles = відсоток виконання
4. Якщо >= 100% -> проект завершено
```

---

## Event Listeners

### ConstructionWorldListener

- Слухає `BlockPlaceEvent` (EventPriority.MONITOR, ignoreCancelled=true)
- Якщо розміщений блок потрапляє в `boundingBox` активного сайту:
  - Ставить 1-tick async delay (через Bukkit.getScheduler())
  - Потім викликає `constructionValidator.checkSite(site)`
  - Чому delay? Щоб блок встиг записатись у world до перевірки

### PlayerInteractEvent (shovel)

- Гравець правою кнопкою лопатою по землі на активному сайті
- Негайний feedback: показати actionbar з прогресом
- Запустити повну перевірку

---

## ConstructionContractService

Головний сервіс модуля.

| Метод | Опис |
|-------|------|
| `generateConstructionOffer(board, template)` | Запустити ConstructionSitePlanner, зберегти сайт у DB, spawn offer |
| `acceptConstruction(player, offer)` | Призначити гравцю, повідомити де будувати |
| `checkCompletion(site)` | Запустити validation, якщо 100% -> completeOffer() |
| `cancelSite(siteId)` | Скасувати проект (status = FAILED) |
| `getSitesForBoard(boardId)` | Всі активні сайти дошки |

---

## Ключові класи

| Клас | Пакет | Роль |
|------|-------|------|
| `ConstructionSite` | `model` | Record сайту з геометрією |
| `ConstructionSitePlanner` | `construction` | Пошук місця для будівництва |
| `ConstructionValidator` | `construction` | Перевірка реального стану блоків |
| `ConstructionContractService` | `construction` | Orchestration весь модуль |
| `ConstructionWorldListener` | `listener` | BlockPlaceEvent -> check |
| `ProjectTrigger` | `model` | Умова розблокування PROJECT |