package org.hitcon.data.badge

import android.arch.persistence.room.Entity
import java.util.*


@Entity(tableName = "badge_service", primaryKeys = ["identify", "name"])
data class BadgeServiceEntity(
        var identify: String,

        var name: String,

        var uuid: UUID? = null
)