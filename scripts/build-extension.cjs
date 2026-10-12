const fs = require('node:fs');
const path = require('node:path');
const operations = ['initialize', 'getPurchaseAvailability', 'getUserAuthorizationStatus', 'getProducts', 'purchase', 'getPurchases', 'getPurchase', 'acknowledgePurchase'];
const setup = `const game = runtimeScene.getGame();
if (!game.__ruStorePayExtensionV1) game.__ruStorePayExtensionV1 = { operations: Object.create(null) };
const state = game.__ruStorePayExtensionV1;
const api = typeof window !== 'undefined' && window.cordova && window.cordova.plugins && window.cordova.plugins.RuStoreGDevelopPay;
const slot = (op) => state.operations[op] || { status: 'idle', result: null, error: null };
const request = (op, makeArgs) => {
  if (slot(op).status === 'pending') return;
  const current = { status: 'pending', result: null, error: null };
  state.operations[op] = current;
  const fail = (error) => {
    if (current.status !== 'pending') return;
    current.status = 'error';
    current.error = error && typeof error === 'object' ? error : { simpleName: 'Error', message: String(error || 'Ошибка RuStore') };
  };
  try {
    const args = makeArgs();
    if (!api || typeof api[op] !== 'function') {
      fail({ simpleName: 'PLUGIN_UNAVAILABLE', message: 'Плагин RuStore недоступен. Проверьте установленный Android APK; браузерный предпросмотр не поддерживает покупки.' });
      return;
    }
    api[op].apply(api, args.concat([
      (result) => {
        if (current.status !== 'pending') return;
        current.result = result == null ? null : result;
        current.status = 'success';
      }, fail
    ]));
  } catch (error) { fail(error); }
};
const requiredText = (key) => {
  const value = String(eventsFunctionContext.getArgument(key) || '').trim();
  if (!value) throw { simpleName: 'INVALID_ARGUMENT', message: 'Не указан параметр: ' + key };
  return value;
};`;
const events = code => [{type: 'BuiltinCommonInstructions::JsCode', inlineCode: (setup + '\n' + code).split('\n'), parameterObjects: '', useStrict: true, eventsSheetExpanded: false}];
const param = (name, description, longDescription = '') => ({name, description, longDescription, type: 'string'});
const operation = () => ({name: 'operation', description: 'Операция RuStore', type: 'stringWithSelector', supplementaryInformation: JSON.stringify(operations), defaultValue: 'purchase'});
const functions = [];
function add(name, fullName, functionType, sentence, code, parameters = [], description = '', group = 'RuStore — платежи') {
  functions.push({name, fullName, functionType, sentence, description, group, events: events(code), parameters, objectGroups: []});
}
add('InitSDK', 'Инициализировать RuStore Pay', 'Action', 'Инициализировать RuStore Pay для приложения 2063744155', "request('initialize', () => ['2063744155']);", [], 'Выполняйте один раз при запуске игры. App ID фиксирован в настройках нативного плагина; это не ID товара.');
add('CheckAvailability', 'Проверить доступность платежей', 'Action', 'Проверить доступность платежей RuStore', "request('getPurchaseAvailability', () => []);");
add('GetAuthorizationStatus', 'Получить статус авторизации', 'Action', 'Получить статус авторизации пользователя RuStore', "request('getUserAuthorizationStatus', () => []);");
add('GetProducts', 'Получить товары', 'Action', 'Получить товары RuStore из JSON _PARAM1_', `request('getProducts', () => {
  let ids;
  try { ids = JSON.parse(requiredText('productIds')); }
  catch (error) { throw { simpleName: 'INVALID_PRODUCTS_JSON', message: 'Нужен JSON-массив ID товаров, например ["coins100","premium"].' }; }
  if (!Array.isArray(ids) || !ids.length || ids.length > 1000 || ids.some(id => typeof id !== 'string' || !id.trim()))
    throw { simpleName: 'INVALID_PRODUCTS_JSON', message: 'Укажите от 1 до 1000 непустых строковых ID товаров.' };
  return [ids.map(id => id.trim())];
});`, [param('productIds', 'JSON-массив ID товаров', '["coins100","premium"]. Введите как строковое выражение GDevelop.')]);
add('Purchase', 'Купить товар', 'Action', 'Купить товар RuStore _PARAM1_', "request('purchase', () => [{ productId: requiredText('productId'), quantity: 1, theme: 'LIGHT' }]);", [param('productId', 'ID товара в консоли RuStore')], 'Одна единица товара, одностадийная оплата. Запускайте по нажатию кнопки с условием «Выполнить один раз». Товар выдаётся только после успешной покупки.');
add('GetPurchases', 'Получить покупки пользователя', 'Action', 'Получить покупки пользователя RuStore', "request('getPurchases', () => []);", [], 'Используйте для восстановления покупок. Проверяйте статус покупки и acknowledgementState; список сам по себе не означает, что нужно выдавать все товары повторно.');
add('GetPurchase', 'Получить покупку по ID', 'Action', 'Получить покупку RuStore _PARAM1_', "request('getPurchase', () => [requiredText('purchaseId')]);", [param('purchaseId', 'ID покупки')]);
add('AcknowledgePurchase', 'Подтвердить выдачу товара', 'Action', 'Подтвердить выдачу товара по покупке RuStore _PARAM1_', "request('acknowledgePurchase', () => [requiredText('purchaseId'), '']);", [param('purchaseId', 'ID покупки')], 'Вызывать ПОСЛЕ выдачи и сохранения товара в игре. Это отметка ACKNOWLEDGED, а не подтверждение списания денег.');
add('ClearResult', 'Очистить обработанный результат', 'Action', 'Очистить результат операции RuStore _PARAM1_', "const op = String(eventsFunctionContext.getArgument('operation'));\nif (slot(op).status !== 'pending') delete state.operations[op];", [operation()], 'Очищайте после обработки события успеха или ошибки. Запрос в процессе оплаты не отменяется.');
for (const [name, title, status] of [['OperationPending', 'Операция выполняется', 'pending'], ['OperationSucceeded', 'Операция завершилась успешно', 'success'], ['OperationFailed', 'Операция завершилась с ошибкой', 'error']]) {
  add(name, title, 'Condition', title + ': _PARAM1_', `eventsFunctionContext.returnValue = slot(String(eventsFunctionContext.getArgument('operation'))).status === '${status}';`, [operation()], 'Состояние сохраняется до следующего запроса или действия «Очистить обработанный результат». Для одноразовой обработки используйте «Выполнить один раз».', 'RuStore — состояния');
}
add('PluginAvailable', 'Нативный плагин доступен', 'Condition', 'Нативный плагин RuStore доступен', "eventsFunctionContext.returnValue = !!(api && typeof api.purchase === 'function');", [], 'Проверяет наличие плагина, а не возможность оплаты.', 'RuStore — состояния');
add('PaymentsAvailable', 'Платежи доступны', 'Condition', 'Платежи RuStore доступны', "const availability = slot('getPurchaseAvailability');\neventsFunctionContext.returnValue = availability.status === 'success' && !!(availability.result && availability.result.available === true);", [], 'Сначала вызовите проверку доступности и дождитесь результата.', 'RuStore — состояния');
add('ResultJSON', 'Результат операции в JSON', 'StringExpression', 'Результат операции RuStore _PARAM1_', "const value = slot(String(eventsFunctionContext.getArgument('operation'))).result;\neventsFunctionContext.returnValue = value == null ? '' : JSON.stringify(value);", [operation()], 'Строка для штатного действия GDevelop «Преобразовать JSON в переменную».', 'RuStore — результаты');
for (const [name, title, key] of [['ErrorMessage', 'Текст ошибки', 'message'], ['ErrorCode', 'Код ошибки', 'simpleName']]) {
  add(name, title, 'StringExpression', title + ' RuStore _PARAM1_', `const error = slot(String(eventsFunctionContext.getArgument('operation'))).error;\neventsFunctionContext.returnValue = error && error.${key} ? String(error.${key}) : '';`, [operation()], '', 'RuStore — результаты');
}
for (const [name, title, key] of [['PurchaseId', 'ID успешной покупки', 'purchaseId'], ['PurchasedProductId', 'ID купленного товара', 'productId'], ['InvoiceId', 'ID счёта покупки', 'invoiceId']]) {
  add(name, title, 'StringExpression', title, `const purchase = slot('purchase');\neventsFunctionContext.returnValue = purchase.status === 'success' && purchase.result && purchase.result.${key} != null ? String(purchase.result.${key}) : '';`, [], '', 'RuStore — результаты');
}
const extension = {
  author: 'Артем Жирнов', category: 'Платежи', dimension: '', extensionNamespace: '',
  fullName: 'RuStore Pay — покупки для Android', gdevelopVersion: '',
  helpPath: 'https://github.com/artemkz147-ship-it/rustorpay147/blob/main/GDEVELOP_RU.md',
  iconUrl: 'https://resources.gdevelop-app.com/assets/Icons/Glyphster Pack/Master/SVG/Shopping and Ecommerce/Shopping and Ecommerce_wallet_money_cash.svg',
  name: 'RuStorePay', previewIconUrl: '', shortDescription: 'Платежи RuStore Pay SDK 11.1.0 через Cordova-плагин 1.0.3.', version: '1.0.0',
  description: ['Покупки RuStore для установленной Android-игры. В браузерном предпросмотре платежи недоступны.', 'Нативный плагин закреплён на проверенном коммите конфигурации SDK 11.1.0. Android-сборка и реальные платежи требуют проверки на устройстве.', 'Console App ID: 2063744155. Для другого приложения нужны другие настройки нативного плагина.', 'Расширение не выдаёт игровые товары автоматически: выдавайте и сохраняйте их по успешному результату покупки, затем подтверждайте выдачу.'],
  tags: ['rustore', 'покупки', 'android', 'cordova', 'iap'], authorIds: [],
  dependencies: [{exportName: 'ru.rustore.gdevelop.pay', name: 'RuStore Pay for GDevelop', type: 'cordova', version: 'https://github.com/artemkz147-ship-it/rustorpay147.git#5d3cb99ab786cdcf89d6943824b484c22d063c62'}],
  globalVariables: [], sceneVariables: [], eventsFunctions: functions, eventsFunctionsFolderStructure: [], eventsBasedBehaviors: [], eventsBasedObjects: []
};
fs.writeFileSync(path.join(__dirname, '..', 'RuStorePay_GDevelop_1.0.0.json'), JSON.stringify(extension, null, 2) + '\n');
console.log('Created RuStorePay_GDevelop_1.0.0.json: ' + functions.length + ' functions');
