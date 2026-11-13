package com.f4xizzz.serverbuddy.crates.storage.database.sql.providers

import com.f4xizzz.serverbuddy.crates.CratesModule
import com.f4xizzz.serverbuddy.crates.config.CratesConfig
import com.zaxxer.hikari.HikariConfig
import java.io.File

class H2Provider(config: CratesConfig.Storage) : HikariCPProvider(config) {
    override fun getConnectionURL(): String = String.format(
        "jdbc:h2:%s;AUTO_SERVER=TRUE",
        File(CratesModule.INSTANCE.configDir, "storage.db").toPath().toAbsolutePath()
    )

    override fun getDriverClassName(): String = "org.h2.Driver"
    override fun getDriverName(): String = "h2"
    override fun configure(config: HikariConfig) {}
}
