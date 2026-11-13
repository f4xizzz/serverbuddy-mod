package com.f4xizzz.serverbuddy.modules.calendar

import com.f4xizzz.serverbuddy.core.SbGui
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.putButton
import com.f4xizzz.serverbuddy.core.putFillers
import com.f4xizzz.serverbuddy.core.putSafe
import com.f4xizzz.serverbuddy.core.rewards.RewardService
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore

/** The chest menu of one calendar view (monthly, weekly or login streak). Clicking a ready day claims it. */
class CalendarGui(player: ServerPlayer, private val view: CalendarView) :
    SimpleGui(CalendarService.menu(view).type.type, player, !CalendarService.menu(view).showPlayerInventory) {
    private var ticks = 0

    init {
        val menu = CalendarService.menu(view)
        if (!menu.showPlayerInventory) SbGui.hidePlayerInventory(this, menu.playerInventoryFiller)
        refresh()
    }

    override fun onTick() {
        super.onTick()
        if (++ticks % 40 == 0) refresh()
    }

    private fun viewName(target: CalendarView) = when (target) {
        CalendarView.MONTHLY -> "Monthly calendar"
        CalendarView.WEEKLY -> "Weekly calendar"
        CalendarView.STREAK -> "Login streak"
    }

    fun refresh() {
        val menu = CalendarService.menu(view)
        for (slot in 0 until virtualSize) clearSlot(slot)
        putFillers(menu.fillers)

        val today = CalendarService.today()
        val streakRow = CalendarService.streakOf(player.uuid)
        val cells = CalendarService.cells(player.uuid, view)
        val claimable = cells.count { it.state == CellState.CLAIMABLE }
        val vars = mapOf(
            "title" to viewName(view), "month" to CalendarService.monthName(today), "year" to today.year, "week" to CalendarService.weekNumber(today),
            "date" to today.toString(), "claimable" to claimable, "streak" to streakRow.streak, "best" to streakRow.best
        )

        for ((index, cell) in cells.withIndex()) {
            val slot = menu.daySlots.getOrNull(index) ?: break
            val stack = iconFor(cell, vars)
            if (stack.isEmpty) continue
            putSafe(slot, GuiElementBuilder.from(stack).setCallback { _ -> onCellClick(cell) }.build())
        }

        putButton(menu.buttons.close, true, vars) { close() }
        putButton(menu.buttons.info, true, vars) {}
        putButton(menu.buttons.claimAll, claimable > 0, vars) {
            val count = CalendarService.claimAll(player, view)
            Texts.send(player, if (count > 0) CalendarService.config.messages.allClaimed else CalendarService.config.messages.nothingToClaim, mapOf("count" to count))
            refresh()
        }
        for ((target, button) in listOf(CalendarView.MONTHLY to menu.buttons.monthly, CalendarView.WEEKLY to menu.buttons.weekly, CalendarView.STREAK to menu.buttons.streak)) {
            if (target == view) continue
            putButton(button, CalendarService.isOn(target), vars) { CalendarGui(player, target).open() }
        }
        setTitle(Texts.render(menu.title, vars))
    }

    private fun onCellClick(cell: Cell) {
        val messages = CalendarService.config.messages
        when (CalendarService.claim(player, view, cell.number)) {
            ClaimResult.OK -> {}
            ClaimResult.ALREADY -> Texts.send(player, messages.alreadyClaimed)
            ClaimResult.NOT_AVAILABLE -> Texts.send(player, messages.notAvailable)
            ClaimResult.MISSED -> Texts.send(player, messages.missed)
            ClaimResult.EMPTY -> Texts.send(player, messages.noReward)
            ClaimResult.OFF -> Texts.send(player, messages.moduleOff)
        }
        refresh()
    }

    /** The icon of a cell: the day's own item, else the first reward's item, else the state icon; text from the state icon. */
    private fun iconFor(cell: Cell, base: Map<String, Any?>): ItemStack {
        val config = CalendarService.config
        val menu = CalendarService.menu(view)
        val icons = menu.icons
        val status = config.messages.status
        val (spec, statusText) = when (cell.state) {
            CellState.CLAIMED -> icons.claimed to status.claimed
            CellState.CLAIMABLE -> icons.claimable to status.claimable
            CellState.MISSED -> icons.missed to status.missed
            CellState.LOCKED -> icons.locked to status.locked
            CellState.EMPTY -> icons.empty to status.empty
        }
        val rewardsText = cell.rewards.mapNotNull { RewardService.describeText(it) }.joinToString("\n") { Texts.fill(config.messages.rewardLine, mapOf("reward" to it)) }
        val vars = base + mapOf(
            "day" to cell.number, "date" to cell.date?.toString().orEmpty(), "weekday" to (cell.weekday?.let { CalendarService.weekdayName(it) } ?: ""),
            "label" to (cell.day?.label ?: ""), "rewards" to rewardsText, "status" to statusText
        )
        val styled = spec.build(vars)
        var stack = styled
        val useReward = if (view == CalendarView.STREAK) CalendarService.streak.useRewardIcon else CalendarService.file(view)?.useRewardIcon ?: true
        val source: ItemStack? = cell.day?.icon?.build(vars)?.takeIf { !it.isEmpty }
            ?: if (useReward && cell.state != CellState.CLAIMED && cell.state != CellState.EMPTY) RewardService.icon(cell.rewards).takeIf { !it.isEmpty } else null
        if (source != null) {
            source.set(DataComponents.ITEM_NAME, styled.get(DataComponents.ITEM_NAME))
            source.set(DataComponents.LORE, styled.get(DataComponents.LORE))
            stack = source
        }
        val extra = cell.day?.lore.orEmpty()
        if (extra.isNotEmpty()) {
            val lines = ArrayList(stack.get(DataComponents.LORE)?.lines().orEmpty())
            for (line in extra) for (part in Texts.fill(line, vars).split("\n")) lines.add(Texts.parse(part))
            stack.set(DataComponents.LORE, ItemLore(lines))
        }
        stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, net.minecraft.util.Unit.INSTANCE)
        if (cell.state == CellState.CLAIMABLE) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        stack.count = cell.number.coerceIn(1, 99)
        return stack
    }
}
