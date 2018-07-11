package org.hitcon.data.qrcode

import java.util.*

enum class HitconBadgeServices {
    Transaction,
    Txn,
    AddERC20,
    Balance,
    GeneralPurposeCmd,
    GeneralPurposeData
}

data class InitializeContent(val address: String, val key: String, val service: String, val characteristics: String) {
    companion object {
        val serviceNames = arrayOf(
                HitconBadgeServices.Transaction,
                HitconBadgeServices.Txn,
                HitconBadgeServices.AddERC20,
                HitconBadgeServices.Balance,
                HitconBadgeServices.GeneralPurposeCmd,
                HitconBadgeServices.GeneralPurposeData)
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