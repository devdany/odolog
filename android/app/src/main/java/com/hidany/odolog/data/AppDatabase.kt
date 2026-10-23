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
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trips(): TripDao
    abstract fun drivers(): DriverDao
    abstract fun places(): PlaceDao
    abstract fun chain(): ChainDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "odolog.db")
                .build().also { instance = it }
        }
    }
}
