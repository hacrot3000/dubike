package com.du.dtc.bike.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.du.dtc.bike.R;
import com.du.dtc.bike.db.BikeDatabase;
import com.du.dtc.bike.db.BikeLogEntity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryActivity extends AppCompatActivity {

    // ── Biểu đồ nhiệt độ ──
    private LineChart chartTemp;
    private boolean[] tempVisible = {true, true, true, true, true, true, true, true};
    private LineData tempData;

    // ── Biểu đồ điện & tốc độ ──
    private LineChart chartPower;
    private boolean[] powerVisible = {true, true, true, true};
    private LineData powerData;

    // ── Biểu đồ cell ──
    private LineChart chartCells;

    private ProgressBar progressBar;
    private TextView tvRecordCount;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Màu sắc cho các series
    private static final int[] TEMP_COLORS = {
            Color.parseColor("#FF6B6B"), // BalReg
            Color.parseColor("#FFA500"), // FET
            Color.parseColor("#4ECDC4"), // Pin1
            Color.parseColor("#95E1D3"), // Pin2
            Color.parseColor("#A8E6CF"), // Pin3
            Color.parseColor("#DCEDC1"), // Pin4
            Color.parseColor("#C678DD"), // Motor
            Color.parseColor("#E06C75"), // Controller
    };
    private static final String[] TEMP_LABELS = {
            "BalReg", "FET", "Pin1", "Pin2", "Pin3", "Pin4", "Motor", "Controller"
    };

    // Legend IDs for temp chart
    private int[] tempLegendIds;
    // Legend IDs for power chart
    private int[] powerLegendIds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // Back button
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        progressBar = findViewById(R.id.progress_bar);
        tvRecordCount = findViewById(R.id.tv_record_count);

        chartTemp = findViewById(R.id.chart_temp);
        chartPower = findViewById(R.id.chart_power);
        chartCells = findViewById(R.id.chart_cells);

        setupChartAppearance(chartTemp, "°C");
        setupChartAppearance(chartPower, "");
        setupChartAppearance(chartCells, "V");

        // Wire up temp legend toggles
        tempLegendIds = new int[]{
                R.id.legend_balreg, R.id.legend_fet, R.id.legend_pin1, R.id.legend_pin2,
                R.id.legend_pin3, R.id.legend_pin4, R.id.legend_motor, R.id.legend_ctrl
        };
        for (int i = 0; i < tempLegendIds.length; i++) {
            final int idx = i;
            LinearLayout legend = findViewById(tempLegendIds[i]);
            if (legend != null) {
                legend.setOnClickListener(v -> toggleTempSeries(idx));
            }
        }

        // Wire up power legend toggles
        powerLegendIds = new int[]{
                R.id.legend_voltage, R.id.legend_current, R.id.legend_speed, R.id.legend_soc
        };
        for (int i = 0; i < powerLegendIds.length; i++) {
            final int idx = i;
            LinearLayout legend = findViewById(powerLegendIds[i]);
            if (legend != null) {
                legend.setOnClickListener(v -> togglePowerSeries(idx));
            }
        }

        loadFromDatabase();
    }

    // ─────────────────────────────────────────────
    //  Cấu hình giao diện chung cho mọi LineChart
    // ─────────────────────────────────────────────
    private void setupChartAppearance(LineChart chart, String yUnit) {
        chart.setBackgroundColor(Color.parseColor("#1A1F2E"));
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        chart.setPinchZoom(true);
        chart.setDoubleTapToZoomEnabled(true);
        chart.setHighlightPerTapEnabled(true);
        chart.setNoDataText("Chưa có dữ liệu");
        chart.setNoDataTextColor(Color.parseColor("#888EA8"));

        // X-Axis: hiển thị dưới, là mốc thời gian
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.parseColor("#888EA8"));
        xAxis.setTextSize(9f);
        xAxis.setDrawGridLines(true);
        xAxis.setGridColor(Color.parseColor("#2A2F3E"));
        xAxis.setAxisLineColor(Color.parseColor("#3A3F4E"));
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            @Override
            public String getFormattedValue(float value) {
                return sdf.format(new Date((long) value));
            }
        });

        // Y-Axis trái
        YAxis left = chart.getAxisLeft();
        left.setTextColor(Color.parseColor("#888EA8"));
        left.setTextSize(9f);
        left.setDrawGridLines(true);
        left.setGridColor(Color.parseColor("#2A2F3E"));
        left.setAxisLineColor(Color.parseColor("#3A3F4E"));
        if (!yUnit.isEmpty()) {
            left.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return String.format(Locale.getDefault(), "%.1f%s", value, yUnit);
                }
            });
        }

        // Y-Axis phải: tắt
        chart.getAxisRight().setEnabled(false);
    }

    // ─────────────────────────────────────────────
    //  Load dữ liệu từ Room DB (background thread)
    // ─────────────────────────────────────────────
    private void loadFromDatabase() {
        executor.execute(() -> {
            List<BikeLogEntity> logs = BikeDatabase.getDatabase(this)
                    .bikeLogDao()
                    .getRecentLogs();

            // Đảo thứ tự: Room trả về DESC, ta cần ASC theo thời gian
            Collections.reverse(logs);

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                tvRecordCount.setText(logs.size() + " bản ghi");

                if (logs.isEmpty()) return;

                buildTempChart(logs);
                buildPowerChart(logs);
                buildCellChart(logs);
            });
        });
    }

    // ─────────────────────────────────────────────
    //  BIỂU ĐỒ 1: Nhiệt độ
    // ─────────────────────────────────────────────
    private void buildTempChart(List<BikeLogEntity> logs) {
        // 8 series: BalReg, FET, Pin1-4, Motor, Controller
        @SuppressWarnings("unchecked")
        List<Entry>[] series = new List[8];
        for (int i = 0; i < 8; i++) series[i] = new ArrayList<>();

        for (BikeLogEntity e : logs) {
            float x = e.timestamp;
            series[0].add(new Entry(x, (float) e.tempBalanceReg));
            series[1].add(new Entry(x, (float) e.tempFet));
            series[2].add(new Entry(x, (float) e.tempPin1));
            series[3].add(new Entry(x, (float) e.tempPin2));
            series[4].add(new Entry(x, (float) e.tempPin3));
            series[5].add(new Entry(x, (float) e.tempPin4));
            series[6].add(new Entry(x, (float) e.tempMotor));
            series[7].add(new Entry(x, (float) e.tempController));
        }

        List<LineDataSet> datasets = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            LineDataSet ds = makeLineDataSet(series[i], TEMP_LABELS[i], TEMP_COLORS[i]);
            datasets.add(ds);
        }

        tempData = new LineData(new ArrayList<>(datasets));
        chartTemp.setData(tempData);
        chartTemp.invalidate();
    }

    // ─────────────────────────────────────────────
    //  BIỂU ĐỒ 2: Điện áp / Dòng điện / Tốc độ / SOC
    // ─────────────────────────────────────────────
    private void buildPowerChart(List<BikeLogEntity> logs) {
        List<Entry> voltageEntries = new ArrayList<>();
        List<Entry> currentEntries = new ArrayList<>();
        List<Entry> speedEntries = new ArrayList<>();
        List<Entry> socEntries = new ArrayList<>();

        for (BikeLogEntity e : logs) {
            float x = e.timestamp;
            // Điện áp: 70-84V
            voltageEntries.add(new Entry(x, (float) e.voltage));
            // Dòng: -150 đến 50A
            currentEntries.add(new Entry(x, (float) e.current));
            // Tốc độ: 0-100 km/h
            speedEntries.add(new Entry(x, (float) e.speed));
            // SOC: 0-100 %
            socEntries.add(new Entry(x, (float) e.soc));
        }

        LineDataSet dsVoltage = makeLineDataSet(voltageEntries, "Điện áp (V)", Color.parseColor("#61AFEF"));
        LineDataSet dsCurrent = makeLineDataSet(currentEntries, "Dòng (A)", Color.parseColor("#E5C07B"));
        LineDataSet dsSpeed = makeLineDataSet(speedEntries, "Tốc độ (km/h)", Color.parseColor("#98C379"));
        LineDataSet dsSoc = makeLineDataSet(socEntries, "SOC (%)", Color.parseColor("#56B6C2"));

        powerData = new LineData(dsVoltage, dsCurrent, dsSpeed, dsSoc);
        chartPower.setData(powerData);
        chartPower.invalidate();
    }

    // ─────────────────────────────────────────────
    //  BIỂU ĐỒ 3: Cell Voltages — Vmin, Vmax, Diff
    // ─────────────────────────────────────────────
    private void buildCellChart(List<BikeLogEntity> logs) {
        List<Entry> maxEntries = new ArrayList<>();
        List<Entry> minEntries = new ArrayList<>();
        List<Entry> diffEntries = new ArrayList<>();

        for (BikeLogEntity e : logs) {
            if (e.cellVoltages == null || e.cellVoltages.isEmpty()) continue;
            float x = e.timestamp;

            double min = Double.MAX_VALUE, max = Double.MIN_VALUE;
            for (double v : e.cellVoltages) {
                if (v < min) min = v;
                if (v > max) max = v;
            }
            double diff = max - min;

            maxEntries.add(new Entry(x, (float) max));
            minEntries.add(new Entry(x, (float) min));
            diffEntries.add(new Entry(x, (float) diff));
        }

        if (maxEntries.isEmpty()) {
            chartCells.setNoDataText("Không có dữ liệu cell");
            chartCells.invalidate();
            return;
        }

        // Vmax — xanh lá
        LineDataSet dsMax = makeLineDataSet(maxEntries, "Vmax", Color.parseColor("#98C379"));
        dsMax.setLineWidth(1.8f);

        // Vmin — đỏ
        LineDataSet dsMin = makeLineDataSet(minEntries, "Vmin", Color.parseColor("#FF6B6B"));
        dsMin.setLineWidth(1.8f);

        // Diff — vàng, tô mờ dưới đường
        LineDataSet dsDiff = makeLineDataSet(diffEntries, "Lệch (Diff)", Color.parseColor("#E5C07B"));
        dsDiff.setLineWidth(2f);
        dsDiff.setFillColor(Color.parseColor("#40E5C07B"));
        dsDiff.setDrawFilled(true);
        dsDiff.setFillAlpha(60);

        LineData cellData = new LineData(dsMax, dsMin, dsDiff);
        chartCells.setData(cellData);
        chartCells.invalidate();
    }

    // ─────────────────────────────────────────────
    //  Helper: Tạo LineDataSet với style nhất quán
    // ─────────────────────────────────────────────
    private LineDataSet makeLineDataSet(List<Entry> entries, String label, int color) {
        LineDataSet ds = new LineDataSet(entries, label);
        ds.setColor(color);
        ds.setLineWidth(1.5f);
        ds.setDrawCircles(false);
        ds.setDrawValues(false);
        ds.setMode(LineDataSet.Mode.LINEAR);
        ds.setHighlightLineWidth(1f);
        ds.setHighLightColor(Color.parseColor("#80FFFFFF"));
        return ds;
    }

    // ─────────────────────────────────────────────
    //  Toggle series bật/tắt — Biểu đồ Nhiệt độ
    // ─────────────────────────────────────────────
    private void toggleTempSeries(int index) {
        if (tempData == null) return;
        tempVisible[index] = !tempVisible[index];
        LineDataSet ds = (LineDataSet) tempData.getDataSetByIndex(index);
        if (ds != null) {
            ds.setVisible(tempVisible[index]);
        }
        // Dim legend icon
        LinearLayout legend = findViewById(tempLegendIds[index]);
        if (legend != null) {
            legend.setAlpha(tempVisible[index] ? 1f : 0.35f);
        }
        chartTemp.invalidate();
    }

    // ─────────────────────────────────────────────
    //  Toggle series bật/tắt — Biểu đồ Điện & Tốc
    // ─────────────────────────────────────────────
    private void togglePowerSeries(int index) {
        if (powerData == null) return;
        powerVisible[index] = !powerVisible[index];
        LineDataSet ds = (LineDataSet) powerData.getDataSetByIndex(index);
        if (ds != null) {
            ds.setVisible(powerVisible[index]);
        }
        LinearLayout legend = findViewById(powerLegendIds[index]);
        if (legend != null) {
            legend.setAlpha(powerVisible[index] ? 1f : 0.35f);
        }
        chartPower.invalidate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
