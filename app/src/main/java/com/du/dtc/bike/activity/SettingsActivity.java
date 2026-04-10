package com.du.dtc.bike.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.du.dtc.bike.R;
import com.du.dtc.bike.ble.BikeBleFreq;

public class SettingsActivity extends AppCompatActivity {

    private RadioGroup rgScanFilter;
    private RadioButton rbFilterDatbike, rbFilterAll;
    private Switch swHistory;
    private EditText edtLogDrive, edtLogPark, edtLogOff;
    private EditText edtPollActive, edtPollBg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Scan filter
        rgScanFilter = findViewById(R.id.rg_scan_filter);
        rbFilterDatbike = findViewById(R.id.rb_filter_datbike);
        rbFilterAll = findViewById(R.id.rb_filter_all);

        swHistory = findViewById(R.id.sw_history);

        // Logging group
        edtLogDrive = findViewById(R.id.edt_log_drive);
        edtLogPark = findViewById(R.id.edt_log_park);
        edtLogOff = findViewById(R.id.edt_log_off); // Ô này hiển thị theo Phút

        // Polling group
        edtPollActive = findViewById(R.id.edt_poll_active);
        edtPollBg = findViewById(R.id.edt_poll_bg);

        loadCurrentSettings();

        findViewById(R.id.btn_save).setOnClickListener(v -> saveSettings());
    }

    private void loadCurrentSettings() {
        // Load Radio Group (Lọc thiết bị)
        if (BikeBleFreq.isOnlyShowDatBike) {
            rbFilterDatbike.setChecked(true);
        } else {
            rbFilterAll.setChecked(true);
        }

        swHistory.setChecked(BikeBleFreq.isAllowHistoryLog);

        // Logging (chia 1000 ra Giây)
        edtLogDrive.setText(String.valueOf(BikeBleFreq.logDrive / 1000));
        edtLogPark.setText(String.valueOf(BikeBleFreq.logPark / 1000));
        // Ghi lúc tắt máy thường rất lâu (ví dụ 1 tiếng), nên chia 60.000 để hiện thị
        // Phút
        edtLogOff.setText(String.valueOf(BikeBleFreq.logOff / 60000));

        // Polling (Lưu ý: Active có thể là 0.7s, nếu dùng số thập phân thì xử lý tại
        // đây, hiện tại quy tròn)
        edtPollActive.setText(String.valueOf(BikeBleFreq.pollActive / 1000.0f).replace(".0", ""));
        edtPollBg.setText(String.valueOf(BikeBleFreq.pollBg / 1000));
    }

    private void saveSettings() {
        try {
            // 1. Đọc và Parse dữ liệu an toàn
            boolean isOnlyShowDatBike = rbFilterDatbike.isChecked();

            int logD = parseSeconds(edtLogDrive, 30);
            int logP = parseSeconds(edtLogPark, 300);
            int logO = parseMinutesAsMillis(edtLogOff, 60);

            int pollA = parseMillis(edtPollActive, 1000);
            int pollB = parseSeconds(edtPollBg, 60);

            // 2. Lưu vào SharedPreferences
            SharedPreferences.Editor editor = getSharedPreferences("BikeFreqPrefs", Context.MODE_PRIVATE).edit();

            editor.putBoolean("isOnlyShowDatBike", isOnlyShowDatBike);
            editor.putBoolean("isAllowHistoryLog", swHistory.isChecked());

            // Lưu Logging
            editor.putInt("logDrive", logD);
            editor.putInt("logPark", logP);
            editor.putInt("logOff", logO);

            // Lưu Polling
            editor.putInt("pollActive", pollA);
            editor.putInt("pollBg", pollB);

            editor.apply();

            // Cập nhật ngay vào RAM để có tác dụng lập tức
            BikeBleFreq.init(this);

            Toast.makeText(this, "Đã lưu cấu hình thành công!", Toast.LENGTH_SHORT).show();
            finish();

        } catch (Exception e) {
            Toast.makeText(this, "Lỗi nhập liệu! Vui lòng chỉ nhập số hợp lệ.", Toast.LENGTH_LONG).show();
        }
    }

    private int parseSeconds(EditText editText, int defaultSeconds) {
        String text = editText.getText().toString().trim();
        if (text.isEmpty())
            return defaultSeconds * 1000;
        return Integer.parseInt(text) * 1000;
    }

    private int parseMinutesAsMillis(EditText editText, int defaultMinutes) {
        String text = editText.getText().toString().trim();
        if (text.isEmpty())
            return defaultMinutes * 60000;
        return Integer.parseInt(text) * 60000;
    }

    private int parseMillis(EditText editText, int defaultMillis) {
        String text = editText.getText().toString().trim();
        if (text.isEmpty())
            return defaultMillis;
        return (int) (Float.parseFloat(text) * 1000);
    }
}