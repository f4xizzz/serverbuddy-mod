package com.f4xizzz.serverbuddy.modules.leaderboards

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokedex.CaughtCount
import com.cobblemon.mod.common.api.pokedex.CaughtPercent
import com.cobblemon.mod.common.api.pokedex.SeenCount
import net.minecraft.server.level.ServerPlayer

/** Pokédex numbers of an online player (Cobblemon). Only load this class when Cobblemon is installed. */
object CobblemonDexStats {
    class Dex(val caught: Int, val seen: Int, val percent: Double)

    fun read(player: ServerPlayer): Dex? = try {
        val pokedex = Cobblemon.playerDataManager.getPokedexData(player)
        val caught = CaughtCount.calculate(pokedex)
        val seen = SeenCount.calculate(pokedex)
        val raw = CaughtPercent.calculate(pokedex)
        // the calculator already answers in percent (0.195 = 0.195%) when it says so; otherwise it is a 0..1 fraction
        val percent = if (raw == null) 0.0 else if (CaughtPercent.outputIsPercentage) raw.toDouble() else raw * 100.0
        Dex(caught?.toInt() ?: 0, seen?.toInt() ?: 0, percent)
    } catch (e: Throwable) {
        null
    }
}
