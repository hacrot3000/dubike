package com.du.dtc.bike.ble;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import com.du.dtc.bike.log.BleDebugLogger;
import com.du.dtc.bike.activity.MainActivity;

public class DataParser {

    // Cờ toàn cục báo hiệu có lỗi JSON
    public static boolean isDataError = true;

    public static void parseBinary(String uuid, byte[] bytes, BikeData data) {
        if (bytes == null || bytes.length == 0)
            return;

        try {
            ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

            switch (uuid) {
                case "6d2eb205": {
                    DashboardTelemetry telemetry = DashboardTelemetry.fromBytes(bytes);
                    if (telemetry != null) {
                        data.odo = telemetry.odo;
                        data.speed = telemetry.speed;
                        data.current = telemetry.current;
                        data.voltage = telemetry.voltage;
                        data.soc = telemetry.batteryPercent;
                        data.kmLeft = telemetry.estimatedRange;

                        data.tempBms = telemetry.batteryTemp;
                        data.tempMotor = telemetry.motorTemp;
                        data.tempController = telemetry.controllerTemp;

                        data.turnLeft = telemetry.isLeftTurn;
                        data.turnRight = telemetry.isRightTurn;

                        triggerStageChange(data.pcbState, telemetry.state);
                        data.pcbState = telemetry.state;
                        data.pcbError = telemetry.errorCode;
                    }
                    break;
                }
                case "eec8fd7f": {
                    if (bytes.length >= 1) {
                        int lockFlag = buf.get(0) & 0xFF;
                        updateLockStatus(lockFlag, data);
                    }
                    break;
                }
                case "c75ebe03": {
                    if (bytes.length >= 2) {
                        short val = buf.getShort(0);
                        if (val != 0) {
                            BleDebugLogger.i("DataParser", "[c75ebe03] Thẻ NFC quẹt: " + val);
                        }
                    }
                    break;
                }

                default:
                    BleDebugLogger.d("DataParser MISSING", "[" + uuid + "] " + BleDebugLogger.bytesToHex(bytes));
                    isDataError = true;
            }

        } catch (Exception e) {
            BleDebugLogger.e("DataParser ERROR", "[" + uuid + "] " + BleDebugLogger.bytesToHex(bytes));
            isDataError = true;
        }
    }

    public static void parseJson(String jsonStr, BikeData data) {
        if (jsonStr == null || jsonStr.isEmpty())
            return;

        JSONObject json;
        try {
            int start = jsonStr.indexOf("{");
            int end = jsonStr.lastIndexOf("}");
            if (start == -1 || end == -1)
                return;

            String cleanJson = jsonStr.substring(start, end + 1);
            json = new JSONObject(cleanJson);
        } catch (Exception e) {
            return; // Nếu không phải JSON hợp lệ thì bỏ qua luôn
        }

        // ========================================================
        // KHỐI 1: Phân tích gói PIN & NHIỆT ĐỘ (BMS)
        // ========================================================
        try {
            if (json.has("voltage")) {
                data.voltage = json.optDouble("voltage", data.voltage);
                data.current = json.optDouble("current", data.current);
                data.isCharging = json.optBoolean("charge", data.isCharging);
                data.isDischarging = json.optBoolean("discharge", data.isDischarging);
                data.tempFet = json.optDouble("fet", data.tempFet);
                data.tempBalanceReg = json.optDouble("resistor", data.tempBalanceReg);
                data.bmsError = json.optInt("error", data.bmsError);

                if (json.has("soc")) {
                    // FIX: Thử đọc như một Object (Xe mới), nếu lỗi thì đọc như một Double (Xe cũ)
                    JSONObject socObj = json.optJSONObject("soc");
                    if (socObj != null) {
                        data.soc = socObj.optDouble("soc", 0.0) * 100;
                        data.cycles = socObj.optDouble("cycles", data.cycles);
                        data.currentAh = socObj.optDouble("Ah", data.currentAh);
                        data.absAh = socObj.optDouble("absAh", data.absAh);
                    } else {
                        // Khắc phục cho xe cũ
                        data.soc = json.optDouble("soc", 0.0) * 100;
                    }
                }

                if (json.has("NTCs")) {
                    JSONArray ntcs = json.getJSONArray("NTCs");
                    int len = ntcs.length();
                    if (len > 0) {
                        double sumTemp = 0;
                        for (int i = 0; i < len; i++)
                            sumTemp += ntcs.optDouble(i, 0.0);
                        data.tempBms = sumTemp / len;

                        if (len >= 4) {
                            data.tempPin1 = ntcs.optDouble(0, data.tempPin1);
                            data.tempPin2 = ntcs.optDouble(1, data.tempPin2);
                            data.tempPin3 = ntcs.optDouble(2, data.tempPin3);
                            data.tempPin4 = ntcs.optDouble(3, data.tempPin4);
                        }
                    }
                }
            }
        } catch (Exception e) {
            isDataError = true;
            BleDebugLogger.e("DataParser", "Lỗi Parse BMS: " + e.getMessage());
            BleDebugLogger.e("DataParser", "RAW JSON BMS: " + jsonStr);
        }

        // ========================================================
        // KHỐI 2: Phân tích gói CẤU HÌNH + VẬN HÀNH (PCB)
        // ========================================================
        try {
            if (json.has("bikeConfig")) {
                JSONObject cfg = json.getJSONObject("bikeConfig");
                data.name = cfg.optString("name", data.name);
                data.frame = cfg.optString("model", data.frame);
                data.vin = cfg.optString("frame", data.vin);
                data.idleOff = cfg.optBoolean("idleOff", data.idleOff);
            }

            if (json.has("firmware")) {
                JSONObject fw = json.getJSONObject("firmware");
                data.fw = fw.optString("version", data.fw);
                data.fwHash = fw.optString("hash", data.fwHash);
            }

            if (json.has("controller")) {
                JSONObject ctrl = json.getJSONObject("controller");
                data.adc1 = ctrl.optDouble("adc1", data.adc1);
                data.adc2 = ctrl.optDouble("adc2", data.adc2);
                data.tempMotor = ctrl.optDouble("motor", data.tempMotor);
            }

            if (json.has("mainPcb")) {
                JSONObject pcb = json.getJSONObject("mainPcb");
                data.odo = pcb.optDouble("odo", data.odo);
                data.speed = pcb.optDouble("speed", data.speed);
                data.kmLeft = pcb.optDouble("kmLeft", data.kmLeft);
                data.throttle = pcb.optDouble("throttle", data.throttle);
                data.pcbError = pcb.optInt("error", data.pcbError);
                data.turnLeft = pcb.optBoolean("left", data.turnLeft);
                data.turnRight = pcb.optBoolean("right", data.turnRight);
                data.headlight = pcb.optBoolean("headlight", data.headlight);

                int newState = pcb.optInt("state", data.pcbState);
                triggerStageChange(data.pcbState, newState);
                data.pcbState = newState;
            }
        } catch (Exception e) {
            isDataError = true;
            BleDebugLogger.e("DataParser", "Lỗi Parse Config/PCB: " + e.getMessage());
            BleDebugLogger.e("DataParser", "RAW JSON PCB: " + jsonStr);
        }

        // ========================================================
        // KHỐI 3: Phân tích mảng CELL PIN (CellVols)
        // ========================================================
        try {
            if (json.has("cellVols")) {
                JSONArray cells = json.getJSONArray("cellVols");
                data.cellVoltages.clear();

                double min = 5.0;
                double max = 0.0;

                for (int i = 0; i < cells.length(); i++) {
                    double v = cells.optDouble(i, 0.0);
                    data.cellVoltages.add(v);
                    if (v < min && v > 0)
                        min = v;
                    if (v > max)
                        max = v;
                }

                if (cells.length() > 0) {
                    data.vMin = min;
                    data.vMax = max;
                    data.cellDiff = max - min;
                }
            }
        } catch (Exception e) {
            isDataError = true;
            BleDebugLogger.e("DataParser", "Lỗi Parse CellVols: " + e.getMessage());
            BleDebugLogger.e("DataParser", "RAW JSON CELLS: " + jsonStr);
        }
    }

    /**
     * Giải mã gói dữ liệu Dashboard (41 bytes) từ xe.
     * Sử dụng class DashboardTelemetry làm trung gian giải mã.
     */
    public static void parseDashboard(byte[] bytes, BikeData data) {
        if (bytes == null || bytes.length == 0)
            return;

        try {
            // Sử dụng class DashboardTelemetry mà chúng ta đã tạo trước đó
            DashboardTelemetry telemetry = DashboardTelemetry.fromBytes(bytes);

            if (telemetry != null) {
                // Cập nhật dữ liệu từ telemetry sang object global BikeData
                data.odo = telemetry.odo;
                data.speed = telemetry.speed;
                data.current = telemetry.current;
                data.voltage = telemetry.voltage;
                data.soc = telemetry.batteryPercent; // Phần trăm pin
                data.kmLeft = telemetry.estimatedRange;

                // Cập nhật nhiệt độ
                data.tempBms = telemetry.batteryTemp;
                data.tempMotor = telemetry.motorTemp;
                data.tempController = telemetry.controllerTemp;

                // Cập nhật trạng thái rẽ (Xi nhan)
                data.turnLeft = telemetry.isLeftTurn;
                data.turnRight = telemetry.isRightTurn;

                // Nếu class BikeData của bạn chưa có biến isParking, bạn có thể tạo thêm
                // boolean isParking;
                // data.isParking = telemetry.isParking;

                // Cập nhật mã lỗi và trạng thái bo mạch
                data.pcbError = telemetry.errorCode;

                int newState = telemetry.state;
                triggerStageChange(data.pcbState, newState);
                data.pcbState = newState;
            }
        } catch (Exception e) {
            BleDebugLogger.e("DataParser", "Lỗi parseDashboard: " + e.getMessage());
        }
    }

    /**
     * Giải mã trạng thái Khóa thông minh (Smartkey Echo) từ xe.
     * Trả về các cờ báo hiệu xe đang khóa, mở, hoặc đang réo còi báo động.
     */
    public static void parseLockStatus(byte[] bytes, BikeData data) {
        if (bytes == null || bytes.length == 0)
            return;

        try {
            int lockFlag = bytes[0] & 0xFF;
            updateLockStatus(lockFlag, data);

        } catch (Exception e) {
            BleDebugLogger.e("DataParser", "Lỗi parseLockStatus: " + e.getMessage());
        }
    }

    private static void updateLockStatus(int lockFlag, BikeData data) {
        data.isAlarmSounding = false; // <--- Tắt báo động
        data.isArmed = false; // <--- Tắt báo động

        switch (lockFlag) {
            case 49: // Khóa
                data.isLocked = true;
                BleDebugLogger.d("DataParser", "🔒 Trạng thái: XE ĐÃ KHÓA");
                break;

            case 48: // Mở khóa
                data.isLocked = false;
                BleDebugLogger.d("DataParser", "🔓 Trạng thái: XE ĐÃ MỞ KHÓA");
                break;

            case 2: // Báo động
                BleDebugLogger.w("DataParser", "🚨 CẢNH BÁO: BÁO ĐỘNG ĐANG KÊU!");
                data.isAlarmSounding = true; // <--- Bật báo động
                break;

            case 7: // Đang bảo vệ
                data.isArmed = true;
                BleDebugLogger.d("DataParser", "🛡️ Trạng thái: ĐANG BẢO VỆ CHỐNG TRỘM");
                break;

            default:
                BleDebugLogger.d("DataParser", "Trạng thái khóa không xác định: " + lockFlag);
        }
    }

    private static void triggerStageChange(int oldState, int newState) {
        if (oldState == BikeData.PCB_STATE_OFF && newState != BikeData.PCB_STATE_OFF) {
            MainActivity.bikeBleLib.bikeControl.syncCurrentTime();
        }
    }
}