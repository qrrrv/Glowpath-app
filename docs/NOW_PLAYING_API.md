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
