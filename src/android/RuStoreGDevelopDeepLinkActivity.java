package ru.rustore.gdevelop.pay;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import ru.rustore.sdk.pay.RuStorePayClient;
import ru.rustore.sdk.pay.model.SdkTheme;

/**
 * Принимает deeplink возврата после оплаты без изменений Cordova MainActivity.
 * Console App ID сохраняется при инициализации, поэтому SDK можно восстановить,
 * даже если Android завершил процесс игры, пока было открыто внешнее окно оплаты.
 */
public final class RuStoreGDevelopDeepLinkActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }

    private void handle(Intent intent) {
        try {
            String appId = RuStoreGDevelopPay.readConsoleAppId(this);
            RuStorePayClient client = RuStoreGDevelopPay.ensureClient(this, appId);
            if (client != null) {
                client.getIntentInteractor().proceedIntent(intent, SdkTheme.LIGHT);
            }
        } catch (Throwable ignored) {
            // Игра получит состояние SDK при следующем явном обращении к плагину.
        }

        try {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(launchIntent);
            }
        } finally {
            finish();
        }
    }
}
