package com.mapcontrol.trip;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Araçtaki yolculuk, rota noktası ve son açılma kayıtları. Telefon silinse de satırlar buradan yeniden verilir. */
public final class TripStore extends SQLiteOpenHelper {

    private static final String NAME = "trips.db";
    private static final int VERSION = 2;

    /**
     * Saklama süresi henüz belirlenmedi (doküman: açık soru). Geçici üst sınır: en yeni bu kadar
     * yolculuk tutulur, fazlası eskiden silinir.
     */
    static final int MAX_TRIPS = 2000;

    public TripStore(Context context) {
        super(context.getApplicationContext(), NAME, null, VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE trips ("
                + "id TEXT PRIMARY KEY,"
                + "started_at INTEGER NOT NULL,"
                + "ended_at INTEGER NOT NULL,"
                + "odo_start_km INTEGER, odo_end_km INTEGER, distance_km INTEGER NOT NULL,"
                + "fuel_start_percent INTEGER, fuel_end_percent INTEGER, fuel_used_ml INTEGER NOT NULL,"
                + "range_start_km INTEGER, range_end_km INTEGER,"
                + "avg_speed_kmh INTEGER NOT NULL, max_speed_kmh INTEGER NOT NULL, moving_ms INTEGER NOT NULL,"
                + "lat_start REAL, lon_start REAL, lat_end REAL, lon_end REAL)");
        db.execSQL("CREATE INDEX trips_by_start ON trips (started_at)");
        db.execSQL("CREATE TABLE route_points ("
                + "trip_id TEXT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,"
                + "seq INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, speed_kmh INTEGER NOT NULL,"
                + "PRIMARY KEY (trip_id, seq))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Son açılma kaldırıldı.
            db.execSQL("DROP TABLE IF EXISTS openings");
        }
    }

    /** Aynı {@code id} ikinci kez yazılmaz. */
    public void insertTrip(TripRecord t) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("id", t.id);
            v.put("started_at", t.startedAt);
            v.put("ended_at", t.endedAt);
            v.put("odo_start_km", t.odoStartKm);
            v.put("odo_end_km", t.odoEndKm);
            v.put("distance_km", t.distanceKm);
            v.put("fuel_start_percent", t.fuelStartPercent);
            v.put("fuel_end_percent", t.fuelEndPercent);
            v.put("fuel_used_ml", t.fuelUsedMl);
            v.put("range_start_km", t.rangeStartKm);
            v.put("range_end_km", t.rangeEndKm);
            v.put("avg_speed_kmh", t.avgSpeedKmh);
            v.put("max_speed_kmh", t.maxSpeedKmh);
            v.put("moving_ms", t.movingMs);
            putIfKnown(v, "lat_start", t.latStart);
            putIfKnown(v, "lon_start", t.lonStart);
            putIfKnown(v, "lat_end", t.latEnd);
            putIfKnown(v, "lon_end", t.lonEnd);
            if (db.insertWithOnConflict("trips", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1) {
                for (int i = 0; i < t.route.size(); i++) {
                    TripRecord.Point p = t.route.get(i);
                    ContentValues pv = new ContentValues();
                    pv.put("trip_id", t.id);
                    pv.put("seq", i);
                    pv.put("lat", p.lat);
                    pv.put("lon", p.lon);
                    pv.put("speed_kmh", p.speedKmh);
                    db.insert("route_points", null, pv);
                }
            }
            db.execSQL("DELETE FROM trips WHERE id IN "
                    + "(SELECT id FROM trips ORDER BY started_at DESC LIMIT -1 OFFSET " + MAX_TRIPS + ")");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static void putIfKnown(ContentValues v, String column, double value) {
        if (!Double.isNaN(value)) {
            v.put(column, value);
        }
    }

    /**
     * {@code since} sonrasında başlayan yolculuklar, eskiden yeniye, en çok {@code limit} tane.
     * Telefon son aldığının başlangıcını tekrar {@code since} verir.
     */
    public List<TripRecord> tripsSince(long since, int limit) {
        SQLiteDatabase db = getReadableDatabase();
        List<TripRecord> out = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT id, started_at, ended_at, odo_start_km, odo_end_km, distance_km,"
                + " fuel_start_percent, fuel_end_percent, fuel_used_ml, range_start_km, range_end_km,"
                + " avg_speed_kmh, max_speed_kmh, moving_ms, lat_start, lon_start, lat_end, lon_end"
                + " FROM trips WHERE started_at > ? ORDER BY started_at ASC LIMIT ?",
                new String[]{Long.toString(since), Integer.toString(limit)})) {
            while (c.moveToNext()) {
                String id = c.getString(0);
                out.add(new TripRecord(id, c.getLong(1), c.getLong(2), c.getInt(3), c.getInt(4), c.getInt(5),
                        c.getInt(6), c.getInt(7), c.getInt(8), c.getInt(9), c.getInt(10),
                        c.getInt(11), c.getInt(12), c.getLong(13),
                        real(c, 14), real(c, 15), real(c, 16), real(c, 17), route(db, id)));
            }
        }
        return out;
    }

    private static double real(Cursor c, int i) {
        return c.isNull(i) ? Double.NaN : c.getDouble(i);
    }

    private static List<TripRecord.Point> route(SQLiteDatabase db, String tripId) {
        List<TripRecord.Point> points = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT lat, lon, speed_kmh FROM route_points WHERE trip_id = ? ORDER BY seq",
                new String[]{tripId})) {
            while (c.moveToNext()) {
                points.add(new TripRecord.Point(c.getDouble(0), c.getDouble(1), c.getInt(2)));
            }
        }
        return points;
    }
}
