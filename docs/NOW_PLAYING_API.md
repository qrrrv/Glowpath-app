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

### `GET /now`

Текущий трек в JSON.

**Пример ответа:**

```json
{
  "title": "90",
  "artist": "Монеточка",
  "album": "Раскраски для взрослых",
  "is_playing": true,
  "has_song": true,
  "duration_ms": 201979,
  "position_ms": 22657,
  "current_uri": "content://...",
  "library_size": 159,
  "updated_at": 1789254998168,
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
| `position_ms` | number | Текущая позиция, мс |
| `current_uri` | string | URI файла |
| `library_size` | number | Размер библиотеки |
| `updated_at` | number | Unix timestamp (мс) последнего обновления |
| `cover_url` | string | URL обложки |

Алиас: `GET /status` — то же самое.

---

### `GET /cover`

Обложка текущего трека (JPEG).

Если обложки нет — `404`.

---

### `GET /`

Краткая текстовая справка.

---

## Примеры

### curl

```bash
curl http://127.0.0.1:8765/now
curl -o cover.jpg http://127.0.0.1:8765/cover
```

### JavaScript / fetch

```js
const res = await fetch("http://127.0.0.1:8765/now");
const track = await res.json();
console.log(track.title, track.artist);
```

### Python

```python
import requests

r = requests.get("http://127.0.0.1:8765/now", timeout=5)
data = r.json()
print(data["title"], "-", data["artist"])
```

### Hikka / Telethon модуль

В конфиге модуля укажи:

```
api_base = http://127.0.0.1:8765
```

(если userbot на том же телефоне, например Termux)

---

## ExteraGram bridge (альтернатива)

Помимо HTTP, в приложении есть ContentProvider для плагинов ExteraGram:

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

---

## Для разработчиков плагинов

Минимальный сценарий «now playing» карточки:

1. `GET /now` → title, artist, is_playing, position/duration  
2. `GET /cover` → картинка  
3. Собрать сообщение / баннер  

Поллинг раз в 10–15 секунд обычно достаточно для авто-обновления.

Вопросы и PR — в Issues репозитория.
