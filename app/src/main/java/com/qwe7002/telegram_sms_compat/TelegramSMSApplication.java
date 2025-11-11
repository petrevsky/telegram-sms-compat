package com.qwe7002.telegram_sms_compat;

import android.app.Application;
import android.util.Log;

import org.conscrypt.Conscrypt;

import java.security.Security;

/**
 * Custom Application class to install Conscrypt security provider globally
 * for TLS 1.2/1.3 support on Android 4.4.4 across all app components.
 */
public class TelegramSMSApplication extends Application {
    private static final String TAG = "TelegramSMSApp";

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Install Conscrypt as the top security provider for TLS 1.2/1.3 support
        // This runs in EVERY process (main, :command, :battery) as soon as the app starts
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1);
            Log.d(TAG, "Conscrypt installed globally - TLS 1.2/1.3 enabled in all processes");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Conscrypt native library not available: " + e.getMessage());
            Log.w(TAG, "Will use manual TLS 1.2 configuration in OkHttp instead");
            // The app will continue and use the manual TLS 1.2 configuration in public_func.get_okhttp_obj()
        } catch (Exception e) {
            Log.e(TAG, "Failed to install Conscrypt security provider", e);
            Log.w(TAG, "Will use manual TLS 1.2 configuration in OkHttp instead");
        }
    }
}

