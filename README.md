# Plugin-JJK

Плагин для **Paper 1.21.8** с техниками из аниме *Магическая битва*.

## Что теперь есть

- Техники:
  - Годжо: `gojo_blue`, `gojo_red`, `gojo_purple`
  - Сукуна: `sukuna_slash`
  - Фушигуро: `fushiguro_ten_shadows`
- Система **получения техник через тренировку** (со сложностью у каждой техники).
- Система **прокачки уровней техник** (XP и level up).
- Система **проклятой энергии**:
  - трата энергии на тренировку и применение техник
  - регенерация энергии со временем
  - рост максимального запаса от общего ранга колдуна
- Данные игроков сохраняются в `plugins/Plugin-JJK/playerdata.yml`.

## Активация техник на клавиши/сочетания

На чистом Paper без клиентского мода нельзя перехватывать *любую* клавишу напрямую, поэтому сделана привязка к стандартным вводам Minecraft:

- `left_click` (ЛКМ)
- `shift_left_click` (Shift + ЛКМ)
- `right_click` (ПКМ)
- `shift_right_click` (Shift + ПКМ)
- `swap_hands` (клавиша `F`)
- `drop_key` (клавиша `Q`)

## Команды

```bash
/jjk help
/jjk list
/jjk stats
/jjk train <technique>
/jjk use <technique>
/jjk bind <combo> <technique>
/jjk unbind <combo>
/jjk binds
```

Примеры:

```bash
/jjk train gojo_blue
/jjk bind swap_hands gojo_blue
/jjk bind shift_right_click gojo_purple
/jjk bind drop_key sukuna_slash
/jjk binds
```

Также оставлена совместимость со старым форматом:

```bash
/jjk gojo blue
/jjk sukuna slash
/jjk fushiguro tenshadows
```

## Сборка

```bash
mvn clean package
```

Готовый `.jar` будет в `target/`.
