# Board System — Чеклист тестування

> **⚠ Нагадування для AI-агента:**
> Якщо будь-який пункт цього документа змінюється (додається кейс, виправляється опис, змінюється очікуваний результат) — **обов'язково**:
> 1. Додай запис у секцію `## Лог змін` внизу файлу (дата + короткий опис).
> 2. Якщо змінюється алгоритм або геометрія — звір із [01-board-system.md](./01-board-system.md) та оновлюй синхронно.
> 3. Не видаляй старі записи логу — лише додавай нові зверху.

---

## BoardLayout

### BL-01 — `fromAnchor()` повертає рівно 6 plank-блоків
- [ ] Виконано
- **Кроки:** Викликати `BoardLayout.fromAnchor(anchor, BlockFace.NORTH, BlockFace.EAST)`.
- **Очікується:** `layout.plankBlocks().size() == 6`.

### BL-02 — `fromAnchor()` повертає рівно 6 sign-блоків
- [ ] Виконано
- **Кроки:** Той самий виклик, що і BL-01.
- **Очікується:** `layout.signBlocks().size() == 6`.

### BL-03 — `headerSign()` = CENTER_TOP (signs[1])
- [ ] Виконано
- **Кроки:** Взяти `signBlocks().get(1)` і порівняти з `headerSign()`.
- **Очікується:** `layout.headerSign().equals(layout.signBlocks().get(1)) == true`.

### BL-04 — `taskSignBlocks()` повертає рівно 5 блоків
- [ ] Виконано
- **Кроки:** Викликати `taskSignBlocks()` після `fromAnchor()`.
- **Очікується:** розмір == 5.

### BL-05 — `taskSignBlocks()` не містить `headerSign()`
- [ ] Виконано
- **Кроки:** Перевірити `taskSignBlocks().contains(headerSign()) == false`.
- **Очікується:** `false`.

### BL-06..09 — Ротація facing для кожної сторони
- [ ] Виконано
- **Кроки:** Для кожного `facing` (NORTH, SOUTH, EAST, WEST) побудувати layout і перевірити координати першого і третього plank у рядку.
- **Очікується:**

  | facing | left plank offset | right plank offset |
  |--------|-------------------|--------------------|
  | NORTH  | +X (EAST side)    | -X (WEST side)     |
  | SOUTH  | -X               | +X                 |
  | EAST   | +Z               | -Z                 |
  | WEST   | -Z               | +Z                 |

### BL-10 — `fromBoard()` повертає null якщо world = null
- [ ] Виконано
- **Кроки:** Створити `Board` з worldName, що не завантажений у Server, викликати `fromBoard(board, server)`.
- **Очікується:** `null`.

### BL-11 — `keyFor()` формат
- [ ] Виконано
- **Кроки:** Викликати `BoardLayout.keyFor(location)` де world="world", x=10, y=64, z=-5.
- **Очікується:** `"world:10:64:-5"`.

### BL-12 — `interactionKeys()` = 13 унікальних ключів
- [ ] Виконано
- **Кроки:** Викликати `interactionKeys()` після `fromAnchor()`.
- **Очікується:** розмір == 13 (1 support + 6 planks + 6 signs).

---

## BoardStructureValidator

### BSV-01 — World не завантажений
- [ ] Виконано
- **Кроки:** Викликати `validate(board)` де `board.worldName()` не є завантаженим світом.
- **Очікується:** `result.valid() == false`, errors містить `"World is not loaded."`.

### BSV-02 — Fence відсутній
- [ ] Виконано
- **Кроки:** Поставити STONE на місце fence, викликати `validate(anchor, NORTH, RIGHT)`.
- **Очікується:** `result.valid() == false`, errors містить фразу про support block.

### BSV-03 — Один plank замінено на інший матеріал
- [ ] Виконано
- **Кроки:** Замінити один OAK_PLANKS на STONE_BRICKS, викликати `validate()`.
- **Очікується:** `result.valid() == false`, errors містить фразу про surface.

### BSV-04 — Sign повернутий не в той бік
- [ ] Виконано
- **Кроки:** Встановити знак з facing != facing дошки, викликати `validate()`.
- **Очікується:** `result.valid() == false`, errors містить фразу про front signs.

### BSV-05 — Header sign без "contact"
- [ ] Виконано
- **Кроки:** Header sign має текст "hello" у рядку 0, викликати `validate()`.
- **Очікується:** `result.valid() == false`, errors містить фразу про header sign.

### BSV-06 — Bell відсутній у радіусі
- [ ] Виконано
- **Кроки:** Немає жодного BELL-блоку в межах `bellRadius`, викликати `validate()`.
- **Очікується:** `result.valid() == false`, errors містить фразу про bell.

### BSV-07 — Bell є, але далі bellRadius
- [ ] Виконано
- **Кроки:** Поставити BELL на відстані `bellRadius + 5`, викликати `validate()`.
- **Очікується:** `result.valid() == false`, errors містить фразу про bell.

### BSV-08 — Bell є, але village POI відсутній
- [ ] Виконано
- **Кроки:** Bell у радіусі, але жодного LECTERN/BED/тощо поряд.
- **Очікується:** `result.valid() == false`, errors містить фразу про village area.

### BSV-09 — Повна валідна структура
- [ ] Виконано
- **Кроки:** Побудувати повністю правильну структуру, викликати `validate()`.
- **Очікується:** `result.valid() == true`, `result.errors().isEmpty() == true`.

### BSV-10 — `scan()` знаходить валідний варіант
- [ ] Виконано
- **Кроки:** Побудувати валідну дошку на відстані 3 блоків від target, викликати `scan(target)`.
- **Очікується:** `result.valid() == true`.

---

## BoardService

### BS-01 — `register()` → `getBoard(id)`
- [ ] Виконано
- **Кроки:** Викликати `register(board)`, потім `getBoard(board.id())`.
- **Очікується:** `Optional` містить той самий об'єкт.

### BS-02 — `addBoard()` дублікат
- [ ] Виконано
- **Кроки:** Викликати `addBoard(board)` двічі з однаковим id.
- **Очікується:** перший виклик → `true`, другий → `false`.

### BS-03 — `removeBoard()` видаляє дошку
- [ ] Виконано
- **Кроки:** `register(board)`, `removeBoard(board.id())`, `getBoard(board.id())`.
- **Очікується:** `Optional.empty()`.

### BS-04 — `getBoardAt()` знаходить за координатами
- [ ] Виконано
- **Кроки:** `register(board)`, викликати `getBoardAt(location)` де location = anchor дошки.
- **Очікується:** `Optional` містить board.

### BS-05 — `getBoardAt()` повертає empty для чужої локації
- [ ] Виконано
- **Кроки:** `getBoardAt(location)` де координати не збігаються з жодною дошкою.
- **Очікується:** `Optional.empty()`.

### BS-06 — `getNearestActiveBoard()` ігнорує inactive
- [ ] Виконано
- **Кроки:** Додати дошку з `active = false`, викликати `getNearestActiveBoard()` поряд.
- **Очікується:** `Optional.empty()`.

### BS-07 — `getNearestActiveBoard()` ігнорує інший world
- [ ] Виконано
- **Кроки:** Дошка в world "nether", запит з world "world".
- **Очікується:** `Optional.empty()`.

### BS-08 — `getNearestActiveBoard()` поза radius
- [ ] Виконано
- **Кроки:** Дошка на відстані `maxRadius + 100`, викликати з малим radius.
- **Очікується:** `Optional.empty()`.

### BS-09 — `getRegionalPeers()` по regionTag
- [ ] Виконано
- **Кроки:** Дві дошки з однаковим `regionTag`, викликати `getRegionalPeers(board1)`.
- **Очікується:** список містить board2, не містить board1.

### BS-10 — `getRegionalPeers()` по linkedBoardIds
- [ ] Виконано
- **Кроки:** `board1.linkedBoardIds = [board2.id]`, викликати `getRegionalPeers(board1)`.
- **Очікується:** список містить board2.

### BS-11 — `getRegionalPeers()` не повертає саму дошку
- [ ] Виконано
- **Кроки:** Будь-який виклик `getRegionalPeers(board)`.
- **Очікується:** результат не містить board з тим самим id.

### BS-12 — `allBoards()` unmodifiable
- [ ] Виконано
- **Кроки:** Спробувати `allBoards().add(...)`.
- **Очікується:** `UnsupportedOperationException`.

### BS-13 — `activeBoards()` фільтрує inactive та invalid
- [ ] Виконано
- **Кроки:** Додати 3 дошки: одна active+valid, одна active+invalid, одна inactive+valid.
- **Очікується:** `activeBoards()` повертає лише першу.

---

## BoardSignService

### BSS-01 — Delivery: line 2 = "delivered/total"
- [ ] Виконано
- **Кроки:** Створити DELIVERY оффер з delivered=3, total=10. Викликати `syncBoard()`.
- **Очікується:** sign line 2 == `"3/10"`.

### BSS-02 — Обрізання до 15 символів
- [ ] Виконано
- **Кроки:** Оффер з назвою матеріалу з 20+ символів.
- **Очікується:** кожен рядок знаку ≤ 15 символів.

### BSS-03 — Word-wrap назви матеріалу
- [ ] Виконано
- **Кроки:** Матеріал "OAK_LOG" → prettyToken = "Oak Log". Викликати `syncBoard()`.
- **Очікується:** line 0 = "Oak", line 1 = "Log" (або "Oak Log" якщо влазить в 15 символів).

### BSS-04 — Construction: line 2 з `{done}/{total} req`
- [ ] Виконано
- **Кроки:** CONSTRUCTION оффер, completedRequirementCount=1, totalRequirementCount=3.
- **Очікується:** sign line 2 == `"1/3 req"`.

### BSS-05 — Дошка не active → signs.invalid
- [ ] Виконано
- **Кроки:** `board.active = false`, викликати `syncBoard()`.
- **Очікується:** всі task signs мають текст з `messages.rawList("signs.invalid")`.

### BSS-06 — Менше офферів ніж слотів → signs.idle
- [ ] Виконано
- **Кроки:** 2 активних оффери на дошці з 5 слотами, `syncBoard()`.
- **Очікується:** slots 3-5 мають текст з `messages.rawList("signs.idle")`.

### BSS-07 — `syncBoard()` ігнорує не-Sign блок
- [ ] Виконано
- **Кроки:** Один з sign-блоків у layout = STONE (не знак), викликати `syncBoard()`.
- **Очікується:** метод не кидає виняток, інші знаки оновлені.

### BSS-08 — Header sign = "contact"
- [ ] Виконано
- **Кроки:** Викликати `syncBoard()` на валідній дошці.
- **Очікується:** header sign line 0 == `"contact"`.

---

## BoardProtectionListener

### BPL-01 — Гравець без admin не ламає блок дошки
- [ ] Виконано
- **Кроки:** Гравець без `frontiercontracts.admin` → `BlockBreakEvent` на блок дошки.
- **Очікується:** `event.isCancelled() == true`, гравцю надіслано `errors.board-protected`.

### BPL-02 — Гравець без admin не змінює sign дошки
- [ ] Виконано
- **Кроки:** Гравець без permission → `SignChangeEvent` на знак дошки.
- **Очікується:** `event.isCancelled() == true`.

### BPL-03 — Гравець з admin може ламати
- [ ] Виконано
- **Кроки:** Гравець з `frontiercontracts.admin` → `BlockBreakEvent` на блок дошки.
- **Очікується:** `event.isCancelled() == false`.

### BPL-04 — Блок не частина дошки → не скасовується
- [ ] Виконано
- **Кроки:** `BlockBreakEvent` на звичайний блок поряд з дошкою.
- **Очікується:** `event.isCancelled() == false`.

---

## BlockInteractListener

### BIL-01 — Right-click порожня рука → відкриває BoardMenu
- [ ] Виконано
- **Кроки:** `PlayerInteractEvent` з `RIGHT_CLICK_BLOCK`, clickedBlock = sign дошки, рука порожня.
- **Очікується:** `menuService.openBoardMenu()` викликано, event cancelled.

### BIL-02 — Right-click з предметом → deliverToBoard
- [ ] Виконано
- **Кроки:** `PlayerInteractEvent` з предметом у руці на sign дошки.
- **Очікується:** `contractService.deliverToBoard()` викликано.

### BIL-03 — Right-click невалідна дошка → повідомлення
- [ ] Виконано
- **Кроки:** `board.active = false` або `isValidStructure = false`, right-click.
- **Очікується:** гравцю надіслано `errors.board-invalid`, меню НЕ відкрито.

### BIL-04 — Left-click ігнорується
- [ ] Виконано
- **Кроки:** `PlayerInteractEvent` з `LEFT_CLICK_BLOCK` на sign дошки.
- **Очікується:** жодних дій від listener-а.

### BIL-05 — Клік на блок не частини дошки ігнорується
- [ ] Виконано
- **Кроки:** `PlayerInteractEvent` на блок, якого немає в locationIndex.
- **Очікується:** жодних дій від listener-а.

---

## Лог змін

| Дата | Версія | Що змінено |
|------|--------|------------|
| 2026-05-15 | v1.1 | Додано кроки виконання, очікувані результати та лог змін до всіх 47 кейсів |
| 2026-05-15 | v1.0 | Початкове створення чеклісту — 47 кейсів по 5 модулях |
