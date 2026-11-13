package com.f4xizzz.serverbuddy.kits.storage.file

import com.f4xizzz.serverbuddy.kits.data.UserData
import java.util.*

class FileData {
    var userdata: HashMap<UUID, UserData> = HashMap()
    override fun toString(): String {
        return "FileData(userdata=$userdata)"
    }
}