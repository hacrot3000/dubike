package com.du.dtc.bike.db;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

// Tăng version lên 3
@Database(entities = { BikeLogEntity.class }, version = 3, exportSchema = false)
@TypeConverters({ Converters.class })
public abstract class BikeDatabase extends RoomDatabase {
    private static volatile BikeDatabase INSTANCE;

    public abstract BikeLogDao bikeLogDao();

    // 👉 VIẾT LỆNH MIGRATION ĐỂ GIỮ LẠI DỮ LIỆU CŨ
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE bike_logs ADD COLUMN adc1 REAL NOT NULL DEFAULT 0.0");
            database.execSQL("ALTER TABLE bike_logs ADD COLUMN adc2 REAL NOT NULL DEFAULT 0.0");
            database.execSQL("ALTER TABLE bike_logs ADD COLUMN throttle REAL NOT NULL DEFAULT 0.0");
        }
    };

    public static BikeDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (BikeDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            BikeDatabase.class, "bike_database")
                            .addMigrations(MIGRATION_2_3) // <--- Chèn lệnh Migration vào đây
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}