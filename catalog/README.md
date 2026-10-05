# Mimi — Каталог игр

Здесь лежат JSON-файлы всех сцен. Kotlin-код для добавления игр **не нужен**.

---

## Архитектура

Сцены — это JSON. Приложение читает их из встроенных ресурсов (бандл), затем обновляет по сети.

```
catalog/                        ← источник правды, коммитить в git
├── catalog.json                ← индекс, генерируется из папок
├── sort_fruits_01/
│   └── sort_fruits_01.json     ← данные сцены
└── ...
```

При сборке Gradle копирует каталог в бандл → офлайн-доступ без интернета.  
После пуша GitHub Actions деплоит обновления на GitHub Pages → приложения обновляют каталог
без перевыпуска.

---

## Как добавить новую игру

### Шаг 1. Написать JSON сцены

Каждая игра живёт в своей папке: `catalog/my_game_01/my_game_01.json`.
Имя папки, имя файла и `sceneId` должны совпадать — из них строится URL публикации.
Медиа кладётся рядом: `catalog/my_game_01/media/`.

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

### Шаг 2. Пересобрать индекс

```bash
./gradlew generateCatalogIndex
```

`catalog.json` собирается из папок автоматически — руками его не правят.

### Шаг 3. Проверить

```bash
./gradlew :core:jvmTest     # валидация: сцену проверят так же, как в CI
./gradlew :desktopApp:run   # посмотреть глазами
```

Gradle скопирует каталог в бандл автоматически — игра появится в списке.

### Шаг 4. Опубликовать

```bash
git add catalog/my_game_01 catalog/catalog.json
git commit -m "feat: add my_game_01"
git push
```

GitHub Actions прогонит валидацию и задеплоит на Pages. Приложения получат
новую сцену без обновления. Если сцена не проходит проверку — деплоя не будет.

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
    { "id": "sort_fruits_01", "url": "https://olluorg.github.io/mimi/sort_fruits_01/sort_fruits_01.json" }
  ]
}
```

Приложение читает этот индекс, сравнивает с тем что уже есть в кэше, и подтягивает недостающие сцены.
Метаданные (название, описание, возраст) берёт из самих scene JSON — не из `catalog.json`.

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

Затем `./gradlew generateCatalogIndex` — индекс соберётся сам.

---

## Структура проекта

```
catalog/                        ← ПИШИ ЗДЕСЬ (источник правды)
├── catalog.json                ← индекс, генерируется
├── sort_fruits_01/
│   └── sort_fruits_01.json
├── find_animal_01/
│   ├── find_animal_01.json
│   └── media/meow.mp3
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

---

## Игры без неправильного ответа

Большинство механик спрашивают «угадал?». Для неговорящего ребёнка первые две
ступени устроены иначе — в них **нельзя ошибиться**, и это принципиально:

* **причина-следствие** — трогаю и мир меняется. Это то, с чего начинается
  ощущение «я влияю на происходящее»;
* **выбор** — «что ты хочешь?». Оба ответа верные, навык здесь — сам факт
  выбора. Пометить выбор ошибкой — значит учить ровно обратному.

### `freeSelection`

Любое нажатие по сущности из группы засчитывается. Никогда не даёт `matchFail`,
поэтому такие игры не портят статистику ошибок.

| Параметр | По умолчанию | Описание |
|----------|--------------|----------|
| `group` | — | тег `group`, по которому отбираются варианты |
| `repeatable` | `false` | объект остаётся активным — можно нажимать снова и снова |
| `hideOnSelect` | `false` | объект исчезает при нажатии (пузырь лопается) |

Выбранное имя (`name`, иначе id) записывается в `_scene.lastChoice` и попадает
в историю сессий — на экране «Для взрослых» видно, что ребёнок предпочитает.

### `revealNext`

Показывает следующий скрытый объект группы. Нужен, чтобы просьба давала
результат: нажал «ещё» — появился ещё один шарик.

| Параметр | По умолчанию | Описание |
|----------|--------------|----------|
| `group` | `reward` | группа, из которой доставать объекты |
| `on` | `matchSuccess` | `matchSuccess` или `tap` |

Объекты должны быть объявлены скрытыми: `"initialState": "INACTIVE"`.

### `initialState`

Поле сущности рядом с `components` и `tags`. По умолчанию `ACTIVE`.
`INACTIVE` — объект не показан и ждёт своего момента.

```json
{
  "id": "balloon_0",
  "components": [],
  "properties": { "position": {"type":"vec2","x":150.0,"y":260.0},
                  "emoji": {"type":"text","value":"🎈"} },
  "tags": { "group": "reward" },
  "initialState": "INACTIVE"
}
```

### Цели для таких игр

#### `anySelected`

Завершается, когда выбран **любой** вариант из группы.

```json
{ "id": "chose", "type": "anySelected", "config": { "group": "choices" } }
```

#### `interactionCount`

Завершается после N взаимодействий — для игр на повторение.

```json
{ "id": "played", "type": "interactionCount", "config": { "count": "8" } }
```

> `anySelected` не сочетается с `hideOnSelect`: спрятанный объект не считается
> выбранным. Для «лопни пузыри» берите `interactionCount`.

---

## Проверка перед публикацией

```bash
./gradlew :core:jvmTest          # валидация всех сцен + прогон механик
./gradlew generateCatalogIndex   # пересобрать catalog.json
```

Сцена не попадёт на Pages, пока не пройдёт валидацию: проверяются разбор JSON,
совпадение `sceneId` с именем папки, известность поведений и целей,
достижимость цели, метаданные, `requires` и наличие медиа.

---

## Картинки вместо эмодзи

Эмодзи хватает для фруктов и фигур, но визуальные опоры работают лучше всего
на **фотографиях вещей самого ребёнка** — его кружки, его куртки, его группы
в саду. Положи файл рядом со сценой и сошлись на него свойством `image`:

```
catalog/my_game_01/
├── my_game_01.json
└── media/
    ├── cup.png
    └── coat.png
```

```json
"properties": {
  "position": { "type": "vec2", "x": 320.0, "y": 250.0 },
  "image":    { "type": "text", "value": "my_game_01/media/cup.png" },
  "label":    { "type": "text", "value": "кружка" }
}
```

Путь пишется от корня каталога. Поддерживаются `png`, `jpg`, `webp`.
Если `image` задан, он рисуется вместо `emoji`; `label` подписывается снизу.
Валидация проверяет, что файл существует — битая ссылка не уедет в прод.

---

## Доска общения

`daily_needs_board_01` — не игра. Это шесть потребностей, которые ребёнок
иначе никак не выразит: нажал на кружку — планшет сказал «пить».

Собирается из тех же деталей:

```json
{ "behaviorRef": { "type": "freeSelection",
    "config": { "group": "needs", "repeatable": "true", "speak": "true" } } }
```

| Параметр | Что делает |
|----------|------------|
| `speak` | произносит вслух `speech`, иначе `name`, иначе `label` |
| `repeatable` | плитка не «расходуется» — доской можно пользоваться постоянно |

Такую доску легко сделать под конкретного ребёнка: свои потребности, свои
фотографии, свои слова. Ошибок в ней нет и быть не может — это ребёнок
говорит, а не отвечает на вопрос.

---

## Библиотека карточек

`catalog/_cards/` — 313 карточек PECS с русскими подписями и **записанной**
озвучкой. Папки, начинающиеся с `_`, не сцены: индекс и валидация их пропускают.

```
catalog/_cards/
├── cards.json          ← id, подпись, категория
├── apple.jpg           ← 300×300
└── audio/apple.mp3     ← «яблоко», живой голос
```

В сцене:

```json
"properties": {
  "position": { "type": "vec2", "x": 220.0, "y": 300.0 },
  "image":    { "type": "text", "value": "_cards/apple.jpg" },
  "sound":    { "type": "text", "value": "_cards/audio/apple.mp3" },
  "label":    { "type": "text", "value": "Яблоко" },
  "name":     { "type": "text", "value": "Яблоко" }
}
```

`freeSelection` со `speak: true` проигрывает `sound`, если он есть, и только
иначе синтезирует речь: запись звучит чище, всегда одинаково и не зависит от
установленного в системе голоса.

---

## Вариативность: одна сцена — много раскладов

Сцена, одинаковая при каждом запуске, учит не тому: ребёнок запоминает, что
ответ — плитка слева. Блок `variation` заставляет движок раздавать сцену
заново на каждой игре.

```json
"variation": {
  "pick": [
    { "group": "food", "count": 2, "tagKey": "kind" },
    { "group": "toys", "count": 2, "tagKey": "kind" }
  ],
  "shufflePositions": ["items"]
}
```

| Поле | Что делает |
|------|------------|
| `shufflePositions` | список групп, чьи участники меняются местами между собой |
| `pick` | взять из пула только `count` штук |
| `pick[].keep` | сущности, которые нельзя выбросить (та, которую называет задание) |
| `pick[].tagKey` | по какому тегу набирать; по умолчанию `group` |

`tagKey` нужен, когда цель следит за всей группой `items`, а набирать надо
поровну из каждого вида — иначе сортировка может выдать четыре карточки в одну
корзину, и вторая останется пустой.

**Детерминизм сохраняется.** Раскладом управляет seed: один и тот же seed
всегда даёт одну и ту же сцену, поэтому прогон воспроизводим. Новизна берётся
из того, что сессия выбирает новый seed, а не из непредсказуемости движка.
Кнопка «ещё раз» тоже сдвигает seed — повтор не должен быть тем же экраном.

> `:core:jvmTest` проигрывает каждую сцену с `variation` на 25 сидах и проверяет,
> что она проходима, и что раскладка действительно меняется. Случайность, которая
> может раздать тупик, — худший вид случайности: ребёнок один не пожалуется.

---

## Чем рисовать плитку

Приложение несёт собственные шрифты — Noto Sans и Noto Emoji, обе под OFL, — так
что буквы, цифры и эмодзи отрисуются на всех платформах одинаково. Долгое время
это было не так: веб-сборка Compose несёт один шрифт без пиктографики, и любой
такой символ там просто исчезал.

Но шрифт — это запасной путь, а не первый выбор. Картинку лучше рисовать:

| Чем | Свойство | Когда |
|-----|----------|-------|
| Фотокарточка | `image` | предмет, зверь, еда, одежда — всё из `_cards/` |
| Фигура | `shape` + `color` | кружок, квадрат, треугольник, звезда |
| Количество | `count` | группы точек для счёта |
| Пиктограмма | `pictogram` | чего нет карточкой: `nose`, `eye`, `mouth`, `ear`, `hand`, `foot` |
| Текст | `emoji` / `label` / `face` | цифра, буква, слово, эмодзи |

```json
"properties": {
  "position":  { "type": "vec2", "x": 220.0, "y": 300.0 },
  "pictogram": { "type": "text", "value": "nose" },
  "label":     { "type": "text", "value": "нос" }
}
```

Новая пиктограмма добавляется в `sharedUI/.../renderer/Pictograms.kt` — десяток
строк рисования, без зависимостей и сразу на всех платформах. Готовые наборы
иконок не подошли: в Font Awesome есть глаз и ухо доступности, но нет ни носа,
ни рта, то есть сцена про лицо покрывается наполовину.

Валидация проверяет каждый символ в `emoji`, `label`, `name`, `face` и `speech`
против того, что покрывают встроенные шрифты. Вне покрытия остаются
декоративные блоки Unicode — например `●` из «Геометрических фигур», который
раньше использовали как точку для счёта; для таких случаев есть `count`.

---

## Речевые игры («Запуск речи»)

Игры для детей, которые не говорят или говорят мало (алалия, диспраксия, дизартрия).
В каталоге они собраны в отдельный фильтр «Запуск речи» (меню взрослого → «Запуск речи»).

### Разметка `meta.speech`

Сцена попадает в фильтр **только** с блоком `meta.speech`, и валидатор (CI) проверяет,
что заявленные режимы действительно реализованы поведениями — иначе сцена не публикуется.

```json
"meta": {
  "speech": {
    "stage":    "imitation",                 // aac | vocal | imitation | words
    "targets":  ["alalia", "apraxia"],       // alalia | apraxia | dysarthria
    "modes":    ["imitation", "voice"],      // aac | voice | cloze | imitation | drill
    "words":    ["му", "ав-ав"],
    "adultTip": "Произнесите звук вместе с игрой и подождите."
  }
}
```

| mode | что требует валидатор |
|------|-----------------------|
| `aac` | `freeSelection` с `speak=true` или `awaitResponse` с `accept` ⊇ `tap` |
| `voice` | `voiceDrive` или `awaitResponse` с `accept` ⊇ `voice` |
| `cloze`, `imitation` | `awaitResponse` |
| `drill` | `awaitResponse` с `order=random` и `trials` ≥ 10 |

Также обязательны: непустой `adultTip` и цель `interactionCount`.

### `awaitResponse` — модель → пауза → ответ

Показывает по одной карточке из группы, произносит её (`prompt` — файл, иначе `speech` — текст),
ждёт ответа. Ответ: голос (микрофон), кнопка взрослого «Сказал(а)!» (удержание), нажатие (для AAC).
Тишина — не ошибка: слово повторяется `maxPrompts` раз, затем игра идёт дальше.

| Параметр | По умолчанию | Описание |
|----------|-------------|----------|
| `group` | `targets` | группа карточек |
| `accept` | `voice,adult` | что считается ответом: `voice`, `adult`, `tap` |
| `firstLeadMs` / `leadMs` | 3000 / 800 | пауза перед моделью (первая — чтобы не перебить инструкцию) |
| `waitMs` | 6000 | сколько ждать ответа |
| `maxPrompts` | 2 | сколько раз повторить модель |
| `rewardMs` | 1800 | пауза на награду перед следующей карточкой |
| `trials` | размер группы | число ходов (поставьте такое же в `interactionCount`) |
| `order` | `sequential` | `random` — вразброс, без повторов подряд |
| `feedback` | `every` | `fading` — похвала всё реже (принцип моторного обучения) |
| `guardMs` | 1500 | сколько игнорировать микрофон после своей реплики |

Свойства карточки: `speech`/`prompt` — модель, `reward` — звук-ответ (корова мычит в ответ),
`answer` — полная фраза, которая звучит в конце хода («Раз, два, три!»).

### `voiceDrive` — предмет, движимый голосом

Пока звучит голос, `entity` поднимается от `fromY` к `toY` (скорость `rise` × громкость),
в тишине опускается (`fall`). Достиг верха — ход засчитан. Кнопка взрослого запускает сразу.
Сущности поставьте `"glide": {"type":"flag","value":true}` для плавного движения.

### Микрофон

Читается **только громкость** — без записи, распознавания и отправки куда-либо.
Android — `RECORD_AUDIO` (запрашивается при первой речевой игре), веб — `getUserMedia`,
десктоп — системный вход. На iOS микрофон пока не подключён: работает кнопка взрослого.

### Поощрения

Когда ребёнок отвечает правильно (карточка переходит в состояние «верно»), над ней вспыхивают
звёзды (`_anim/burst.json`), а в конце игры падает конфетти (`_anim/confetti.json`). Так работает
во **всех** играх, настраивать ничего не нужно. Точки прогресса внизу заполняются звёздочками.

Карточка может «ожить» — свойство `rewardMotion`:

| значение | что делает | для чего |
|----------|-----------|----------|
| `drive` | уезжает вправо и возвращается слева | машина, поезд |
| `fly` | улетает вверх и возвращается снизу | самолёт, птица |
| `jump` | подпрыгивает дважды | животные, люди, «да» |
| `spin` | поворачивается вокруг себя | «ещё», сюрприз |
| `wiggle` | качается | колокольчик, «нет» |

Звук поощрения — свойство `reward` (в речевых играх он обрезается до `rewardMs`).
Синтезированные звуки: `_sounds/car.wav` (гудок и мотор), `train.wav`, `airplane.wav`, `bell.wav`, `pop.wav`.

Анимации и звуки генерируются кодом: `python3 tools/generate_animations.py`, `python3 tools/generate_sounds.py`.

### Точная гласная: `expectSound`

Микрофон различает гласные **а, о, у, и, э** — на устройстве, по форме звука, без распознавания слов.
Если у карточки в `awaitResponse` задано `"expectSound": {"type":"text","value":"у"}`:

- любой голос по-прежнему засчитывает ход — бонус никогда не условие;
- если услышана именно эта гласная — большая золотая вспышка, фанфара, отметка «точно» в прогрессе.

Когда звук неясен, приложение отвечает «не уверен» (без бонуса, без штрафа) — это нормально.
Хлопки, стук и шум голосом не считаются: засчитывается только звонкий (периодичный) звук.

### Рот, показывающий звук: `mouth`

Свойство карточки `"mouth": {"type":"text","value":"_anim/mouth_a.json"}` показывает рядом с микрофоном
анимированные губы — ровно в тот момент, когда игра произносит образец, и повторно, если образец звучит снова.

Для ребёнка с диспраксией это главная подсказка: он не может спланировать движение на слух, но может
повторить увиденное (так работает методика DTTC — ребёнок смотрит на губы взрослого).

Готовые:

| файл | движение | для чего |
|------|----------|----------|
| `mouth_a`, `mouth_o`, `mouth_u`, `mouth_i`, `mouth_e` | протяжная гласная | а, о, у, и, э |
| `mouth_pb` | губы смыкаются, держат паузу и размыкаются толчком | **п и б** |
| `mouth_m` | губы сомкнуты долго (звук идёт через нос), потом открываются | **м** |
| `mouth_vf` | нижняя губа поднимается к верхним зубам | **в и ф** |
| `mouth_ma` | смычка и открытие дважды | повторяющийся слог: «ма-ма», «па-па», «би-би» |

«П» и «Б» — одно и то же движение губ, разница только в голосе, который не виден; то же у «В» и «Ф».
Поэтому на пару звуков приходится один файл, а не два одинаковых.
