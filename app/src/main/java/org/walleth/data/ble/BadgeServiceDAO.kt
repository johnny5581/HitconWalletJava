package org.walleth.data.ble

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Query

fun BadgeServiceDAO.upsertBadge(entry:Badge) {
    entry?.let {
        for(service in it.services) {
            service?.let { upsert(it) }
        }
    }
}


@Dao
interface BadgeServiceDAO {

    @Query("SELECT * FROM badge_service WHERE badge_address = :badgeAddress")
    fun getBadgeServices(badgeAddress: String)

    @Query("SELECT * FROM badge_service WHERE badge_address = :badgeAddress AND service_name = :serviceName")
    fun getBadgeService(badgeAddress: String, serviceName: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entry: BadgeService)

    @Query("DELETE FROM badge_service WHERE badge_address = :deviceAddress")
    fun delete(deviceAddress: String)
}