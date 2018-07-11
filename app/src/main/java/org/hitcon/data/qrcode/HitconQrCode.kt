package org.hitcon.data.qrcode

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable

private val queryMapping = hashMapOf(
        Pair(HitconQrCodeType.Initialize, arrayOf("v", "a", "k", "s", "c"))
)
private val pathMapping = hashMapOf(
        Pair("pair", HitconQrCodeType.Initialize)
)


fun Uri.toHitconQrCode(): HitconQrCode {
    var queryPairs = LinkedHashMap<String, String>()
    for (pair in this.query.toString().split("&")) {
        var idx = pair.indexOf("=")
        var key = pair.substring(0, idx)
        var value = pair.substring(idx + 1)
        queryPairs.put(key, value)
    }
    return HitconQrCode(this.host, queryPairs)
}
fun String.toHitconQrCode(): HitconQrCode {
    return Uri.parse(this).toHitconQrCode()
}


fun String.isHitconQrCodeUri() : Boolean {
    return Uri.parse(this).scheme == "hitcon"
}



enum class HitconQrCodeType {
    Invalid,
    Initialize,
    RecoveryCode,
}


class HitconQrCode(val path: String, val data: Map<String, String> = HashMap()) : Parcelable {
    val type: HitconQrCodeType = if (pathMapping.containsKey(path)) pathMapping[path]!! else HitconQrCodeType.Invalid
    val valid: Boolean = if (type == HitconQrCodeType.Invalid) false else checkDataValid(queryMapping[type]!!)

    constructor(parcel: Parcel) : this(
            parcel.readString(),
            linkedMapOf<String, String>().apply {
                var size = parcel.readInt()
                for(i in 0..size)
                {
                    var k = parcel.readString()
                    var v = parcel.readString()
                    this.put(k, v)
                }
            }
            )


    private fun checkDataValid(mapping: Array<String>) = mapping.all { data.containsKey(it) }
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(path)
        parcel.writeInt(data.size)
        for (pair in data)
        {
            parcel.writeString(pair.key)
            parcel.writeString(pair.value)
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<HitconQrCode> {
        override fun createFromParcel(parcel: Parcel): HitconQrCode {
            return HitconQrCode(parcel)
        }

        override fun newArray(size: Int): Array<HitconQrCode?> {
            return arrayOfNulls(size)
        }
    }


}