```markdown
# GlowPath Now Playing API

Локальный HTTP API для получения текущего трека из GlowPath.

Сервер поднимается автоматически при запуске приложения.  
Порт по умолчанию: **8765**.

Никакой авторизации и API-ключей нет.

---

## Базовый URL

| Где клиент | URL |
|------------|-----|
| На том же телефоне | `http://127.0.0.1:8765` |
| В той же Wi‑Fi сети | `http://<IP-телефона>:8765` |

Узнать IP телефона: Настройки → Wi‑Fi → текущая сеть.

---

## Эндпоинты

### `GET /now` (алиас `/status`)

Текущий трек в JSON (расширенный).

**Пример ответа:**

```json
{
  "title": "90",
  "artist": "Монеточка",
  "album": "Раскраски для взрослых",
  "is_playing": true,
  "has_song": true,
  "duration_ms": 201979,
  "position_ms": 25600,
  "position_raw_ms": 22657,
  "remaining_ms": 176379,
  "progress": 0.1267,
  "position_str": "0:25",
  "duration_str": "3:21",
  "remaining_str": "2:56",
  "current_uri": "content://...",
  "library_size": 159,
  "bridge_queue_size": 0,
  "last_command": "Play / Pause",
  "last_query": "",
  "last_import_uri": "",
  "last_batch_summary": "",
  "updated_at": 1789254998168,
  "server_time": 1789255023000,
  "cover_url": "http://127.0.0.1:8765/cover"
}
```

| Поле | Тип | Описание |
|------|-----|----------|
| `title` | string | Название трека |
| `artist` | string | Исполнитель |
| `album` | string | Альбом |
| `is_playing` | boolean | Сейчас играет |
| `has_song` | boolean | Есть загруженный трек |
| `duration_ms` | number | Длительность, мс |
| `position_ms` | number | **Эффективная** позиция (с учётом времени с последнего обновления) |
| `position_raw_ms` | number | Сырая позиция из снапшота |
| `remaining_ms` | number | Сколько осталось |
| `progress` | number | Прогресс 0.0 – 1.0 |
| `position_str` | string | Позиция в виде `m:ss` / `h:mm:ss` |
| `duration_str` | string | Длительность в удобном виде |
| `remaining_str` | string | Остаток в удобном виде |
| `current_uri` | string | URI файла |
| `library_size` | number | Размер библиотеки |
| `bridge_queue_size` | number | Размер очереди bridge |
| `last_command` | string | Последняя команда |
| `last_query` | string | Последний поисковый запрос |
| `last_import_uri` | string | Последний импортированный URI |
| `last_batch_summary` | string | Краткое описание последнего batch |
| `updated_at` | number | Unix timestamp (мс) последнего обновления снапшота |
| `server_time` | number | Текущее время сервера |
| `cover_url` | string | URL обложки |

---

### `GET /cover`

Обложка текущего трека (JPEG).  
Если обложки нет — `404`.

---

### `GET /info`

Информация о сервере.

```json
{
  "api_version": "1.1",
  "server": "GlowPath NowPlayingHttpServer",
  "port": 8765,
  "running": true,
  "timestamp": 1789255023000
}
```

---

### Управление (POST)

| Метод | Путь | Действие |
|-------|------|----------|
| POST | `/control/play_pause` | Play / Pause |
| POST | `/control/next` | Следующий трек |
| POST | `/control/prev` | Предыдущий трек |
| POST | `/control/stop` | Стоп |

Все возвращают:

```json
{
  "ok": true,
  "command": "play_pause",
  "message": "Command dispatched"
}
```

---

### `GET /`

Краткая текстовая справка.

---

## Примеры

### curl

```bash
curl http://127.0.0.1:8765/now
curl -o cover.jpg http://127.0.0.1:8765/cover
curl -X POST http://127.0.0.1:8765/control/next
```

### JavaScript / fetch

```js
const res = await fetch("http://127.0.0.1:8765/now");
const track = await res.json();
console.log(`${track.artist} — ${track.title}`);
console.log(`${track.position_str} / ${track.duration_str} (${(track.progress * 100).toFixed(1)}%)`);
```

### Python

```python
import requests

r = requests.get("http://127.0.0.1:8765/now", timeout=5)
data = r.json()
print(f"{data['artist']} — {data['title']}")
print(f"{data['position_str']} / {data['duration_str']} ({data['progress']*100:.1f}%)")
```

### Hikka / Heroku модуль

В конфиге модуля:

```
api_base = http://127.0.0.1:8765
```

(если userbot на том же телефоне, например Termux)

---

## ExteraGram bridge (альтернатива)

Помимо HTTP, в приложении есть ContentProvider:

| URI | Назначение |
|-----|------------|
| `content://com.musicplayer.bridge/status` | Статус трека (Cursor) |
| `content://com.musicplayer.bridge/cover/current` | Обложка |

Authority: `com.musicplayer.bridge`

Подходит только для кода, который выполняется **на том же устройстве** внутри кастомного клиента Telegram.

---

## Замечания

- Сервер работает, только пока процесс приложения жив (приложение открыто или играет в фоне).
- CORS: `Access-Control-Allow-Origin: *` — можно дергать из WebView / браузера.
- Данные берутся из того же snapshot, что и ExteraGram bridge.
- Порт сейчас фиксированный: `8765`.
- `position_ms` — **эффективная** позиция. Модулю не нужно самому считать прошедшее время.

---

## Для разработчиков плагинов / модулей

Минимальный сценарий «now playing» карточки:

1. `GET /now` → title, artist, is_playing, position_str, duration_str, progress  
2. `GET /cover` → картинка  
3. Собрать сообщение / баннер  

Поллинг раз в 8–15 секунд обычно достаточно для авто-обновления.

Вопросы и PR — в Issues репозитория.
```
