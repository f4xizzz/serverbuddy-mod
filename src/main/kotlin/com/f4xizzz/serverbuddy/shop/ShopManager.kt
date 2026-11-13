package com.f4xizzz.serverbuddy.shop

import com.f4xizzz.serverbuddy.shop.config.ConfigManager
import com.f4xizzz.serverbuddy.shop.data.ShopInstance

object ShopManager {
    private val instances: MutableMap<String, ShopInstance> = mutableMapOf()

    fun load() {
        instances.clear()

        ConfigManager.SHOPS.forEach { (id, config) ->
            instances[id] = ShopInstance.create(config)
        }
    }

    fun getShop(id: String): ShopInstance? {
        return instances[id]
    }

    fun getAllShops(): Collection<ShopInstance> {
        return instances.values
    }
}