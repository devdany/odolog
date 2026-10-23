package com.hidany.odolog.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Upsert suspend fun upsert(trip: TripEntity)
    @Query("SELECT * FROM trips WHERE id = :id") suspend fun get(id: String): TripEntity?
    @Query("SELECT * FROM trips WHERE id = :id") fun observe(id: String): Flow<TripEntity?>
    @Query("SELECT * FROM trips WHERE endAt IS NULL ORDER BY startAt DESC LIMIT 1") suspend fun open(): TripEntity?
    @Query("SELECT * FROM trips ORDER BY startAt DESC LIMIT 1") suspend fun latest(): TripEntity?
    @Query("SELECT * FROM trips WHERE startAt >= :from AND startAt < :to ORDER BY startAt DESC")
    fun observeRange(from: Long, to: Long): Flow<List<TripEntity>>
    @Query("SELECT * FROM trips WHERE endAt IS NOT NULL AND purpose = 'UNCONFIRMED' ORDER BY startAt ASC")
    fun observePending(): Flow<List<TripEntity>>
    /** 차량 화면에 보여줄 미확정: 확인된 현재 운전자 본인 것 + 운전자 미상의 미확인 구간(넘기지 않은 것) */
    @Query("""SELECT * FROM trips WHERE endAt IS NOT NULL AND purpose = 'UNCONFIRMED' AND dismissedAt IS NULL
              AND (driverId = :driverId OR (source = 'GAP' AND driverId IS NULL)) ORDER BY startAt ASC""")
    fun observePendingFor(driverId: String): Flow<List<TripEntity>>
    @Query("SELECT * FROM trips WHERE driverId = :driverId ORDER BY startAt DESC LIMIT :n")
    fun observeRecentFor(driverId: String, n: Int): Flow<List<TripEntity>>
    @Query("SELECT * FROM trips WHERE driverId = :driverId AND startAt >= :from AND startAt < :to ORDER BY startAt DESC")
    fun observeRangeFor(driverId: String, from: Long, to: Long): Flow<List<TripEntity>>
    @Query("SELECT * FROM trips WHERE endAt IS NULL ORDER BY startAt DESC LIMIT 1")
    fun observeOpen(): Flow<TripEntity?>
    @Query("SELECT * FROM trips ORDER BY startAt DESC LIMIT :n") fun observeRecent(n: Int): Flow<List<TripEntity>>
    @Query("SELECT * FROM trips WHERE syncedAt IS NULL OR updatedAt > syncedAt") suspend fun unsynced(): List<TripEntity>
    @Query("UPDATE trips SET syncedAt = :at WHERE id IN (:ids) AND updatedAt <= :at") suspend fun markSynced(ids: List<String>, at: Long)
}

@Dao
interface DriverDao {
    @Query("SELECT * FROM drivers ORDER BY name") fun observeAll(): Flow<List<DriverEntity>>
    @Query("SELECT * FROM drivers ORDER BY name") suspend fun all(): List<DriverEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(d: DriverEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(d: List<DriverEntity>)
    @Query("DELETE FROM drivers WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM drivers WHERE fromServer = 1") suspend fun clearServer()
}

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places") suspend fun all(): List<PlaceEntity>
    @Query("SELECT * FROM places ORDER BY kind, label") fun observeAll(): Flow<List<PlaceEntity>>
    @Insert suspend fun insert(p: PlaceEntity): Long
    @Query("DELETE FROM places WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM places WHERE kind = :kind") suspend fun deleteKind(kind: PlaceKind)
}

@Dao
interface ChainDao {
    @Insert suspend fun insert(e: ChainEventEntity)
}

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE trips ADD COLUMN checkinId TEXT")
        db.execSQL("ALTER TABLE trips ADD COLUMN dismissedAt INTEGER")
        db.execSQL("ALTER TABLE drivers ADD COLUMN hasPin INTEGER NOT NULL DEFAULT 0")
    }
}

class Converters {
    @TypeConverter fun purposeToString(p: Purpose?) = p?.name
    @TypeConverter fun stringToPurpose(s: String?) = s?.let { Purpose.valueOf(it) }
    @TypeConverter fun sourceToString(s: TripSource) = s.name
    @TypeConverter fun stringToSource(s: String) = TripSource.valueOf(s)
    @TypeConverter fun kindToString(k: PlaceKind) = k.name
    @TypeConverter fun stringToKind(s: String) = PlaceKind.valueOf(s)
}

@Database(
    entities = [TripEntity::class, DriverEntity::class, PlaceEntity::class, ChainEventEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    // v2: PIN 본인 확인(checkinId), 미확인 구간 넘기기(dismissedAt), 운전자 PIN 여부(hasPin)
    abstract fun trips(): TripDao
    abstract fun drivers(): DriverDao
    abstract fun places(): PlaceDao
    abstract fun chain(): ChainDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "odolog.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
