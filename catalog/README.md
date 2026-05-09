# Mimi — Каталог игр

Здесь лежат JSON-файлы всех сцен. Kotlin-код для добавления игр **не нужен**.

---

## Архитектура

Сцены — это JSON. Приложение читает их из встроенных ресурсов (бандл), затем обновляет по сети.

```
catalog/                   ← источник правды, коммитить в git
├── catalog.json           ← индекс всех сцен
├── sort_fruits_01.json    ← данные сцены
└── ...
```

При сборке Gradle копирует `catalog/*.json` в бандл → офлайн-доступ без интернета.  
После пуша GitHub Actions деплоит обновления на GitHub Pages → приложения обновляют каталог
без перевыпуска.

---

## Как добавить новую игру

### Шаг 1. Написать JSON сцены

Создай файл `catalog/my_game_01.json`:

```json
{
  "sceneId": "my_game_01",
  "entities": [
    {
      "id": "apple",
      "components": ["drag"],
      "behaviors": [],
      "properties": {
        "position":  { "type": "vec2", "x": 200.0, "y": 250.0 },
        "zOrder":    { "type": "num",  "value": 1.0 },
        "emoji":     { "type": "text", "value": "🍎" },
        "category":  { "type": "text", "value": "fruit" }
      },
      "tags": { "group": "items" },
      "zOrder": 0
    },
    {
      "id": "basket",
      "components": ["dropTarget"],
      "behaviors": [],
      "properties": {
        "position":        { "type": "vec2", "x": 500.0, "y": 700.0 },
        "zOrder":          { "type": "num",  "value": 0.0 },
        "emoji":           { "type": "text", "value": "🧺" },
        "constraintType":  { "type": "text", "value": "propertyEquals" },
        "source":          { "type": "text", "value": "category" },
        "target":          { "type": "text", "value": "acceptsCategory" },
        "acceptsCategory": { "type": "text", "value": "fruit" }
      },
      "tags": {},
      "zOrder": 0
    }
  ],
  "objectives": [
    { "id": "win", "type": "allMatched", "config": { "group": "items" }, "requires": null }
  ],
  "behaviors": [
    { "behaviorRef": { "type": "progressTracker",  "config": {} }, "entityId": null },
    { "behaviorRef": { "type": "snapToTarget",     "config": {} }, "entityId": null },
    { "behaviorRef": { "type": "adaptiveHint",     "config": {} }, "entityId": null },
    { "behaviorRef": { "type": "playSoundOnEvent", "config": { "on": "sceneCompleted", "sound": "complete" } }, "entityId": null }
  ],
  "fieldPolicies": {},
  "idleTimeout": 30,
  "onIdle": "HINT",
  "metadata": {}
}
```

### Шаг 2. Добавить запись в `catalog/catalog.json`

```json
{ "id": "my_game_01", "url": "https://olluorg.github.io/mimi/my_game_01.json" }
```

Вставь эту строку в массив `scenes`. Название и возраст брать отсюда не нужно — они уже есть в scene JSON.

### Шаг 3. Проверить локально

```bash
./gradlew :desktopApp:run
```

Gradle скопирует `catalog/*.json` в бандл автоматически — игра появится в каталоге.

### Шаг 4. Опубликовать

```bash
git add catalog/my_game_01.json catalog/catalog.json
git commit -m "feat: add my_game_01"
git push
```

GitHub Actions задеплоит на GitHub Pages. Приложения получат новую сцену без обновления.

---

## Анатомия сцены

Каждый файл содержит полное описание сцены включая метаданные для отображения в каталоге.

```json
{
  "sceneId": "уникальный_id",
  "meta": {
    "title":       "Разложи фрукты",
    "description": "Перетащи фрукты в корзину",
    "ageMin":      2,
    "ageMax":      5,
    "version":     1,
    "tags":        ["sorting", "fruits"]
  },
  "entities":      [ ... ],
  "objectives":    [ ... ],
  "behaviors":     [ ... ],
  "fieldPolicies": {},
  "idleTimeout":   30,
  "onIdle":        "HINT",
  "metadata":      {}
}
```

### catalog.json

Генерируется автоматически в CI из scene-файлов. Содержит только ID и URL:

```json
{
  "version": 2,
  "scenes": [
    { "id": "sort_fruits_01", "url": "https://olluorg.github.io/mimi/sort_fruits_01.json" }
  ]
}
```

Приложение читает этот индекс, сравнивает с тем что уже есть в кэше, и подтягивает недостающие сцены.
Метаданные (название, описание, возраст) берёт из самих scene JSON — не из `catalog.json`.
```

### Координатная система

Логическое пространство — **1000 × 1000** единиц.  
Начало `(0, 0)` — верхний левый угол, ось Y направлена вниз.

```
(0,0) ──────────── (1000,0)
  │                    │
  │   типичные Y:      │
  │   250 — объекты    │
  │   700 — цели       │
  │                    │
(0,1000) ─────── (1000,1000)
```

---

## Сущности (entities)

```json
{
  "id": "apple",
  "components": ["drag"],
  "behaviors": [],
  "properties": {
    "position": { "type": "vec2", "x": 200.0, "y": 250.0 },
    "zOrder":   { "type": "num",  "value": 1.0 },
    "emoji":    { "type": "text", "value": "🍎" },
    "category": { "type": "text", "value": "fruit" }
  },
  "tags": { "group": "fruits" },
  "zOrder": 0
}
```

### Типы свойств

| Тип | Формат | Применение |
|-----|--------|------------|
| `vec2` | `{"type":"vec2","x":200.0,"y":300.0}` | позиция |
| `text` | `{"type":"text","value":"fruit"}` | строковые свойства |
| `num`  | `{"type":"num","value":1.0}` | числа, zOrder |
| `bool` | `{"type":"bool","value":true}` | флаги |
| `textList` | `{"type":"textList","values":["a","b"]}` | список для `propertyIncludes` |
| `ref`  | `{"type":"ref","entityId":"basket"}` | ссылка на сущность |

### Зарезервированные свойства

| Свойство | Тип | Описание |
|----------|-----|----------|
| `position` | vec2 | позиция (обязательно) |
| `zOrder`   | num  | порядок отрисовки (выше = поверх) |
| `emoji`    | text | эмодзи на тайле |

---

## Компоненты

### `"drag"` — перетаскиваемый объект

Пользователь может взять и перетащить. При отпускании над `dropTarget` проверяется ограничение.

```json
"components": ["drag"]
```

### `"dropTarget"` — зона сброса

Принимает drag-объекты. Ограничение задаётся через свойства:

```json
"components": ["dropTarget"],
"properties": {
  "constraintType": { "type": "text", "value": "propertyEquals" },
  "source":         { "type": "text", "value": "color" },
  "target":         { "type": "text", "value": "color" },
  "color":          { "type": "text", "value": "red" }
}
```

---

## Ограничения (constraintType)

### `propertyEquals` ★ (чаще всего)

Проходит, если свойство drag-объекта равно свойству цели.

```json
"constraintType": { "type": "text", "value": "propertyEquals" },
"source":         { "type": "text", "value": "color" },
"target":         { "type": "text", "value": "color" },
"color":          { "type": "text", "value": "red" }
```

Пример: круг `color=red` подходит только к ящику `color=red`.

### `propertyIncludes`

Проходит, если значение drag-объекта входит в список значений цели.

```json
"constraintType":   { "type": "text",     "value": "propertyIncludes" },
"source":           { "type": "text",     "value": "shape" },
"target":           { "type": "text",     "value": "acceptedShapes" },
"acceptedShapes":   { "type": "textList", "values": ["circle", "oval"] }
```

### `tagMatch`

Проходит, если drag-объект и цель имеют одинаковое значение указанного тега.

```json
"constraintType": { "type": "text", "value": "tagMatch" },
"tagKey":         { "type": "text", "value": "color" }
```

### `allOf` / `anyOf`

Комбинируют несколько ограничений.

```json
"constraintType": { "type": "text", "value": "allOf" },
"types":          { "type": "text", "value": "propertyEquals,tagMatch" }
```

---

## Поведения (behaviors)

Стандартный набор для большинства сцен:

```json
"behaviors": [
  { "behaviorRef": { "type": "progressTracker",  "config": {} }, "entityId": null },
  { "behaviorRef": { "type": "snapToTarget",     "config": {} }, "entityId": null },
  { "behaviorRef": { "type": "adaptiveHint",     "config": {} }, "entityId": null },
  { "behaviorRef": { "type": "playSoundOnEvent", "config": { "on": "sceneCompleted", "sound": "complete" } }, "entityId": null }
]
```

### Справочник поведений

#### `progressTracker`
Считает матчи и ошибки. Без параметров.

#### `snapToTarget`
Основная механика drag-and-drop.

| Параметр | По умолчанию | Описание |
|----------|-------------|----------|
| `snapRadius` | `80` | радиус захвата (логические единицы) |

#### `adaptiveHint`
Пульсирует объект если ребёнок не взаимодействует с экраном.

| Параметр | По умолчанию | Описание |
|----------|-------------|----------|
| `cooldownMin` | `600` (10с) | минимальная пауза между подсказками (тики) |
| `cooldownMax` | `900` (15с) | максимальная пауза |

Уровни (автоматически): **SUBTLE** → **DIRECTIONAL** → **ASSIST**

#### `playSoundOnEvent`

| Параметр | Значения |
|----------|----------|
| `on`    | `matchSuccess`, `matchFail`, `sceneCompleted` |
| `sound` | `success`, `fail`, `complete`, `hint` |

---

## Условия победы (objectives)

### `allMatched`

Срабатывает, когда **все** объекты с тегом `group` совмещены с целями.

```json
{ "id": "win", "type": "allMatched", "config": { "group": "fruits" }, "requires": null }
```

Объекты должны иметь тег `"group": "fruits"`.

### `allSorted`

Срабатывает, когда все объекты с тегом `sortable` отсортированы.

```json
{ "id": "win", "type": "allSorted", "config": { "tag": "sortable" }, "requires": null }
```

---

## Жизненный цикл объекта

```
ACTIVE → TENTATIVE → LOCKED
  ↑           ↑          ↑
(старт)  (matched)  (сцена завершена)
```

| Состояние | Цвет | Описание |
|-----------|------|----------|
| `ACTIVE`    | синий  | исходное, можно перетаскивать |
| `TENTATIVE` | зелёный | правильно совмещён с целью |
| `LOCKED`    | серый  | зафиксирован после завершения |
| `INACTIVE`  | скрыт  | не отображается |

---

## Полный пример: «Совмести буквы»

`catalog/match_letters_01.json`:

```json
{
  "sceneId": "match_letters_01",
  "entities": [
    {
      "id": "letter_A", "components": ["drag"], "behaviors": [],
      "properties": {
        "position": { "type": "vec2", "x": 200.0, "y": 250.0 },
        "zOrder":   { "type": "num",  "value": 1.0 },
        "emoji":    { "type": "text", "value": "A" },
        "letter":   { "type": "text", "value": "A" }
      },
      "tags": { "group": "letters" }, "zOrder": 0
    },
    {
      "id": "letter_B", "components": ["drag"], "behaviors": [],
      "properties": {
        "position": { "type": "vec2", "x": 500.0, "y": 250.0 },
        "zOrder":   { "type": "num",  "value": 1.0 },
        "emoji":    { "type": "text", "value": "B" },
        "letter":   { "type": "text", "value": "B" }
      },
      "tags": { "group": "letters" }, "zOrder": 0
    },
    {
      "id": "slot_A", "components": ["dropTarget"], "behaviors": [],
      "properties": {
        "position":       { "type": "vec2", "x": 200.0, "y": 700.0 },
        "zOrder":         { "type": "num",  "value": 0.0 },
        "emoji":          { "type": "text", "value": "a" },
        "constraintType": { "type": "text", "value": "propertyEquals" },
        "source":         { "type": "text", "value": "letter" },
        "target":         { "type": "text", "value": "letter" },
        "letter":         { "type": "text", "value": "A" }
      },
      "tags": {}, "zOrder": 0
    },
    {
      "id": "slot_B", "components": ["dropTarget"], "behaviors": [],
      "properties": {
        "position":       { "type": "vec2", "x": 500.0, "y": 700.0 },
        "zOrder":         { "type": "num",  "value": 0.0 },
        "emoji":          { "type": "text", "value": "b" },
        "constraintType": { "type": "text", "value": "propertyEquals" },
        "source":         { "type": "text", "value": "letter" },
        "target":         { "type": "text", "value": "letter" },
        "letter":         { "type": "text", "value": "B" }
      },
      "tags": {}, "zOrder": 0
    }
  ],
  "objectives": [
    { "id": "win", "type": "allMatched", "config": { "group": "letters" }, "requires": null }
  ],
  "behaviors": [
    { "behaviorRef": { "type": "progressTracker",  "config": {} }, "entityId": null },
    { "behaviorRef": { "type": "snapToTarget",     "config": {} }, "entityId": null },
    { "behaviorRef": { "type": "adaptiveHint",     "config": {} }, "entityId": null },
    { "behaviorRef": { "type": "playSoundOnEvent", "config": { "on": "sceneCompleted", "sound": "complete" } }, "entityId": null }
  ],
  "fieldPolicies": {}, "idleTimeout": 30, "onIdle": "HINT", "metadata": {}
}
```

Затем в `catalog/catalog.json`:

```json
{ "id": "match_letters_01", "title": "Буквы А Б", "description": "Совмести заглавную букву с маленькой", "url": "https://olluorg.github.io/mimi/match_letters_01.json", "ageMin": 4, "ageMax": 6 }
```

---

## Структура проекта

```
catalog/                   ← ПИШИ ЗДЕСЬ (источник правды)
├── catalog.json           ← индекс сцен
├── sort_fruits_01.json
└── ...

sharedUI/.../composeResources/files/catalog/   ← gitignored, копируется при сборке
```

> Файлы в `composeResources/` перезаписываются при каждой сборке — не редактируй их вручную.

---

## Советы по дизайну

- **Размещение**: drag-объекты вверху (Y ≈ 200–350), цели внизу (Y ≈ 650–800).
- **Расстановка**: 3 объекта — x = 200, 500, 800; 4 объекта — 150, 380, 620, 850.
- **Emoji**: один символ отображается крупно; двойные (`🔴🔴`) работают, но выглядят хуже.
- **Группы**: все drag-объекты одной механики должны иметь одинаковый тег `group`.
- **zOrder**: drag-объекты = 1.0, цели = 0.0 (drag рисуется поверх).
- **Возраст**: 2–4 года — 3 объекта, 1 цель; 4–6 лет — 4 объекта, 2+ цели.
