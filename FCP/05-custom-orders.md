# Custom Orders — P2P ринок з ескроу

## Призначення

Custom Orders — це **гравець-до-гравця торговельний майданчик**, де один гравець (замовник) створює запит на купівлю предметів, інший (виконавець) приносить ці предмети і отримує оплату.

**Ескроу-механізм**: гроші замовника блокуються на момент створення замовлення і повертаються якщо виконавець не здасть вчасно.

---

## Стейт машина (CustomOrder status)

```
                   create()
   ┌───────────── OPEN ─────────────┐
   │              │ accept()        │
   │              ▼                 │
   │           ACCEPTED             │
   │         /          \           │
   │    submit()      cancel()      │
   │       │           expire()     │
   ▼       ▼              ▼         │
FAILED  COMPLETED    CANCELLED ─────┘
         EXPIRED
```

| Стан | Опис |
|------|------|
| `OPEN` | Замовлення відкрите, чекає виконавця |
| `ACCEPTED` | Виконавець прийняв, виконує |
| `COMPLETED` | Предмети здано, оплата виконана |
| `CANCELLED` | Відмінено замовником або виконавцем |
| `EXPIRED` | Вийшов дедлайн |
| `FAILED` | Технічна помилка |

---

## Ескроу flow

```
1. CREATE:
   economyGateway.withdraw(creator, totalPrice)   <- гроші заморожено
   DB: custom_orders.status = OPEN

2. ACCEPT:
   DB: assigned_player = assignee UUID
   DB: status = ACCEPTED
   deadline = now + durationMinutes * 60

3. SUBMIT (виконавець здає предмети):
   Перевірити інвентар assignee -> items enough?
   player.getInventory().removeItem(required)
   economyGateway.deposit(assignee, totalPrice)   <- оплата виконавцю
   DB: status = COMPLETED

4. CANCEL (замовник або виконавець скасовує):
   economyGateway.depositOffline(creatorUuid, totalPrice)  <- повернення
   DB: status = CANCELLED

5. EXPIRE (BukkitScheduler або on-login check):
   economyGateway.depositOffline(creatorUuid, totalPrice)  <- повернення
   DB: status = EXPIRED
```

`depositOffline` — спеціальний метод: якщо гравець онлайн, одразу deposit; якщо офлайн — зберегти транзакцію у `pending_transactions` таблиці (або InternalWallet).

---

## Ліміти та валідація

| Параметр | Default | Опис |
|----------|---------|------|
| `maxOpenOrdersPerPlayer` | 5 | Скільки OPEN замовлень може мати гравець |
| `minPrice` | 5.0 | Мінімальна ціна замовлення |
| `maxPrice` | 5000.0 | Максимальна ціна |
| `minDurationMinutes` | 15 | Мін. час виконання |
| `maxDurationMinutes` | 1440 | Макс. час (24 год) |
| `minItemAmount` | 1 | Мін. кількість предметів |
| `maxItemAmount` | 1728 | Макс. (27 стаків) |

---

## DB схема (custom_orders)

```sql
CREATE TABLE custom_orders (
    order_id        TEXT PRIMARY KEY,
    creator_uuid    TEXT NOT NULL,
    assigned_uuid   TEXT,
    material        TEXT NOT NULL,
    amount          INTEGER NOT NULL,
    price           REAL NOT NULL,
    status          TEXT NOT NULL DEFAULT 'OPEN',
    created_at      INTEGER NOT NULL,
    deadline        INTEGER,
    completed_at    INTEGER,
    board_id        TEXT
);
```

---

## GUI система

### CustomOrdersMenuHolder (browse / my orders)

Інвентарний GUI (54 slots):

```
Slot 0-44: Список замовлень (до 45 per page)
Slot 45: [Попередня сторінка]
Slot 49: [Закрити]
Slot 53: [Наступна сторінка]
```

Режими:
- `/contracts order browse` — всі OPEN замовлення
- `/contracts order mine` — замовлення гравця (OPEN + ACCEPTED)

### CustomOrderDetailHolder (деталі конкретного замовлення)

```
Slot 13: Іконка товару (Material + amount)
Slot 11: [Прийняти замовлення] (зелений wool)
Slot 15: [Скасувати] (червоний wool, тільки для creator)
Slot 22: [Виконати] (золото, тільки для assignee)
```

---

## CustomOrderService

| Метод | Опис |
|-------|------|
| `createOrder(creator, material, amount, price, durationMin)` | Валідація + ескроу withdraw + DB insert |
| `acceptOrder(player, orderId)` | Призначити виконавця + deadline |
| `submitOrder(player, orderId)` | Перевірити інвентар + transfer + complete |
| `cancelOrder(player, orderId)` | Повернути ескроу + cancel |
| `expireOverdueOrders()` | Планувальник: знайти прострочені + expire |
| `getOpenOrders(boardId)` | Публічні замовлення для board |
| `getPlayerOrders(playerUuid)` | Замовлення конкретного гравця |

---

## Ключові класи

| Клас | Пакет | Роль |
|------|-------|------|
| `CustomOrder` | `model` | POJO замовлення |
| `CustomOrderService` | `customorders` | Вся бізнес-логіка |
| `CustomOrdersMenuHolder` | `gui` | GUI список замовлень |
| `CustomOrderDetailHolder` | `gui` | GUI деталі + дії |
| `MenuListener` | `listener` | InventoryClickEvent -> дії GUI |