package ru.rustore.gdevelop.pay;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ru.rustore.sdk.pay.RuStorePayClient;
import ru.rustore.sdk.pay.RuStorePayClientProvider;
import ru.rustore.sdk.pay.model.AcknowledgementState;
import ru.rustore.sdk.pay.model.AppUserEmail;
import ru.rustore.sdk.pay.model.AppUserId;
import ru.rustore.sdk.pay.model.ConsoleApplicationId;
import ru.rustore.sdk.pay.model.DeveloperPayload;
import ru.rustore.sdk.pay.model.OrderId;
import ru.rustore.sdk.pay.model.PreferredPurchaseType;
import ru.rustore.sdk.pay.model.ProductId;
import ru.rustore.sdk.pay.model.ProductPurchaseParams;
import ru.rustore.sdk.pay.model.PurchaseId;
import ru.rustore.sdk.pay.model.Quantity;
import ru.rustore.sdk.pay.model.SdkTheme;

public final class RuStoreGDevelopPay extends CordovaPlugin {
    private static final String PREFS = "rustore_gdevelop_pay";
    private static final String PREF_APP_ID = "console_app_id";
    private static volatile RuStorePayClient client;

    @Override
    protected void pluginInitialize() {
        super.pluginInitialize();
        try {
            String saved = readConsoleAppId(cordova.getActivity());
            if (!saved.isEmpty()) {
                RuStorePayClient c = ensureClient(cordova.getActivity(), saved);
                Intent intent = cordova.getActivity().getIntent();
                if (c != null && intent != null) {
                    c.getIntentInteractor().proceedIntent(intent, SdkTheme.LIGHT);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        try {
            String saved = readConsoleAppId(cordova.getActivity());
            RuStorePayClient c = ensureClient(cordova.getActivity(), saved);
            if (c != null) c.getIntentInteractor().proceedIntent(intent, SdkTheme.LIGHT);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callback) {
        try {
            switch (action) {
                case "initialize":
                    initialize(args, callback);
                    return true;
                case "getPurchaseAvailability":
                    getPurchaseAvailability(callback);
                    return true;
                case "getUserAuthorizationStatus":
                    getUserAuthorizationStatus(callback);
                    return true;
                case "getProducts":
                    getProducts(args, callback);
                    return true;
                case "purchase":
                    purchase(args, callback);
                    return true;
                case "getPurchases":
                    getPurchases(callback);
                    return true;
                case "getPurchase":
                    getPurchase(args, callback);
                    return true;
                case "acknowledgePurchase":
                    acknowledgePurchase(args, callback);
                    return true;
                default:
                    return false;
            }
        } catch (Throwable error) {
            fail(callback, error);
            return true;
        }
    }

    private void initialize(JSONArray args, CallbackContext callback) {
        final String appId = args.optString(0, "").trim();
        if (appId.isEmpty()) {
            fail(callback, "INVALID_CONSOLE_APP_ID", "Console App ID is empty");
            return;
        }
        if (!appId.matches("[0-9]+")) {
            fail(callback, "INVALID_CONSOLE_APP_ID", "Console App ID must contain digits only");
            return;
        }

        cordova.getThreadPool().execute(() -> {
            try {
                SharedPreferences prefs = cordova.getActivity().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                prefs.edit().putString(PREF_APP_ID, appId).apply();
                RuStorePayClient c = ensureClient(cordova.getActivity(), appId);
                JSONObject out = new JSONObject();
                out.put("initialized", c != null);
                out.put("consoleAppId", appId);
                out.put("deeplinkScheme", cordova.getActivity().getPackageName() + ".rustorepay");
                success(callback, out);
            } catch (Throwable error) {
                fail(callback, error);
            }
        });
    }

    static String readConsoleAppId(Context context) {
        if (context == null) return "";
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(PREF_APP_ID, "");
    }

    static synchronized RuStorePayClient ensureClient(Context context, String consoleAppId) {
        if (client != null) return client;

        // Use the public manual-initialization API deliberately. This keeps the
        // Console App ID project-specific without requiring a generated manifest
        // resource or a manual Android Studio edit.
        String appId = consoleAppId == null ? "" : consoleAppId.trim();
        if (appId.isEmpty()) appId = readConsoleAppId(context);
        if (appId.isEmpty()) return null;

        client = new RuStorePayClientProvider().provide(
                context,
                new ConsoleApplicationId(appId),
                Collections.emptyMap()
        );
        return client;
    }

    private RuStorePayClient requireClient() {
        String appId = readConsoleAppId(cordova.getActivity());
        RuStorePayClient c = ensureClient(cordova.getActivity(), appId);
        if (c == null) throw new IllegalStateException("Call Initialize RuStore Pay first");
        return c;
    }

    private void getPurchaseAvailability(CallbackContext callback) {
        try {
            requireClient().getPurchaseInteractor().getPurchaseAvailability()
                    .addOnSuccessListener(result -> {
                        try {
                            JSONObject out = new JSONObject();
                            String simpleName = result == null ? "" : result.getClass().getSimpleName();
                            out.put("available", "Available".equals(simpleName));
                            out.put("result", toJsonValue(result, 0));
                            success(callback, out);
                        } catch (Throwable error) {
                            fail(callback, error);
                        }
                    })
                    .addOnFailureListener(error -> fail(callback, error));
        } catch (Throwable error) {
            fail(callback, error);
        }
    }

    private void getUserAuthorizationStatus(CallbackContext callback) {
        try {
            requireClient().getUserInteractor().getUserAuthorizationStatus()
                    .addOnSuccessListener(result -> {
                        try {
                            JSONObject out = new JSONObject();
                            out.put("status", result == null ? "UNKNOWN" : result.toString());
                            success(callback, out);
                        } catch (Throwable error) {
                            fail(callback, error);
                        }
                    })
                    .addOnFailureListener(error -> fail(callback, error));
        } catch (Throwable error) {
            fail(callback, error);
        }
    }

    private void getProducts(JSONArray args, CallbackContext callback) {
        try {
            JSONArray input = args.optJSONArray(0);
            java.util.ArrayList<ProductId> ids = new java.util.ArrayList<>();
            if (input != null) {
                for (int i = 0; i < input.length(); i++) {
                    String id = input.optString(i, "").trim();
                    if (!id.isEmpty()) ids.add(new ProductId(id));
                }
            }
            requireClient().getProductInteractor().getProducts(ids)
                    .addOnSuccessListener(products -> {
                        try { success(callback, toJsonValue(products, 0)); }
                        catch (Throwable error) { fail(callback, error); }
                    })
                    .addOnFailureListener(error -> fail(callback, error));
        } catch (Throwable error) {
            fail(callback, error);
        }
    }

    private void purchase(JSONArray args, CallbackContext callback) {
        try {
            JSONObject p = args.optJSONObject(0);
            if (p == null) p = new JSONObject();
            final String productId = p.optString("productId", "").trim();
            if (productId.isEmpty()) {
                fail(callback, "INVALID_PRODUCT_ID", "productId is required");
                return;
            }

            int quantity = Math.max(1, p.optInt("quantity", 1));
            String orderId = p.optString("orderId", "").trim();
            String developerPayload = p.optString("developerPayload", "").trim();
            String appUserId = p.optString("appUserId", "").trim();
            String appUserEmail = p.optString("appUserEmail", "").trim();
            String themeName = p.optString("theme", "LIGHT").trim().toUpperCase(Locale.ROOT);
            SdkTheme theme = "DARK".equals(themeName) ? SdkTheme.DARK : SdkTheme.LIGHT;

            ProductPurchaseParams params = new ProductPurchaseParams(
                    new ProductId(productId),
                    new Quantity(quantity),
                    orderId.isEmpty() ? null : new OrderId(orderId),
                    developerPayload.isEmpty() ? null : new DeveloperPayload(developerPayload),
                    appUserId.isEmpty() ? null : new AppUserId(appUserId),
                    appUserEmail.isEmpty() ? null : new AppUserEmail(appUserEmail)
            );

            // The SDK starts a native payment UI, so invoke it on Android's UI thread.
            final ProductPurchaseParams finalParams = params;
            cordova.getActivity().runOnUiThread(() -> {
                try {
                    requireClient().getPurchaseInteractor()
                            .purchase(finalParams, PreferredPurchaseType.ONE_STEP, theme, null)
                            .addOnSuccessListener(result -> {
                                try { success(callback, toJsonValue(result, 0)); }
                                catch (Throwable error) { fail(callback, error); }
                            })
                            .addOnFailureListener(error -> fail(callback, error));
                } catch (Throwable error) {
                    fail(callback, error);
                }
            });
        } catch (Throwable error) {
            fail(callback, error);
        }
    }

    private void getPurchases(CallbackContext callback) {
        try {
            requireClient().getPurchaseInteractor().getPurchases(null, null, null)
                    .addOnSuccessListener(purchases -> {
                        try { success(callback, toJsonValue(purchases, 0)); }
                        catch (Throwable error) { fail(callback, error); }
                    })
                    .addOnFailureListener(error -> fail(callback, error));
        } catch (Throwable error) {
            fail(callback, error);
        }
    }

    private void getPurchase(JSONArray args, CallbackContext callback) {
        try {
            String id = args.optString(0, "").trim();
            if (id.isEmpty()) {
                fail(callback, "INVALID_PURCHASE_ID", "purchaseId is required");
                return;
            }
            requireClient().getPurchaseInteractor().getPurchase(new PurchaseId(id))
                    .addOnSuccessListener(purchase -> {
                        try { success(callback, toJsonValue(purchase, 0)); }
                        catch (Throwable error) { fail(callback, error); }
                    })
                    .addOnFailureListener(error -> fail(callback, error));
        } catch (Throwable error) {
            fail(callback, error);
        }
    }

    private void acknowledgePurchase(JSONArray args, CallbackContext callback) {
        try {
            String id = args.optString(0, "").trim();
            String payload = args.optString(1, "").trim();
            if (id.isEmpty()) {
                fail(callback, "INVALID_PURCHASE_ID", "purchaseId is required");
                return;
            }
            requireClient().getPurchaseInteractor().updateAcknowledgementState(
                    new PurchaseId(id),
                    AcknowledgementState.ACKNOWLEDGED,
                    payload.isEmpty() ? null : new DeveloperPayload(payload)
            )
                    .addOnSuccessListener(state -> {
                        try {
                            JSONObject out = new JSONObject();
                            out.put("purchaseId", id);
                            out.put("acknowledgementState", state == null ? "UNKNOWN" : state.toString());
                            success(callback, out);
                        } catch (Throwable error) {
                            fail(callback, error);
                        }
                    })
                    .addOnFailureListener(error -> fail(callback, error));
        } catch (Throwable error) {
            fail(callback, error);
        }
    }

    private static void success(CallbackContext callback, Object value) {
        callback.success(value instanceof String ? (String) value : String.valueOf(value));
    }

    private static void fail(CallbackContext callback, String name, String message) {
        try {
            JSONObject out = new JSONObject();
            out.put("simpleName", name == null ? "Error" : name);
            out.put("message", message == null ? "" : message);
            callback.error(out.toString());
        } catch (Throwable ignored) {
            callback.error(message == null ? "RuStore Pay error" : message);
        }
    }

    private static void fail(CallbackContext callback, Throwable error) {
        try {
            JSONObject out = new JSONObject();
            Throwable cause = error;
            if (error instanceof java.lang.reflect.InvocationTargetException && error.getCause() != null) {
                cause = error.getCause();
            }
            out.put("simpleName", cause == null ? "Error" : cause.getClass().getSimpleName());
            out.put("message", cause == null || cause.getMessage() == null ? "" : cause.getMessage());
            if (cause != null) {
                Object details = toJsonValue(cause, 0);
                out.put("details", details);
            }
            callback.error(out.toString());
        } catch (Throwable ignored) {
            callback.error(error == null ? "RuStore Pay error" : String.valueOf(error));
        }
    }

    /** Convert SDK model objects to JSON without coupling the bridge to every model subclass. */
    private static Object toJsonValue(Object value, int depth) throws Exception {
        if (value == null) return JSONObject.NULL;
        if (value == JSONObject.NULL) return value;
        if (value instanceof String || value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof Enum<?>) return ((Enum<?>) value).name();
        if (value instanceof Date) return ((Date) value).getTime();
        if (depth > 5) return String.valueOf(value);

        if (value instanceof Iterable<?>) {
            JSONArray array = new JSONArray();
            for (Object item : (Iterable<?>) value) array.put(toJsonValue(item, depth + 1));
            return array;
        }
        if (value instanceof Map<?, ?>) {
            JSONObject object = new JSONObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                object.put(String.valueOf(entry.getKey()), toJsonValue(entry.getValue(), depth + 1));
            }
            return object;
        }

        // Kotlin value classes in the SDK expose getValue().
        try {
            Method getValue = value.getClass().getMethod("getValue");
            if (getValue.getParameterTypes().length == 0) {
                Object unwrapped = getValue.invoke(value);
                if (unwrapped != value) return toJsonValue(unwrapped, depth + 1);
            }
        } catch (NoSuchMethodException ignored) {
        }

        JSONObject object = new JSONObject();
        object.put("_class", value.getClass().getSimpleName());
        for (Method method : value.getClass().getMethods()) {
            if (method.getParameterTypes().length != 0) continue;
            String methodName = method.getName();
            if ("getClass".equals(methodName) || "hashCode".equals(methodName) || "toString".equals(methodName)) continue;
            String key = null;
            if (methodName.startsWith("get") && methodName.length() > 3) {
                key = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
            } else if (methodName.startsWith("is") && methodName.length() > 2) {
                key = Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
            }
            if (key == null || "class".equals(key)) continue;
            try {
                Object child = method.invoke(value);
                object.put(key, toJsonValue(child, depth + 1));
            } catch (Throwable ignored) {
                // Skip getters that are not safe to evaluate in this context.
            }
        }
        return object;
    }
}
