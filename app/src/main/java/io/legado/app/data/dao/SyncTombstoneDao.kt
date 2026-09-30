package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.legado.app.data.entities.SyncTombstone

@Dao
interface SyncTombstoneDao {

    @get:Query("select * from sync_tombstones")
    val all: List<SyncTombstone>

    @Query("select * from sync_tombstones where type = :type")
    fun getByType(type: String): List<SyncTombstone>

    @Query("select * from sync_tombstones where type = :type and `key` = :key")
    fun get(type: String, key: String): SyncTombstone?

    @Query("select * from sync_tombstones where deletedAt > :since")
    fun getSince(since: Long): List<SyncTombstone>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg tombstone: SyncTombstone)

    /**
     * 记录一条删除。已存在同 (type, key) 墓碑时保留较新的 deletedAt,避免并发删除把时间改小。
     *
     * 不用 SQLite `ON CONFLICT ... DO UPDATE`:UPSERT 需 SQLite 3.24(Android 11 / API 30),
     * 而本项目 minSdk 26(API 26 的 SQLite 是 3.18),会运行时崩。故在事务内读-比-写。
     */
    @Transaction
    fun put(type: String, key: String, deletedAt: Long) {
        val old = get(type, key)
        if (old == null || deletedAt > old.deletedAt) {
            insert(SyncTombstone(type, key, deletedAt))
        }
    }

    @Query("delete from sync_tombstones where type = :type and `key` = :key")
    fun delete(type: String, key: String)

    /** 同步落地后清理已传播完毕的墓碑(两端 deletedAt 都已知晓时调用) */
    @Query("delete from sync_tombstones where deletedAt <= :before")
    fun deleteBefore(before: Long)

    @Query("delete from sync_tombstones")
    fun deleteAll()
}
