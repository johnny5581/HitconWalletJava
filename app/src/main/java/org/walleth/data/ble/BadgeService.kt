package org.walleth.data.ble

import android.arch.persistence.room.Entity
import java.util.*

@Entity(tableName = "badge_service", primaryKeys = arrayOf("badge_address", "service_name"))
data class BadgeService(
        val badge_address: String,
        val service_name: String,
        var service_uuid: UUID?
)