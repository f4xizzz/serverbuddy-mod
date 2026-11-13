package com.f4xizzz.serverbuddy.crates.storage.database.sql.providers

import com.f4xizzz.serverbuddy.crates.CratesModule
import com.f4xizzz.serverbuddy.crates.config.CratesConfig
import com.zaxxer.hikari.HikariConfig
import java.io.File

class SQLiteProvider(config: CratesConfig.Storage) : HikariCPProvider(config) {
    override fun getConnectionURL(): String = String.format(
        "jdbc:sqlite:%s",
        File(CratesModule.INSTANCE.configDir, "storage.db").toPath().toAbsolutePath()
    )

    override fun getDriverClassName(): String = "org.sqlite.JDBC"
    override fun getDriverName(): String = "sqlite"
    override fun configure(config: HikariConfig) {
        config.maximumPoolSize = 1
        config.minimumIdle = 1
    }
}
