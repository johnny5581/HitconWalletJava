package org.hitcon.data.badge

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.Ignore
import android.arch.persistence.room.PrimaryKey
import org.hitcon.BadgeProvider
import org.hitcon.HitconBadgeServices
import org.jetbrains.annotations.NotNull
import java.sql.Timestamp
import java.util.*

@Entity(tableName = "badge")
data class BadgeEntity(
        @PrimaryKey
        var identify: String = "",

        var address: String? = null,

        var name: String? = null,

        var key: String? = null,

        @ColumnInfo(name = "last_use_time")
        var lastusetime: Timestamp = Timestamp(System.currentTimeMillis()),

        @Ignore
        var services: List<BadgeServiceEntity>? = null
)
{
        fun getUuidName(uuid: UUID) : HitconBadgeServices? {
                var name= services?.firstOrNull { t -> t.uuid == uuid }?.name
                if(name != null)
                        return HitconBadgeServices.valueOf(name)
                return null
        }

}




