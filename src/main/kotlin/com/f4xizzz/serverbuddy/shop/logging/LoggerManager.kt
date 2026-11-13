package com.f4xizzz.serverbuddy.shop.logging

import com.f4xizzz.serverbuddy.shop.ShopModule
import com.f4xizzz.serverbuddy.shop.config.ConfigManager
import com.f4xizzz.serverbuddy.shop.data.ShopTransaction
import java.util.*
import java.util.concurrent.CompletableFuture

object LoggerManager {
    private lateinit var logger: ILogger
    private var enabled: Boolean = false

    fun load() {
        try {
            logger = ILogger.load(ConfigManager.CONFIG.logging)
            enabled = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun logTransaction(transaction: ShopTransaction) {
        if (enabled) {
            logger.logTransaction(transaction)
        }
    }

    fun getUserTransactions(uuid: UUID): CompletableFuture<List<ShopTransaction>> {
        return CompletableFuture.supplyAsync({
            if (canListLogs()) {
                logger.getUserTransactions(uuid)
            } else {
                emptyList()
            }
        }, ShopModule.INSTANCE.asyncExecutor)
    }

    fun getAllLogs(): CompletableFuture<List<ShopTransaction>> {
        return CompletableFuture.supplyAsync({
            if (canListLogs()) {
                logger.getAllTransactions()
            } else {
                emptyList()
            }
        }, ShopModule.INSTANCE.asyncExecutor)
    }

    fun canListLogs(): Boolean {
        return enabled && logger.canListLogs()
    }
}