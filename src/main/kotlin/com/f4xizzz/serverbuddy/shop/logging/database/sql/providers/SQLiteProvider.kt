package com.f4xizzz.serverbuddy.shop.logging.database.sql.providers

import com.f4xizzz.serverbuddy.shop.ShopModule
import com.f4xizzz.serverbuddy.shop.config.LoggingOptions
import com.zaxxer.hikari.HikariConfig
import java.io.File

class SQLiteProvider(config: LoggingOptions) : HikariCPProvider(config) {
    override fun getConnectionURL(): String = String.format(
        "jdbc:sqlite:%s",
        File(ShopModule.INSTANCE.configDir, "logs.db").toPath().toAbsolutePath()
    )

    override fun getDriverClassName(): String = "org.sqlite.JDBC"
    override fun getDriverName(): String = "sqlite"
    override fun configure(config: HikariConfig) {}
}
