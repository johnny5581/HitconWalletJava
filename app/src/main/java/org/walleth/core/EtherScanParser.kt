package org.walleth.core

import org.json.JSONArray
import org.kethereum.functions.getTokenTransferTo
import org.kethereum.functions.getTokenTransferValue
import org.kethereum.functions.isTokenTransfer
import org.kethereum.model.Address
import org.kethereum.model.ChainDefinition
import org.kethereum.model.createTransactionWithDefaults
import org.walleth.data.transactions.TransactionEntity
import org.walleth.data.transactions.TransactionSource
import org.walleth.data.transactions.TransactionState
import org.walleth.khex.hexToByteArray
import java.math.BigInteger

class ParseResult(val list: List<TransactionEntity>, val highestBlock: Long)

fun parseEtherScanTransactions(jsonArray: JSONArray, chain: ChainDefinition): ParseResult {
    var lastBlockNumber = 0L
    val list = (0 until jsonArray.length()).map {
        val transactionJson = jsonArray.getJSONObject(it)
        val value = BigInteger(transactionJson.getString("value"))
        val timeStamp = transactionJson.getString("timeStamp").toLong()
        val blockNumber = transactionJson.getString("blockNumber").toLong()
        lastBlockNumber = Math.max(blockNumber, lastBlockNumber)
        val trans = TransactionEntity(
                transactionJson.getString("hash"),
                createTransactionWithDefaults(
                        chain = chain,
                        value = value,
                        from = Address(transactionJson.getString("from")),
                        to = Address(transactionJson.getString("to")),
                        nonce = try {
                            BigInteger(transactionJson.getString("nonce"))
                        } catch (e: NumberFormatException) {
                            null
                        },
                        input = transactionJson.getString("input").hexToByteArray().toList(),
                        txHash = transactionJson.getString("hash"),
                        creationEpochSecond = timeStamp
                ),
                signatureData = null,
                transactionState = TransactionState(false, isPending = false, source = TransactionSource.ETHERSCAN)
        )
        if(trans.transaction.isTokenTransfer())
        {
            //set to and value with trans
            //trans.transaction.to = Address(transactionJson.getString("contractAddress"))
            //trans.transaction.value = BigInteger.valueOf(0)
            trans.ercTo = trans.transaction.getTokenTransferTo()
            trans.ercValue = trans.transaction.getTokenTransferValue().toString()
            trans.ercContract = Address(transactionJson.getString("contractAddress"))
        }
        trans

    }
    return ParseResult(list, lastBlockNumber)
}
