RuStore Pay GDevelop v2.1.0 — FIX deeplink

Для текущей игры по умолчанию:
Console App ID: 2063744599
App scheme: com.coupledare.iskra.rustorepay

Что исправлено:
- RuStore Pay снова использует штатную автоматическую инициализацию SDK через AndroidManifest (как в рабочем v1.0.2).
- sdk_pay_scheme_value явно добавляется в AndroidManifest.
- дополнительно добавлен app_scheme_value для совместимости с текстом ошибки SDK.
- deeplink activity принимает com.coupledare.iskra.rustorepay://... и передает Intent в RuStore Pay.
- ручной RuStorePayClientProvider.provide() НЕ вызывается, поэтому нет двойной инициализации.

Для другой игры при ручной установке плагина можно переопределить:
RUSTORE_CONSOLE_APP_ID
RUSTORE_APP_SCHEME
