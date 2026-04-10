package com.du.dtc.bike.activity;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.du.dtc.bike.R;

public class AdvancedActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_advanced);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        findViewById(R.id.item_tech_info).setOnClickListener(v -> {
            startActivity(new Intent(this, TechInfoActivity.class));
        });

        findViewById(R.id.item_settings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });
    }
}