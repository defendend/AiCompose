# CI/CD Workflow Examples

Примеры GitHub Actions workflows для публикации Android приложений.

## Файлы

| Файл | Описание |
|------|----------|
| `build-debug-apk.yml` | Сборка Debug APK (без подписи) |
| `publish-rustore.yml` | Публикация в RuStore |
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
| `RUSTORE_COMPANY_ID` | ID компании | RuStore Console → Настройки |
| `RUSTORE_CLIENT_SECRET` | API ключ | RuStore Console → API |

### Для Google Play

| Secret | Описание | Как получить |
|--------|----------|--------------|
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | Service Account JSON | Google Play Console → API access |

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
- **publish-*.yml** — при создании тега `v*` (например: `git tag v1.0.0 && git push origin v1.0.0`)

## Ручной запуск

Actions → выбери workflow → Run workflow
