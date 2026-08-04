package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.CookieEntity

@Dao
interface CookieDao {

    @Query("SELECT * FROM cookies Where url = :url")
    fun get(url: String): CookieEntity?

    @Query("select * from cookies where url like '%|%'")
    fun getOkHttpCookies(): List<CookieEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg cookie: CookieEntity)

    @Update
    fun update(vararg cookie: CookieEntity)

    @Query("delete from cookies where url = :url")
    fun delete(url: String)

    @Query("delete from cookies where url like '%|%'")
    fun deleteOkHttp()
}