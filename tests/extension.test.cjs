const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const file = path.join(__dirname, '..', 'RuStorePay_GDevelop_1.0.0.json');

function extension() {
  assert.ok(fs.existsSync(file), 'Importable extension JSON must exist');
  return JSON.parse(fs.readFileSync(file, 'utf8'));
}
function harness(nativeAvailable = true) {
  const data = extension();
  const calls = [];
  const bridge = {module: {exports: {}}, require: () => (ok, fail, service, action, args) => calls.push({ok, fail, service, action, args})};
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '..', 'www', 'RuStoreGDevelopPay.js'), 'utf8'), bridge);
  const game = {};
  const context = vm.createContext({runtimeScene: {getGame: () => game}, window: nativeAvailable ? {cordova: {plugins: {RuStoreGDevelopPay: bridge.module.exports}}} : {}});
  function run(name, args = {}) {
    const f = data.eventsFunctions.find(f => f.name === name);
    assert.ok(f, 'Missing function ' + name);
    context.eventsFunctionContext = {getArgument: key => args[key], returnValue: undefined};
    // GDevelop compiles each JS event into a function with local scope.
    for (const event of f.events) vm.runInContext('(function () { "use strict";\n' + event.inlineCode.join('\n') + '\n})();', context);
    return context.eventsFunctionContext.returnValue;
  }
  return {run, calls, context};
}

test('extension is importable and pins the updated native plugin', () => {
  const e = extension();
  assert.equal(e.name, 'RuStorePay');
  assert.equal(e.dependencies[0].type, 'cordova');
  assert.ok(e.dependencies[0].version.endsWith('#5d3cb99ab786cdcf89d6943824b484c22d063c62'));
  assert.equal(new Set(e.eventsFunctions.map(f => f.name)).size, e.eventsFunctions.length);
});
test('purchase forwards ID and prevents concurrent duplicate payment', () => {
  const h = harness();
  h.run('Purchase', {productId: 'coins100'});
  h.run('Purchase', {productId: 'coins100'});
  assert.equal(h.calls.length, 1);
  assert.equal(h.calls[0].action, 'purchase');
  assert.equal(h.calls[0].args[0].productId, 'coins100');
  assert.equal(h.run('OperationPending', {operation: 'purchase'}), true);
  assert.equal(h.run('OperationSucceeded', {operation: 'purchase'}), false);
  h.calls[0].ok(JSON.stringify({purchaseId: 'p1', productId: 'coins100', invoiceId: 'i1', purchaseType: 'ONE_STEP'}));
  assert.equal(h.run('OperationSucceeded', {operation: 'purchase'}), true);
  assert.equal(h.run('PurchaseId'), 'p1');
  assert.equal(h.run('PurchasedProductId'), 'coins100');
  assert.equal(h.run('InvoiceId'), 'i1');
  // Duplicate native callbacks must not replace a completed result.
  h.calls[0].fail(JSON.stringify({message: 'late error'}));
  assert.equal(h.run('OperationSucceeded', {operation: 'purchase'}), true);
  h.run('ClearResult', {operation: 'purchase'});
  assert.equal(h.run('OperationSucceeded', {operation: 'purchase'}), false);
  assert.equal(h.run('PurchaseId'), '');
});
test('cancellation is an error and never becomes purchase success', () => {
  const h = harness();
  h.run('Purchase', {productId: 'coins100'});
  h.calls[0].fail(JSON.stringify({simpleName: 'ProductPurchaseCancelled', message: 'cancelled'}));
  assert.equal(h.run('OperationFailed', {operation: 'purchase'}), true);
  assert.equal(h.run('OperationSucceeded', {operation: 'purchase'}), false);
  assert.equal(h.run('ErrorCode', {operation: 'purchase'}), 'ProductPurchaseCancelled');
  assert.equal(h.run('PurchaseId'), '');
});
test('browser preview reports missing plugin without pretending to pay', () => {
  const h = harness(false);
  h.run('Purchase', {productId: 'coins100'});
  assert.equal(h.calls.length, 0);
  assert.equal(h.run('PluginAvailable'), false);
  assert.equal(h.run('OperationFailed', {operation: 'purchase'}), true);
  assert.equal(h.run('ErrorCode', {operation: 'purchase'}), 'PLUGIN_UNAVAILABLE');
});
test('invalid inputs never invoke payment API', () => {
  const h = harness();
  h.run('Purchase', {productId: '  '});
  h.run('GetProducts', {productIds: '{bad json}'});
  h.run('AcknowledgePurchase', {purchaseId: ''});
  assert.equal(h.calls.length, 0);
  assert.equal(h.run('OperationFailed', {operation: 'purchase'}), true);
  assert.equal(h.run('OperationFailed', {operation: 'getProducts'}), true);
});
test('products and purchases retain separate results across requests and scenes', () => {
  const h = harness();
  h.run('GetProducts', {productIds: '["coins100","premium"]'});
  h.calls[0].ok('[{"productId":"coins100","amountLabel":"100 ₽"}]');
  h.run('GetPurchases');
  h.calls[1].ok('[{"purchaseId":"old1","acknowledgementState":"PENDING"}]');
  assert.equal(JSON.parse(h.run('ResultJSON', {operation: 'getProducts'}))[0].amountLabel, '100 ₽');
  assert.equal(JSON.parse(h.run('ResultJSON', {operation: 'getPurchases'}))[0].purchaseId, 'old1');
  assert.equal(h.run('OperationSucceeded', {operation: 'purchase'}), false);
  h.run('AcknowledgePurchase', {purchaseId: 'old1'});
  assert.equal(h.calls[2].action, 'acknowledgePurchase');
  assert.equal(h.calls[2].args[0], 'old1');
});
test('availability is based on SDK response, not plugin existence', () => {
  const h = harness();
  assert.equal(h.run('PaymentsAvailable'), false);
  h.run('CheckAvailability');
  h.calls[0].ok('{"available":false}');
  assert.equal(h.run('PaymentsAvailable'), false);
  h.run('CheckAvailability');
  h.calls[1].ok('{"available":true}');
  assert.equal(h.run('PaymentsAvailable'), true);
});
test('pending operation cannot be cleared and accidentally started twice', () => {
  const h = harness();
  h.run('Purchase', {productId: 'coins100'});
  h.run('ClearResult', {operation: 'purchase'});
  h.run('Purchase', {productId: 'coins100'});
  assert.equal(h.calls.length, 1);
});
