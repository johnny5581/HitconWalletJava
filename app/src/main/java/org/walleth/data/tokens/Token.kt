package org.walleth.data.tokens

import android.arch.persistence.room.Entity
import org.kethereum.model.Address
import org.kethereum.model.ChainDefinition
import org.walleth.data.networks.NetworkDefinition

fun Token.isETH() = address.hex == "0x0"
fun Token.isHITCON() = address.hex == ""

fun getEthTokenForChain(networkDefinition: NetworkDefinition) = Token(
        symbol = "ETH",
        name = "Ether",
        decimals = 18,
        address = Address("0x0"),
        chain = networkDefinition.chain,
        showInList = true,
        starred = false,
        fromUser = false,
        order = 0
)

fun getHitconTokenForChain(networkDefinition: NetworkDefinition) = Token(
        symbol = "HITCON",
        name = "HITCON Coin",
        decimals = 18,
        address = Address("0x1234567890123456"),
        chain = networkDefinition.chain,
        showInList = true,
        starred = true,
        fromUser = true,
        order = 0
)


@Entity(tableName = "tokens", primaryKeys = arrayOf("address", "chain"))
data class Token(
        val name: String,
        val symbol: String,
        val address: Address,
        val decimals: Int,
        val chain: ChainDefinition,
        val showInList: Boolean,
        val starred: Boolean,
        val fromUser: Boolean,
        val order:Int
)