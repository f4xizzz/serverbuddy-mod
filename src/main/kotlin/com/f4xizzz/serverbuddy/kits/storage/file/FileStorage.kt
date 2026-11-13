package com.f4xizzz.serverbuddy.kits.storage.file

import com.f4xizzz.serverbuddy.kits.config.ConfigManager
import com.f4xizzz.serverbuddy.kits.data.UserData
import com.f4xizzz.serverbuddy.kits.storage.IStorage
import java.util.*

class FileStorage : IStorage {
    private var fileData: FileData = ConfigManager.loadFile(STORAGE_FILENAME, FileData(), true)

    companion object {
        private const val STORAGE_FILENAME = "storage.json"
    }

    override fun getUser(uuid: UUID): UserData {
        val userData = fileData.userdata[uuid]
        return userData ?: UserData()
    }

    override fun saveUser(uuid: UUID, userData: UserData): Boolean {
        fileData.userdata[uuid] = userData
        return ConfigManager.saveFile(STORAGE_FILENAME, fileData)
    }
}