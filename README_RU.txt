RuStore Pay for GDevelop — v1.0.1

Что исправлено:
- устранена двойная инициализация RuStore Pay SDK;
- при сборке Android удаляется авто-провайдер RuStorePayContentProvider;
- остаётся ручная инициализация через Console App ID из GDevelop;
- API JavaScript не изменён, старое расширение GDevelop продолжает работать.

Как использовать:
1. Замени содержимое репозитория rustorpay147 файлами из этой папки.
2. Убедись, что plugin.xml лежит в корне репозитория.
3. В GDevelop зависимость оставь той же:
   https://github.com/artemkz147-ship-it/rustorpay147.git
4. Собери Android заново. Лучше удалить старую APK с телефона перед тестом.

Для игры:
Package Name: com.artem.sexivideopuzzles
Console App ID: 2063744155
Product ID: pazleprtm147
