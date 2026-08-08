package ru.rustore.gdevelop.pay;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import ru.rustore.sdk.pay.RuStorePayClient;
import ru.rustore.sdk.pay.model.SdkTheme;

/**
 * Receives the payment return deeplink without requiring edits to Cordova's MainActivity.
 * The Console App ID is persisted by the initialize action so the SDK can be restored
 * even if Android killed the game process while an external payment app was open.
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
            // The game will surface SDK state on the next explicit operation.
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
