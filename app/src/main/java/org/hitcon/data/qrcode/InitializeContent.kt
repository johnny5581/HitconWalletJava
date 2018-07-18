package org.hitcon.data.qrcode

import org.hitcon.BadgeProvider.Companion.serviceNames
import org.hitcon.HitconBadgeServices
import java.util.*



data class InitializeContent(val address: String, val key: String, val service: String, val characteristics: String) {
    companion object {

    }
    constructor(data: Map<String, String>) : this(
            data.getValue("a"),
            data.getValue("k"),
            data.getValue("s"),
            data.getValue("c")
    )
    private val servicePost: String = service.substring(8)
    fun getUUID(name: HitconBadgeServices): UUID {
        return UUID.fromString(getStrUUID(name))
    }
    fun getStrUUID(name: HitconBadgeServices): String {
        var idx = serviceNames.indexOf(name)
        var header = characteristics.substring(idx * 8, idx * 8 + 8)
        return header + servicePost
    }

    fun getUuidName(uuid: UUID) : HitconBadgeServices? {
        return serviceNames.firstOrNull { getStrUUID(it) == uuid.toString() }
    }

}