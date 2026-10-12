RuStore Pay for GDevelop/Cordova v1.0.3

Обновление 1.0.3:
- Pay SDK 11.1.0 через BOM 2026.08.01;
- Maven-сервер nexus-external.rustore.ru;
- Console App ID и deeplink-схема в AndroidManifest ссылаются на строковые ресурсы;
- Console App ID по умолчанию сохранён: 2063744155.

Исправлено:
- убран tools:node="remove", из-за которого Cordova-сборка падала с ошибкой prefix tools is not bound;
- убрана ручная повторная инициализация RuStorePayClientProvider;
- используется штатная автоматическая инициализация Pay SDK через AndroidManifest;
- сохранён прежний JS API cordova.plugins.RuStoreGDevelopPay;
- Console App ID по умолчанию: 2063744155;
- deeplink-схема: $PACKAGE_NAME.rustorepay.

Если зависимость расширения GDevelop закреплена на старом коммите или теге,
обновите её до коммита с версией 1.0.3 и заново соберите Android-приложение.
При обновлении сохраните прежний Package Name и настройки подписи.
На телефоне проверьте товары, тестовую покупку, возврат после оплаты
и отсутствие повторной выдачи одной покупки. Подробности в README_RU.md.
