package org.walleth.data.transactions

import android.arch.persistence.room.Embedded
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import org.kethereum.model.Address
import org.kethereum.model.SignatureData
import org.kethereum.model.Transaction

fun Transaction.toEntity(signatureData: SignatureData?, transactionState: TransactionState, hexData: String? = null) = TransactionEntity(txHash!!, this, signatureData, transactionState, hexData)

fun TransactionEntity.setHash(newHash: String) {
        hash = newHash
        transaction.txHash = newHash
}
@Entity(tableName = "transactions")
data class TransactionEntity(

        @PrimaryKey
        var hash: String,

        @Embedded
        var transaction: Transaction,

        @Embedded
        var signatureData: SignatureData?,

        @Embedded
        var transactionState: TransactionState,

        var hexData: String? = null,
        var ercTo: Address? = null,
        var ercValue: String? = null,
        var ercContract: Address? = null
)