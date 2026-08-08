var exec = require('cordova/exec');

function decode(value) {
  if (typeof value !== 'string') return value;
  try { return JSON.parse(value); } catch (_) { return value; }
}

function call(action, args, success, error) {
  exec(
    function (value) { if (success) success(decode(value)); },
    function (value) { if (error) error(decode(value)); },
    'RuStoreGDevelopPay',
    action,
    args || []
  );
}

module.exports = {
  initialize: function (consoleAppId, success, error) {
    call('initialize', [String(consoleAppId || '')], success, error);
  },
  getPurchaseAvailability: function (success, error) {
    call('getPurchaseAvailability', [], success, error);
  },
  getUserAuthorizationStatus: function (success, error) {
    call('getUserAuthorizationStatus', [], success, error);
  },
  getProducts: function (productIds, success, error) {
    call('getProducts', [Array.isArray(productIds) ? productIds : []], success, error);
  },
  purchase: function (params, success, error) {
    call('purchase', [params || {}], success, error);
  },
  getPurchases: function (success, error) {
    call('getPurchases', [], success, error);
  },
  getPurchase: function (purchaseId, success, error) {
    call('getPurchase', [String(purchaseId || '')], success, error);
  },
  acknowledgePurchase: function (purchaseId, developerPayload, success, error) {
    call('acknowledgePurchase', [String(purchaseId || ''), String(developerPayload || '')], success, error);
  }
};
