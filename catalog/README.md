# Mimi — Каталог игр

Здесь лежат JSON-файлы всех сцен и инструкция по созданию новых игр.

---

## Быстрый старт (локально, без интернета)

```bash
# 1. Запустить десктопное приложение
./gradlew :desktopApp:run

# 2. Все встроенные игры уже в кэше — интернет не нужен
```

Приложение при старте загружает сцены из `BuiltinScenes.kt`. Изменил файл → перезапустил → видишь результат.

---

## Как добавить новую игру

### Шаг 1. Добавить сцену в `BuiltinScenes.kt`

Файл: `sharedUI/src/commonMain/kotlin/org/ollu/mini/scenes/BuiltinScenes.kt`

```kotlin
object BuiltinScenes {
    val all = listOf(
        // ... существующие ...
        entry("my_game_01", "Моя игра", "Описание для каталога", 3, 6) to myGameScene(),
    )
}

private fun myGameScene(): SceneDefinition {
    // ... описание сцены ...
}
```

### Шаг 2. Запустить локально

```bash
./gradlew :desktopApp:run
```

Игра сразу появится в каталоге.

### Шаг 3. Опубликовать (GitHub Pages)

```bash
# Регенерировать catalog/*.json
./gradlew :desktopApp:generateCatalog

# Закоммитить и запушить — GitHub Actions задеплоит автоматически
git add catalog/ sharedUI/src/commonMain/kotlin/org/ollu/mini/scenes/BuiltinScenes.kt
git commit -m "feat: add my_game_01"
git push
```

---

## Анатомия сцены

```kotlin
SceneDefinition(
    sceneId    = "sort_fruits_01",          // уникальный ID, совпадает с именем файла
    entities   = listOf(/* ... */),         // все объекты на экране
    objectives = listOf(/* ... */),         // условия победы
    behaviors  = sharedBehaviors()          // логика сцены (обычно sharedBehaviors())
)
```

### Координатная система

Логическое пространство — **1000 × 1000** единиц.  
Начало (0, 0) — верхний левый угол. Ось Y направлена вниз.

```
(0,0) ──────────── (1000,0)
  │                    │
  │   типичные Y:      │
  │   250 — объекты    │
  │   700 — цели       │
  │                    │
(0,1000) ─────── (1000,1000)
```

Физические пиксели вычисляются автоматически через letterbox-маппинг.

---

## Сущности (EntityDefinition)

```kotlin
EntityDefinition(
    id         = "apple",                          // уникальный ID в сцене
    components = listOf("drag"),                   // поведение объекта (см. ниже)
    properties = mapOf(
        "position" to PropertyValue.Vec2(200f, 250f),
        "zOrder"   to PropertyValue.Num(1.0),
        "emoji"    to PropertyValue.Text("🍎"),
        // любые пользовательские свойства:
        "category" to PropertyValue.Text("fruit")
    ),
    tags = mapOf("group" to "fruits"),              // для групповых условий победы
    behaviors = listOf(/* entity-level behaviors */)
)
```

### Типы PropertyValue

| Тип | Пример | Применение |
|-----|--------|------------|
| `Vec2(x, y)` | `Vec2(200f, 300f)` | позиция |
| `Text(s)` | `Text("fruit")` | строковые свойства, ключи ограничений |
| `Num(d)` | `Num(1.0)` | числовые свойства, zOrder |
| `Ref(id)` | `Ref("basket")` | ссылка на другую сущность |
| `TextList(list)` | `TextList(listOf("a","b"))` | список значений для `propertyIncludes` |
| `Bool(b)` | `Bool(true)` | флаги |

### Зарезервированные свойства

| Свойство | Тип | Описание |
|----------|-----|----------|
| `position` | Vec2 | позиция на экране (обязательно) |
| `zOrder` | Num | порядок отрисовки (выше = поверх) |
| `emoji` | Text | эмодзи, отображаемый на тайле |

---

## Компоненты

Компонент определяет, как объект взаимодействует с движком.

### `"drag"` — перетаскиваемый объект

```kotlin
components = listOf("drag")
```

Пользователь может взять объект и перетащить. При отпускании над `dropTarget` генерируется событие `Drop`.

### `"dropTarget"` — зона сброса

```kotlin
components = listOf("dropTarget"),
properties = mapOf(
    "constraintType" to PropertyValue.Text("propertyEquals"),
    "source"         to PropertyValue.Text("category"),   // свойство у drag-объекта
    "target"         to PropertyValue.Text("category"),   // свойство у dropTarget
    "category"       to PropertyValue.Text("fruit"),      // ожидаемое значение
    // ...
)
```

Движок вызывает ограничение при каждом Drop. Если ограничение проходит — объект «защёлкивается» (MarkMatched).

---

## Ограничения (Constraints)

Ограничение определяет, допустим ли конкретный сброс.

### `propertyEquals` ★ (чаще всего)

Проходит, если свойство drag-объекта равно свойству цели.

```kotlin
// На dropTarget:
"constraintType" to Text("propertyEquals"),
"source"         to Text("color"),    // ключ свойства у drag-объекта
"target"         to Text("color"),    // ключ свойства у dropTarget
"color"          to Text("red"),      // значение, которое должно совпасть
```

Пример: круг `color=red` подходит только к ящику `color=red`.

### `propertyIncludes`

Проходит, если значение drag-объекта содержится в списке значений цели.

```kotlin
"constraintType" to Text("propertyIncludes"),
"source"         to Text("shape"),
"target"         to Text("acceptedShapes"),
"acceptedShapes" to PropertyValue.TextList(listOf("circle", "oval")),
```

### `tagMatch`

Проходит, если drag-объект и цель имеют одинаковое значение указанного тега.

```kotlin
"constraintType" to Text("tagMatch"),
"tagKey"         to Text("color"),
```

### `allOf` / `anyOf`

Комбинируют несколько ограничений.

```kotlin
"constraintType" to Text("allOf"),
"types"          to Text("propertyEquals,tagMatch"),
// + параметры для каждого из типов
```

---

## Поведения (Behaviors)

Поведения добавляются на уровне сцены. Все стандартные сцены используют `sharedBehaviors()`.

### Стандартный набор `sharedBehaviors()`

```kotlin
private fun sharedBehaviors() = listOf(
    SceneBehaviorBinding(BehaviorRef("progressTracker")),
    SceneBehaviorBinding(BehaviorRef("snapToTarget")),
    SceneBehaviorBinding(BehaviorRef("adaptiveHint")),
    SceneBehaviorBinding(BehaviorRef("playSoundOnEvent",
        mapOf("on" to "sceneCompleted", "sound" to "complete")))
)
```

### Справочник поведений

#### `progressTracker`
Считает матчи и ошибки в мета-свойствах сцены (`matches`, `mistakes`). Без параметров.

#### `snapToTarget`
Основная механика drag-and-drop. Оценивает ограничение, при успехе перемещает объект к цели.

| Параметр | По умолчанию | Описание |
|----------|-------------|----------|
| `snapRadius` | `80` | радиус захвата цели (логические единицы) |
| `priority` | `100` | приоритет команды в CRL |

#### `adaptiveHint`
Показывает подсказки (пульсация объекта), если ребёнок не взаимодействует с экраном.

| Параметр | По умолчанию | Описание |
|----------|-------------|----------|
| `cooldownMin` | `600` (10с) | минимальная пауза между подсказками (тики) |
| `cooldownMax` | `900` (15с) | максимальная пауза |

Уровни подсказок (автоматически):
- **SUBTLE** — после 4 сек / 1 ошибки
- **DIRECTIONAL** — после 8 сек / 2 ошибок
- **ASSIST** — после 12 сек / 3 ошибок

#### `playSoundOnEvent`
Воспроизводит звук при наступлении события.

| Параметр | Значения | Описание |
|----------|----------|----------|
| `on` | `matchSuccess`, `matchFail`, `sceneCompleted` | событие-триггер |
| `sound` | `success`, `fail`, `complete`, `hint` | имя звука |

```kotlin
SceneBehaviorBinding(BehaviorRef("playSoundOnEvent",
    mapOf("on" to "sceneCompleted", "sound" to "complete")))
```

#### `difficultyScaler`
Автоматически адаптирует сложность по метрикам успешности. Без параметров для стандартного использования.

#### `visibilityToggle`
Переключает видимость сущностей при событии.

| Параметр | Значения | Описание |
|----------|----------|----------|
| `targets` | `"id1,id2"` | ID сущностей (через запятую) |
| `on` | `tap`, `matchSuccess` | событие-триггер |

```kotlin
EntityDefinition(
    id = "hint_arrow",
    behaviors = listOf(BehaviorRef("visibilityToggle",
        mapOf("targets" to "hint_arrow", "on" to "tap")))
)
```

#### `lockOnComplete`
При завершении сцены переводит все TENTATIVE-объекты в LOCKED (серые). Без параметров.

---

## Условия победы (Objectives)

```kotlin
objectives = listOf(
    ObjectiveDefinition(
        id     = "obj1",
        type   = "allMatched",
        config = mapOf("group" to "fruits")
    )
)
```

### `allMatched`

Срабатывает, когда **все** объекты с тегом `group = <value>` находятся в состоянии TENTATIVE или LOCKED.

```kotlin
// Объекты должны иметь:
tags = mapOf("group" to "fruits")

// Условие:
ObjectiveDefinition("win", "allMatched", mapOf("group" to "fruits"))
```

### `allSorted`

Срабатывает, когда все объекты, помеченные определённым тегом, отсортированы.

```kotlin
ObjectiveDefinition("win", "allSorted", mapOf("tag" to "sortable"))
```

---

## Жизненный цикл объекта

```
ACTIVE → TENTATIVE → LOCKED
          ↑             ↑
     (matched)    (scene complete)
```

| Состояние | Цвет | Описание |
|-----------|------|----------|
| `ACTIVE` | синий | исходное, можно перетаскивать |
| `TENTATIVE` | зелёный | правильно совмещён с целью |
| `LOCKED` | серый | зафиксирован (после завершения сцены) |
| `INACTIVE` | скрыт | не отображается |

---

## Полный пример: «Совмести буквы»

```kotlin
private fun matchLettersScene(): SceneDefinition {
    fun letter(id: String, char: String, x: Float) = EntityDefinition(
        id         = id,
        components = listOf("drag"),
        properties = mapOf(
            "position" to PropertyValue.Vec2(x, 250f),
            "zOrder"   to PropertyValue.Num(1.0),
            "letter"   to PropertyValue.Text(char),
            "emoji"    to PropertyValue.Text(char)
        ),
        tags = mapOf("group" to "letters")
    )

    fun slot(id: String, char: String, x: Float) = EntityDefinition(
        id         = id,
        components = listOf("dropTarget"),
        properties = mapOf(
            "position"       to PropertyValue.Vec2(x, 700f),
            "zOrder"         to PropertyValue.Num(0.0),
            "constraintType" to PropertyValue.Text("propertyEquals"),
            "source"         to PropertyValue.Text("letter"),
            "target"         to PropertyValue.Text("letter"),
            "letter"         to PropertyValue.Text(char),
            "emoji"          to PropertyValue.Text(char.lowercase())
        )
    )

    return SceneDefinition(
        sceneId    = "match_letters_01",
        entities   = listOf(
            letter("A", "A", 200f),
            letter("B", "B", 500f),
            letter("C", "C", 800f),
            slot("a", "A", 200f),
            slot("b", "B", 500f),
            slot("c", "C", 800f),
        ),
        objectives = listOf(
            ObjectiveDefinition("obj1", "allMatched", mapOf("group" to "letters"))
        ),
        behaviors  = sharedBehaviors()
    )
}
```

Затем в `BuiltinScenes.all`:

```kotlin
entry("match_letters_01", "Буквы А Б В", "Совмести заглавную букву с маленькой", 4, 6)
    to matchLettersScene(),
```

---

## Структура проекта

```
sharedUI/src/commonMain/kotlin/org/ollu/mini/
├── scenes/
│   ├── BuiltinScenes.kt   ← ВСЕ встроенные сцены — правь здесь
│   └── DemoScene.kt
├── catalog/
│   ├── CatalogCache.kt    ← кэш через multiplatform-settings
│   ├── SceneRepository.kt ← Flow-based загрузка: кэш → сеть
│   ├── CatalogScreen.kt
│   └── CatalogEntry.kt
└── ...

catalog/                   ← сгенерированные JSON (не редактировать вручную)
├── catalog.json
├── sort_fruits_01.json
└── ...
```

> **catalog/*.json генерируется автоматически** из `BuiltinScenes.kt` командой  
> `./gradlew :desktopApp:generateCatalog`  
> Не редактируй JSON вручную.

---

## Советы по дизайну

- **Размещение**: drag-объекты вверху (Y ≈ 200–350), цели внизу (Y ≈ 650–800).
- **Расстановка**: 3 объекта — x = 200, 500, 800; 4 объекта — 150, 380, 620, 850.
- **Emoji**: один символ отображается крупно; двойные (`🔴🔴`) работают, но выглядят хуже.
- **Группы**: все drag-объекты одной механики должны иметь одинаковый тег `group`.
- **zOrder**: drag-объекты = 1.0, цели = 0.0 (чтобы drag рисовался поверх).
- **Возраст**: 2–4 года — 3 объекта, 1 цель; 4–6 лет — 4 объекта, 2+ цели.
