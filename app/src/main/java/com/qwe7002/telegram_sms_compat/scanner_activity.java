package com.qwe7002.telegram_sms_compat;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Toast;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

public class scanner_activity extends Activity {

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_scanner);
        Toolbar toolbar = findViewById(R.id.scan_toolbar);
        toolbar.setTitle(R.string.scan_title);
        toolbar.setTitleTextColor(Color.WHITE);

        // QR scanning temporarily disabled due to library unavailability
        Toast.makeText(this, "QR code scanning is currently unavailable. Please enter configuration manually.", Toast.LENGTH_LONG).show();

        // Return empty result to indicate cancellation
        setResult(Activity.RESULT_CANCELED);
        finish();
    }


}
