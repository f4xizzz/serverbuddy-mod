package com.f4xizzz.serverbuddy.crates.storage.database.sql.providers

import com.f4xizzz.serverbuddy.crates.config.CratesConfig
import com.zaxxer.hikari.HikariConfig

class MySQLProvider(val config: CratesConfig.Storage) : HikariCPProvider(config) {
    override fun getConnectionURL(): String = String.format(
        "jdbc:mysql://%s:%d/%s",
        config.host,
        config.port,
        config.database
    )

    override fun getDriverClassName(): String = "com.mysql.cj.jdbc.Driver"
    override fun getDriverName(): String = "mysql"
    override fun configure(config: HikariConfig) {}
}
