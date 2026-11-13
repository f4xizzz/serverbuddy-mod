package com.f4xizzz.serverbuddy.shop

import com.f4xizzz.serverbuddy.shop.data.ShopInstance
import net.minecraft.server.level.ServerPlayer

object ShopAPI {
    fun openShop(player: ServerPlayer, shopId: String) {
        ShopManager.getShop(shopId)?.open(player)
    }

    fun getShop(id: String): ShopInstance? {
        return ShopManager.getShop(id)
    }

    fun getShopIDs(): List<String> {
        return ShopManager.getAllShops().map { it.id }
    }

    fun getAllShops(): List<ShopInstance> {
        return ShopManager.getAllShops().toList()
    }
}