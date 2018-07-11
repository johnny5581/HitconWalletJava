package org.hitcon.data.badge

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Query

fun BadgeDAO.getBadge(id: String): BadgeEntity? {
    var badge = byId(id)
    badge?.let { it.services = getServices(it.identify) }
    return badge
}

fun BadgeDAO.getLastBadge() : BadgeEntity? {
    var badge = byLastTime()
    badge?.let { it.services = getServices(it.identify!!) }
    return badge
}

fun BadgeDAO.upsertBadge(badge: BadgeEntity) {
    upsert(badge)
    badge.services?.run { upsert(this) }
}


@Dao
interface BadgeDAO {
    @Query("SELECT * FROM badge")
    fun all(): List<BadgeEntity>

    @Query("SELECT * FROM badge ORDER BY last_use_time DESC LIMIT 1")
    fun byLastTime(): BadgeEntity?

    @Query("SELECT * FROM badge WHERE identify = :id")
    fun byId(id: String): BadgeEntity?


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entry: BadgeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entries: List<BadgeServiceEntity>)


    @Query("SELECT * FROM badge_service WHERE identify = :id")
    fun getServices(id: String): List<BadgeServiceEntity>

}