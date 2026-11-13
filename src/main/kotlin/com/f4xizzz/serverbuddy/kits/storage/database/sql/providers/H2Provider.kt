package com.f4xizzz.serverbuddy.kits.storage.database.sql.providers

import com.f4xizzz.serverbuddy.kits.KitsModule
import com.f4xizzz.serverbuddy.kits.config.MainConfig
import com.zaxxer.hikari.HikariConfig
import java.io.File

class H2Provider(config: MainConfig.Storage) : HikariCPProvider(config) {

    override fun getConnectionURL(): String = String.format(
        "jdbc:h2:%s;AUTO_SERVER=TRUE",
        File(KitsModule.INSTANCE.configDir, "storage.db").toPath().toAbsolutePath()
    )

    override fun getDriverClassName(): String = "org.h2.Driver"
    override fun getDriverName(): String = "h2"
    override fun configure(config: HikariConfig) {}


}