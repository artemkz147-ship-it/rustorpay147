RuStore Pay for GDevelop/Cordova v1.0.2

Исправлено:
- убран tools:node="remove", из-за которого Cordova-сборка падала с ошибкой prefix tools is not bound;
- убрана ручная повторная инициализация RuStorePayClientProvider;
- используется штатная автоматическая инициализация Pay SDK через AndroidManifest;
- сохранён прежний JS API cordova.plugins.RuStoreGDevelopPay;
- Console App ID по умолчанию: 2063744155;
- deeplink-схема: $PACKAGE_NAME.rustorepay.

Для текущей игры ничего менять в game.json не нужно.
Замените файлы в репозитории rustorpay147 и заново соберите Android-приложение.
