package com.du.dtc.bike.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.List;

@Entity(tableName = "bike_logs")
public class BikeLogEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public long timestamp;
    public int speed;
    public double odo;

    // Battery and Power
    public double voltage;
    public double current;
    public double soc;

    // Temperatures
    public double tempBalanceReg;
    public double tempFet;
    public double tempPin1;
    public double tempPin2;
    public double tempPin3;
    public double tempPin4;
    public double tempMotor;
    public double tempController;

    // Cells
    public List<Double> cellVoltages;

    // 👉 BỔ SUNG: Throttle & ADC (Mặc định là 0.0 với các bản ghi cũ)
    public double adc1;
    public double adc2;
    public double throttle;

    @Override
    public String toString() {
        return "{" +
                "ts=" + timestamp +
                ", sp=" + speed +
                ", odo=" + odo +
                ", vl=" + voltage +
                ", cr=" + current +
                ", soc=" + soc +
                ", tb=" + tempBalanceReg +
                ", tf=" + tempFet +
                ", tp1=" + tempPin1 +
                ", tp2=" + tempPin2 +
                ", tp3=" + tempPin3 +
                ", tp4=" + tempPin4 +
                ", tm=" + tempMotor +
                ", tc=" + tempController +
                ", cv=" + cellVoltages +
                ", a1=" + adc1 +
                ", a2=" + adc2 +
                ", thr=" + throttle +
                '}';
    }
}