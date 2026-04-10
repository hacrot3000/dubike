package com.du.dtc.bike.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface BikeLogDao {
    @Insert
    void insertLog(BikeLogEntity log);

    // Lấy 500 bản ghi gần nhất (ORDER DESC để Room trả về nhanh, Activity sẽ reverse)
    @Query("SELECT * FROM bike_logs ORDER BY timestamp DESC LIMIT 500")
    List<BikeLogEntity> getRecentLogs();

    // Lấy theo khoảng thời gian (ms)
    @Query("SELECT * FROM bike_logs WHERE timestamp >= :fromMs ORDER BY timestamp ASC")
    List<BikeLogEntity> getLogsSince(long fromMs);

    @Query("DELETE FROM bike_logs WHERE timestamp < :beforeMs")
    void deleteOlderThan(long beforeMs);
}
