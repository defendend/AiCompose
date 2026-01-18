# CI/CD Workflow Examples

Примеры GitHub Actions workflows для публикации Android приложений.

## Файлы

| Файл | Описание |
|------|----------|
| `build-debug-apk.yml` | Сборка Debug APK (без подписи) |
| `publish-rustore.yml` | Сборка + загрузка черновика в RuStore |
| `rustore-submit-review.yml` | Отправка черновика на модерацию (ручной запуск) |
| `publish-google-play.yml` | Публикация в Google Play Internal Testing |

## Использование

1. Скопируй нужный workflow в `.github/workflows/`
2. Настрой секреты в GitHub (Settings → Secrets → Actions)
3. Измени `PACKAGE_NAME` на свой пакет

## Требуемые секреты

### Для подписи APK/AAB

| Secret | Описание | Как получить |
|--------|----------|--------------|
| `KEYSTORE_BASE64` | Keystore в base64 | `base64 -i release.keystore` |
| `KEYSTORE_PASSWORD` | Пароль keystore | При создании keystore |
| `KEY_ALIAS` | Alias ключа | При создании keystore |
| `KEY_PASSWORD` | Пароль ключа | При создании keystore |

### Для RuStore

| Secret | Описание | Как получить |
|--------|----------|--------------|
| `RUSTORE_COMPANY_ID` | **key_id** из JSON | RuStore Console → Компания → API → Сгенерировать ключ → скачать JSON |
| `RUSTORE_CLIENT_SECRET` | **client_secret** (приватный ключ) | Из того же JSON файла |

**Важно:** `RUSTORE_COMPANY_ID` должен содержать именно `key_id` из скачанного JSON файла, а НЕ ID компании!

Формат JSON файла из RuStore Console:
```json
{
  "key_id": "12345678",              // ← это в RUSTORE_COMPANY_ID
  "client_secret": "MIIEvQ..."       // ← это в RUSTORE_CLIENT_SECRET
}
```

### Для Google Play

| Secret | Описание | Как получить |
|--------|----------|--------------|
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | Service Account JSON | Google Play Console → API access |

## RuStore Workflow

Публикация в RuStore состоит из двух шагов:

### 1. Автоматический: Build & Upload Draft (`publish-rustore.yml`)
- Триггер: создание тега `v*` (например `v1.0.0`)
- Собирает и подписывает APK
- Загружает черновик в RuStore
- Создаёт GitHub Release

### 2. Ручной: Submit for Review (`rustore-submit-review.yml`)
- Триггер: ручной запуск (workflow_dispatch)
- Требует `version_id` из лога предыдущего workflow
- Отправляет черновик на модерацию

## Создание Keystore

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias my-key \
  -storepass YOUR_PASSWORD \
  -keypass YOUR_PASSWORD \
  -dname "CN=App Name, OU=Mobile, O=Company, L=City, ST=State, C=RU"
```

## Конвертация в base64

```bash
base64 -i release.keystore > keystore_base64.txt
# Скопируй содержимое в GitHub Secret KEYSTORE_BASE64
```

## Особенности KorGE

KorGE игнорирует стандартные параметры подписи Android. Поэтому:

1. Сначала собираем unsigned APK/AAB
2. Затем подписываем вручную через `apksigner` (APK) или `jarsigner` (AAB)

## Триггеры

- **build-debug-apk.yml** — при push в main (изменения в korge-game/ или shared/)
- **publish-rustore.yml** — при создании тега `v*`
- **rustore-submit-review.yml** — только ручной запуск
- **publish-google-play.yml** — при создании тега `v*`

## Ручной запуск

Actions → выбери workflow → Run workflow

## RuStore API Authentication

Аутентификация использует SHA512withRSA подпись:
- Формат timestamp: ISO 8601 (`2024-06-18T11:49:08.290+03:00`)
- Подписывается: `keyId + timestamp`
- Документация: https://www.rustore.ru/help/work-with-rustore-api/api-authorization-token
