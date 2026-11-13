package com.f4xizzz.serverbuddy.shop.logging

import com.f4xizzz.serverbuddy.shop.ShopModule
import com.f4xizzz.serverbuddy.shop.data.ShopTransaction
import java.util.*

class ConsoleLogger: ILogger {
    override fun logTransaction(transaction: ShopTransaction): Boolean {
        ShopModule.LOGGER.info(transaction)
        return true
    }

    override fun getUserTransactions(uuid: UUID): List<ShopTransaction> {
        return emptyList()
    }

    override fun getAllTransactions(): List<ShopTransaction> {
        return emptyList()
    }

    override fun canListLogs(): Boolean {
        return false
    }
}