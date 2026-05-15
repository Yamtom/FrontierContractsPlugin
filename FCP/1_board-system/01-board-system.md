# Board System — Фізична система дошок

## Призначення

Board — це **фізична конструкція у Minecraft-світі**, збудована з конкретних блоків у визначеній геометрії. Саме вона є точкою взаємодії гравця з контрактами: гравець підходить до дошки, правою кнопкою відкриває меню, лівою — здає ресурси.

Дошка не є просто маркером у YAML. Плагін **перевіряє реальні блоки у світі** і відхиляє дошку, якщо структура зламана.

---

## Геометрія дошки (BoardLayout)

```
Вигляд спереду:

  [HS] [HC] [HS]      <- рядок 2 (верхній): 3 блоки OAK_PLANKS
  [HS] [HC] [HS]      <- рядок 1 (нижній): 3 блоки OAK_PLANKS
         |
       [FENCE]        <- стовп OAK_FENCE під центром нижнього рядка

Знаки (Wall Signs):
  Кожен з 6 OAK_PLANKS блоків має знак, прикріплений до його лицьової сторони.
  Знак у center-top позиції = Header Sign (текст "contact").
  Решта 5 знаків = Task Signs (показують контракти).
```

**Клас:** `BoardLayout`

Зберігає абсолютні `Location` об'єкти для:
- 6 блоків planks (positions: LEFT_TOP, CENTER_TOP, RIGHT_TOP, LEFT_BOT, CENTER_BOT, RIGHT_BOT)
- 6 відповідних знаків
- Стовпа fence

Методи:
- `headerSign()` -> `Location` знаку CENTER_TOP
- `taskSignBlocks()` -> 5 `Location` без header
- `allPlanks()`, `allSigns()` -> повні списки для validation

---

## Валідація структури (BoardStructureValidator)

### Стани помилок (ValidationResult)

| Стан | Причина |
|------|---------|
| `WORLD_MISSING` | Світ не завантажений |
| `INVALID_STRUCTURE` | Геометрія зламана: неправильний матеріал, знак не той напрямок, заголовок не "contact" |
| `EMPTY_LOCAL_POOL` | Структура валідна, але нема доступних офферів |
| `GENERATION_FAILED` | Помилка при генерації нових офферів |
| `OK` | Все перевірено |

### Алгоритм валідації

```
1. Перевірити, що world.isLoaded()
2. Для кожного з 6 plank-блоків:
   a. block.getType() == OAK_PLANKS -> інакше INVALID_STRUCTURE
3. fence блок:
   a. block.getType() == OAK_FENCE
4. Для кожного з 6 знаків:
   a. block.getType() instanceof WallSign
   b. sign.getFacing() == очікуваний напрямок (відносно facing дошки)
5. Header sign:
   a. lines[0].toLowerCase().contains("contact")
6. Bell перевірка:
   a. Знайти bell у радіусі bellRadius (default 13) блоків від anchor
   b. Якщо requireVillagePoi = true -> bell має бути village POI
```

---

## Сховище дошок (BoardService)

### Структури даних

```java
Map<String, Board> boardsById        // "board_id" -> Board об'єкт
Map<String, String> locationIndex    // "world:X:Y:Z" -> "board_id"
```

`locationIndex` — просторовий індекс для O(1) lookup за координатами.

### Board YAML-запис

```yaml
boards:
  my_board_01:
    name: "Central Trade Hub"
    type: "SETTLEMENT"
    world: "world"
    anchor: { x: 100, y: 64, z: -200 }
    facing: "NORTH"
    column-side: "RIGHT"
    bell: { x: 105, y: 64, z: -200 }
    bell-distance: 5.0
    region-tag: "north-region"
    contract-pools: ["delivery", "construction"]
    difficulty-modifier: 1.0
    reward-modifier: 1.0
    reputation-modifier: 1.0
    local-offer-slots: 5
    refresh-interval-seconds: 300
    next-refresh-at: 1746700000
    active: true
```

---

## Відображення на знаках (BoardSignService)

### Task Signs (5 знаків)

**DELIVERY контракт:**
```
Line 0: [rank icon] [contract title]
Line 1: [material name]
Line 2: [delivered]/[total]
Line 3: [reward] монет
```

**CONSTRUCTION контракт:**
```
Line 0: [назва проекту]
Line 1: [done]/[total] req
Line 2: [рекомендований інструмент]
Line 3: [reward] монет
```

- Максимум **15 символів** на рядок знаку, word-wrap
- Paper 1.20+ dual-sided signs: `sign.getSide(Side.FRONT)`

---

## Ключові класи

| Клас | Пакет | Роль |
|------|-------|------|
| `Board` | `model` | POJO: id, name, world, anchor, facing, bell, modifiers |
| `BoardLayout` | `core.board` | Геометричний blueprint |
| `BoardStructureValidator` | `core.board` | Перевірка реальних блоків у світі |
| `BoardSignService` | `core.board` | Оновлення фізичних знаків |
| `BoardService` | `core.board` | CRUD + spatial lookup, YAML persistence |
| `BoardProtectionListener` | `listener` | Захист від руйнування не-адмінами |
| `BlockInteractListener` | `listener` | Правий клік -> delivery або GUI |