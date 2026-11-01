# letniye (Android, Jetpack Compose)

Лента-демо, перенесённая из `letniye_41.html`. Профиль пока не переписан.

## Сборка
Только release. GitHub Actions (`.github/workflows/release.yml`) запускается на push в `main`, на теги `v*` и вручную.
APK лежит в Artifacts запуска (`letniye-release-apk`), для тегов — ещё и в GitHub Releases.

## Подпись
Без настройки APK подписывается одноразовым ключом (ставится, но обновить поверх сборки с другим ключом нельзя).
Для постоянного ключа добавьте в Settings → Secrets → Actions:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (необязательно).

    keytool -genkeypair -keystore letniye.jks -alias letniye -keyalg RSA -keysize 2048 -validity 10000
    base64 -w0 letniye.jks   # → KEYSTORE_BASE64

## Тема
Светлая по умолчанию. Тёмная — долгое нажатие на логотип в шапке.
