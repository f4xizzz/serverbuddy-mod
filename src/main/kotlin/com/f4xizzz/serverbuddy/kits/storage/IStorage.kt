package com.f4xizzz.serverbuddy.kits.storage

import com.f4xizzz.serverbuddy.kits.config.MainConfig
import com.f4xizzz.serverbuddy.kits.data.UserData
import com.f4xizzz.serverbuddy.kits.storage.database.MongoStorage
import com.f4xizzz.serverbuddy.kits.storage.database.sql.SQLStorage
import com.f4xizzz.serverbuddy.kits.storage.file.FileStorage
import java.util.*

interface IStorage {
    companion object {
        fun load(config: MainConfig.Storage): IStorage {
            return when (config.type) {
                StorageType.JSON -> FileStorage()
                StorageType.MONGO -> MongoStorage(config)
                StorageType.MYSQL, StorageType.SQLITE -> SQLStorage(config)
            }
        }
    }

    fun getUser(uuid: UUID): UserData
    fun saveUser(uuid: UUID, userData: UserData): Boolean
    fun close() {}
}
