package com.f4xizzz.serverbuddy.shop.logging

import com.f4xizzz.serverbuddy.shop.config.LoggingOptions
import com.f4xizzz.serverbuddy.shop.data.ShopTransaction
import com.f4xizzz.serverbuddy.shop.logging.database.MongoLogger
import com.f4xizzz.serverbuddy.shop.logging.database.sql.SQLLogger
import java.util.*

interface ILogger {
    companion object {
        fun load(config: LoggingOptions): ILogger {
            return when (config.type) {
                LoggerType.CONSOLE -> ConsoleLogger()
                LoggerType.MONGO -> MongoLogger(config)
                LoggerType.MYSQL, LoggerType.SQLITE -> SQLLogger(config)
            }
        }
    }

    fun logTransaction(transaction: ShopTransaction): Boolean
    fun getUserTransactions(uuid: UUID): List<ShopTransaction>
    fun getAllTransactions(): List<ShopTransaction>
    fun canListLogs(): Boolean

    fun close() {}
}
