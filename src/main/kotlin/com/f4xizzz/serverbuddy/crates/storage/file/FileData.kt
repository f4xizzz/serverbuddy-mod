package com.f4xizzz.serverbuddy.crates.storage.file

import com.google.gson.annotations.SerializedName
import com.f4xizzz.serverbuddy.crates.data.userdata.UsedKeyData
import com.f4xizzz.serverbuddy.crates.data.userdata.UserData
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class FileData {
    var userdata: MutableMap<UUID, UserData> = ConcurrentHashMap()
    @SerializedName("used_keys")
    var usedKeys: MutableMap<UUID, UsedKeyData> = ConcurrentHashMap()

    override fun toString(): String {
        return "FileData(userdata=$userdata, usedKeys=$usedKeys)"
    }
}
