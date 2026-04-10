package com.du.dtc.bike.ble;

import android.content.Context;
import android.content.SharedPreferences;

import com.du.dtc.bike.activity.MainActivity;

public class BikeBleFreq {

    public static boolean isOnlyShowDatBike = true;
    public static boolean isAppActive = true;
    public static boolean isAllowHistoryLog = true;

    // Các biến lưu trữ (Đơn vị: Mili-giây)
    public static int keepAliveInterval = 5000;
    public static int scanRadarActive = 1000;
    public static int scanRadarBg = 60000;

    public static int pollActive = 1000;
    public static int pollDrive = 5000;
    public static int pollOff = 3600000;
    public static int pollBg = 60000;

    public static int logDrive = 30000;
    public static int logPark = 300000;
    public static int logOff = 3600000;
    public static int logBg = 60000;

    // Gọi hàm này 1 lần lúc mở App (VD: trong MainActivity hoặc MyBikeApp)
    public static void init(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("BikeFreqPrefs", Context.MODE_PRIVATE);
        isAllowHistoryLog = prefs.getBoolean("isAllowHistoryLog", true);
        isOnlyShowDatBike = prefs.getBoolean("isOnlyShowDatBike", true);
        keepAliveInterval = prefs.getInt("keepAliveInterval", 5000);
        scanRadarActive = prefs.getInt("scanRadarActive", 1000);
        scanRadarBg = prefs.getInt("scanRadarBg", 60000);
        pollActive = prefs.getInt("pollActive", 1000);
        pollDrive = prefs.getInt("pollDrive", 5000);
        pollOff = prefs.getInt("pollOff", 3600000);
        pollBg = prefs.getInt("pollBg", 60000);
        logDrive = prefs.getInt("logDrive", 30000);
        logPark = prefs.getInt("logPark", 300000);
        logOff = prefs.getInt("logOff", 3600000);
        logBg = prefs.getInt("logBg", 60000);
    }

    public static int getKeepAliveInterval() {
        return keepAliveInterval;
    }

    public static int getScanRadarDelay() {
        return isAppActive ? scanRadarActive : scanRadarBg;
    }

    public static int getPollingInterval() {

        int defaultPool = pollBg;

        if (isAppActive)
            defaultPool = pollActive;

        else if (!isAllowHistoryLog)
            defaultPool = pollBg;

        else if (MainActivity.globalBikeData != null) {
            int state = MainActivity.globalBikeData.pcbState;
            if (state == BikeData.PCB_STATE_OFF)
                defaultPool = pollOff;
            else if (state == BikeData.PCB_STATE_MODE_D || state == BikeData.PCB_STATE_MODE_S)
                defaultPool = pollDrive;
        }

        return Math.min(defaultPool, getLogInterval());
    }

    public static int getLogInterval() {
        if (!isAllowHistoryLog)
            return Integer.MAX_VALUE;

        if (MainActivity.globalBikeData != null) {
            int state = MainActivity.globalBikeData.pcbState;
            if (state == BikeData.PCB_STATE_MODE_D || state == BikeData.PCB_STATE_MODE_S)
                return logDrive;
            if (state == BikeData.PCB_STATE_PARK)
                return logPark;
            if (state == BikeData.PCB_STATE_OFF)
                return logOff;
        }

        return Integer.MAX_VALUE;
    }
}