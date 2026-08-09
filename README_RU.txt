RuStore Pay Universal for GDevelop — v2.0.0

ОДИН ПЛАГИН ДЛЯ ВСЕХ ИГР
=========================

В плагине больше НЕ зашиты:
- Console App ID;
- Product ID;
- package name конкретной игры.

Каждая GDevelop-игра передаёт свой Console App ID через:
cordova.plugins.RuStoreGDevelopPay.initialize("CONSOLE_APP_ID", ...)

Product ID передаётся в purchase({ productId: "PRODUCT_ID", ... }).
Package name берётся автоматически из Android-сборки и используется в deeplink scheme.

Почему это работает:
- авто-провайдер RuStorePayContentProvider удаляется из итогового AndroidManifest;
- RuStore Pay создаётся один раз вручную через RuStorePayClientProvider.provide(...);
- xmlns:tools находится прямо на <provider>, поэтому Cordova не теряет namespace при переносе узла;
- API JavaScript совместим с прежним мостом.

Как использовать во всех проектах:
1. Один раз замени содержимое репозитория rustorpay147 содержимым этого архива.
2. В каждой игре оставь Cordova dependency:
   https://github.com/artemkz147-ship-it/rustorpay147.git
3. В коде конкретной игры укажи её Console App ID и Product ID.
4. Собери Android заново.

Не нужно делать отдельную копию плагина под каждую игру.
