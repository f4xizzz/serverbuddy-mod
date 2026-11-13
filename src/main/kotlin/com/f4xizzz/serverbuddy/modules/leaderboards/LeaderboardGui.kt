package com.f4xizzz.serverbuddy.modules.leaderboards

import com.f4xizzz.serverbuddy.core.SbGui
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.putButton
import com.f4xizzz.serverbuddy.core.putFillers
import com.f4xizzz.serverbuddy.core.putSafe
import com.mojang.authlib.GameProfile
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.component.ResolvableProfile

/** A chest menu with the top players of one board as heads, and a button per board to switch. */
class LeaderboardGui(player: ServerPlayer, private var boardId: String) :
    SimpleGui(LeaderboardService.config.menu.type.type, player, false) {
    init {
        refresh()
    }

    fun refresh() {
        val config = LeaderboardService.config
        val menu = config.menu
        for (slot in 0 until virtualSize) clearSlot(slot)
        putFillers(menu.fillers)
        val board = LeaderboardService.board(boardId)
        val title = board?.title ?: boardId
        val snapshot = LeaderboardService.snapshot(boardId)
        val boardVars = mapOf("board" to title)

        for ((index, slot) in menu.playerSlots.withIndex()) {
            if (index >= config.size) break
            val entry = snapshot?.entries?.getOrNull(index)
            val vars = mapOf("position" to index + 1, "board" to title, "player" to (entry?.name ?: ""), "value" to (entry?.let { board?.let { b -> LeaderboardService.formatValue(b, it.value) } } ?: ""))
            if (entry == null) {
                val stack = menu.empty.build(vars)
                if (!stack.isEmpty) putSafe(slot, GuiElementBuilder.from(stack).build())
                continue
            }
            val stack = menu.head.build(vars)
            if (stack.isEmpty) continue
            if (stack.`is`(net.minecraft.world.item.Items.PLAYER_HEAD)) stack.set(DataComponents.PROFILE, ResolvableProfile(GameProfile(entry.uuid, entry.name)))
            putSafe(slot, GuiElementBuilder.from(stack).build())
        }

        for ((index, id) in LeaderboardService.boards.keys.withIndex()) {
            val slot = menu.boardSlots.getOrNull(index) ?: break
            val boardTitle = LeaderboardService.boards.getValue(id).title
            val stack = menu.boardIcon.build(mapOf("board" to boardTitle))
            if (stack.isEmpty) continue
            if (id == boardId) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
            putSafe(slot, GuiElementBuilder.from(stack).setCallback { _ -> boardId = id; refresh() }.build())
        }
        putButton(menu.close, true, boardVars) { close() }
        setTitle(Texts.render(menu.title, boardVars))
    }
}
