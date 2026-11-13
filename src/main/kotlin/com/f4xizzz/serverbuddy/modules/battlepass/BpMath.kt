package com.f4xizzz.serverbuddy.modules.battlepass

import kotlin.math.pow

/** Contas do passe (lógica pura): XP de cada nível, subir de nível e bônus. */
object BpMath {
    /** O resultado de ganhar XP: o nível e o XP dentro do nível depois, e quantos níveis subiu. */
    class Result(val level: Int, val xp: Long, val levelsGained: Int)

    /**
     * XP para sair do nível [level] para o seguinte. FORMULA: `xp_per_level × multiplier^(level-1)`. MANUAL: o valor escrito para
     * o nível seguinte (se faltar, o último definido até ali; se não há nenhum, o `xp_per_level`).
     */
    @JvmStatic
    fun xpForNext(level: Int, curve: BpCurve): Long {
        val value = when (curve.mode) {
            CurveMode.FORMULA -> curve.xpPerLevel * curve.multiplier.pow((level - 1).coerceAtLeast(0))
            CurveMode.MANUAL -> {
                val table = curve.manual.mapNotNull { (k, v) -> k.trim().toIntOrNull()?.let { it to v } }.toMap()
                val target = level + 1
                (table[target] ?: table.filterKeys { it < target }.maxByOrNull { it.key }?.value ?: curve.xpPerLevel).toDouble()
            }
        }
        return value.toLong().coerceAtLeast(1)
    }

    /** Soma [gained] de XP ao ponto onde o jogador está, subindo de nível quantas vezes couber (até [maxLevel]). */
    @JvmStatic
    fun add(level: Int, xp: Long, gained: Long, maxLevel: Int, curve: BpCurve): Result {
        var l = level.coerceIn(1, maxLevel)
        var x = xp + gained.coerceAtLeast(0)
        var up = 0
        while (l < maxLevel) {
            val need = xpForNext(l, curve)
            if (x < need) break
            x -= need
            l++
            up++
        }
        // no último nível o XP não acumula (a barra fica cheia)
        if (l >= maxLevel) x = 0
        return Result(l, x, up)
    }

    /** O maior multiplicador entre os bônus que valem agora ([matches] diz se um bônus vale para o jogador). 1.0 se nenhum vale. */
    @JvmStatic
    fun multiplier(boosts: List<BpBoost>, matches: (BpBoost) -> Boolean): Double =
        boosts.filter { it.multiplier > 0 && matches(it) }.maxOfOrNull { it.multiplier }?.coerceAtLeast(1.0) ?: 1.0
}
