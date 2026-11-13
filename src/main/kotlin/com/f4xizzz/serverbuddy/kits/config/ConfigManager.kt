package com.f4xizzz.serverbuddy.kits.config

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.f4xizzz.serverbuddy.kits.KitsModule
import com.f4xizzz.serverbuddy.kits.utils.Utils
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption


object ConfigManager {
    private var assetPackage = "assets/serverbuddy/kits"

    lateinit var CONFIG: MainConfig

    var KITS: BiMap<String, Kit> = HashBiMap.create()
    var PREVIEWS: BiMap<String, PreviewConfig> = HashBiMap.create()
    var MENUS: BiMap<String, KitMenuConfig> = HashBiMap.create()

    fun load() {
        copyDefaults()

        CONFIG = loadFile("config.json", MainConfig())

        loadKits()
        loadPreviews()
        loadMenus()
    }

    fun copyDefaults() {
        val classLoader = KitsModule::class.java.classLoader

        KitsModule.INSTANCE.configDir.mkdirs()

        attemptDefaultFileCopy(classLoader, "config.json")
        attemptDefaultDirectoryCopy(classLoader, "kits")
        attemptDefaultDirectoryCopy(classLoader, "previews")
        attemptDefaultDirectoryCopy(classLoader, "menus")
    }

    fun loadKits() {
        KITS.clear()

        val dir = KitsModule.INSTANCE.configDir.resolve("kits")
        if (dir.exists() && dir.isDirectory) {
            val files = dir.listFiles()
            if (files != null) {
                for (file in files) {
                    val fileName = file.name
                    if (file.isFile && fileName.contains(".json")) {
                        val id = fileName.substring(0, fileName.lastIndexOf(".json"))
                        val jsonReader = JsonReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8))
                        try {
                            val kit = KitsModule.INSTANCE.gsonPretty.fromJson(JsonParser.parseReader(jsonReader), Kit::class.java)
                            kit.id = id
                            KITS[id] = kit
                            Utils.printInfo("Successfully read and loaded the Kit file $fileName!")
                        } catch (ex: Exception) {
                            Utils.printError("Error while trying to parse the file $fileName as a Kit!")
                            ex.printStackTrace()
                        }
                    } else {
                        Utils.printError("File $fileName is either not a file or is not a .json file!")
                    }
                }
            }
        } else {
            Utils.printError("The 'kits' directory either does not exist or is not a directory!")
        }
    }

    fun loadPreviews() {
        PREVIEWS.clear()

        val dir = KitsModule.INSTANCE.configDir.resolve("previews")
        if (dir.exists() && dir.isDirectory) {
            val files = dir.listFiles()
            if (files != null) {
                for (file in files) {
                    val fileName = file.name
                    if (file.isFile && fileName.contains(".json")) {
                        val id = fileName.substring(0, fileName.lastIndexOf(".json"))
                        val jsonReader = JsonReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8))
                        try {
                            PREVIEWS[id] = KitsModule.INSTANCE.gsonPretty.fromJson(
                                JsonParser.parseReader(jsonReader),
                                PreviewConfig::class.java
                            )
                            Utils.printInfo("Successfully read and loaded the Preview Menu file $fileName!")
                        } catch (ex: Exception) {
                            Utils.printError("Error while trying to parse the file $fileName as a Preview Menu!")
                            ex.printStackTrace()
                        }
                    } else {
                        Utils.printError("File $fileName is either not a file or is not a .json file!")
                    }
                }
            }
        } else {
            Utils.printError("The 'previews' directory either does not exist or is not a directory!")
        }
    }

    fun loadMenus() {
        MENUS.clear()

        val dir = KitsModule.INSTANCE.configDir.resolve("menus")
        if (dir.exists() && dir.isDirectory) {
            val files = dir.listFiles()
            if (files != null) {
                for (file in files) {
                    val fileName = file.name
                    if (file.isFile && fileName.contains(".json")) {
                        val id = fileName.substring(0, fileName.lastIndexOf(".json"))
                        val jsonReader = JsonReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8))
                        try {
                            MENUS[id] = KitsModule.INSTANCE.gsonPretty.fromJson(
                                JsonParser.parseReader(jsonReader),
                                KitMenuConfig::class.java
                            )
                            Utils.printInfo("Successfully read and loaded the Kit Menu file $fileName!")
                        } catch (ex: Exception) {
                            Utils.printError("Error while trying to parse the file $fileName as a Kit Menu!")
                            ex.printStackTrace()
                        }
                    } else {
                        Utils.printError("File $fileName is either not a file or is not a .json file!")
                    }
                }
            }
        } else {
            Utils.printError("The 'menus' directory either does not exist or is not a directory!")
        }
    }

    fun <T : Any> loadFile(filename: String, default: T, create: Boolean = false): T {
        val file = File(KitsModule.INSTANCE.configDir, filename)
        var value: T = default
        try {
            Files.createDirectories(KitsModule.INSTANCE.configDir.toPath())
            if (file.exists()) {
                FileReader(file).use { reader ->
                    val jsonReader = JsonReader(reader)
                    value = KitsModule.INSTANCE.gsonPretty.fromJson(jsonReader, default::class.java)
                }
            } else if (create) {
                Files.createFile(file.toPath())
                FileWriter(file).use { fileWriter ->
                    fileWriter.write(KitsModule.INSTANCE.gsonPretty.toJson(default))
                    fileWriter.flush()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return value
    }

    fun <T> saveFile(filename: String, `object`: T): Boolean {
        val dir = KitsModule.INSTANCE.configDir
        val file = File(dir, filename)
        try {
            FileWriter(file).use { fileWriter ->
                fileWriter.write(KitsModule.INSTANCE.gsonPretty.toJson(`object`))
                fileWriter.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun attemptDefaultFileCopy(classLoader: ClassLoader, fileName: String) {
        val file = KitsModule.INSTANCE.configDir.resolve(fileName)
        if (!file.exists()) {
            try {
                val stream = classLoader.getResourceAsStream("${assetPackage}/$fileName")
                    ?: throw NullPointerException("File not found $fileName")

                Files.copy(stream, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (e: Exception) {
                Utils.printError("Failed to copy the default file '$fileName': $e")
            }
        }
    }

    private fun attemptDefaultDirectoryCopy(classLoader: ClassLoader, directoryName: String) {
        val directory = KitsModule.INSTANCE.configDir.resolve(directoryName)
        if (!directory.exists()) {
            directory.mkdirs()
            try {
                val sourceUrl = classLoader.getResource("${assetPackage}/$directoryName")
                    ?: throw NullPointerException("Directory not found $directoryName")
                val sourcePath = Paths.get(sourceUrl.toURI())

                Files.walk(sourcePath).use { stream ->
                    stream.filter { Files.isRegularFile(it) }
                        .forEach { sourceFile ->
                            val destinationFile = directory.resolve(sourcePath.relativize(sourceFile).toString())
                            Files.copy(sourceFile, destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        }
                }
            } catch (e: Exception) {
                Utils.printError("Failed to copy the default directory '$directoryName': " + e.message)
            }
        }
    }
}
