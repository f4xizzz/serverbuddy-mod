package com.f4xizzz.serverbuddy.kits.storage.database.sql.providers

import com.f4xizzz.serverbuddy.kits.KitsModule
import com.f4xizzz.serverbuddy.kits.config.MainConfig
import com.zaxxer.hikari.HikariConfig
import java.io.File

class SQLiteProvider(config: MainConfig.Storage) : HikariCPProvider(config) {
    override fun getConnectionURL(): String = String.format(
        "jdbc:sqlite:%s",
        File(KitsModule.INSTANCE.configDir, "storage.db").toPath().toAbsolutePath()
    )


    override fun getDriverClassName(): String = "org.sqlite.JDBC"
    override fun getDriverName(): String = "sqlite"
    override fun configure(config: HikariConfig) {}
}