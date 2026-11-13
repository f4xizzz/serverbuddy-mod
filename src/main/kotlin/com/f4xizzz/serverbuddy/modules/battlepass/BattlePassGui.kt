package com.f4xizzz.serverbuddy.modules.battlepass

import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.Economy
import com.f4xizzz.serverbuddy.core.SbGui
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.putButton
import com.f4xizzz.serverbuddy.core.putFillers
import com.f4xizzz.serverbuddy.core.putSafe
import com.f4xizzz.serverbuddy.core.rewards.Reward
import com.f4xizzz.serverbuddy.core.rewards.RewardService
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import kotlin.math.max
import kotlin.math.min

/**
 * O menu de baú do passe de batalha: uma página de níveis por vez, com o prêmio premium em cima, o marcador do nível no meio e o
 * prêmio grátis embaixo. Clicar num prêmio liberado resgata. A página inicial é a do nível atual do jogador.
 */
class BattlePassGui(player: ServerPlayer) : SimpleGui(BattlePassService.config.menu.type.type, player, !BattlePassService.config.menu.showPlayerInventory) {
    private var page = -1
    private var ticks = 0
    private var pendingBuy = 0L

    init {
        val menu = BattlePassService.config.menu
        if (!menu.showPlayerInventory) SbGui.hidePlayerInventory(this, menu.playerInventoryFiller)
        refresh()
    }

    override fun onTick() {
        super.onTick()
        if (++ticks % 40 == 0) refresh()
    }

    fun refresh() {
        val config = BattlePassService.config
        val menu = config.menu
        for (slot in 0 until virtualSize) clearSlot(slot)
        putFillers(menu.fillers)

        val pass = BattlePassService.passOf(player)
        if (pass == null) {
            Texts.send(player, config.messages.seasonInactive)
            setTitle(Texts.render(menu.title, mapOf("level" to 0, "xp" to 0, "needed" to 0, "season" to 0)))
            putButton(menu.buttons.close, true, emptyMap()) { close() }
            return
        }

        val perPage = max(1, minOf(menu.levelSlots.size, menu.freeSlots.size, menu.premiumSlots.size))
        val pages = max(1, (config.levels + perPage - 1) / perPage)
        if (page < 0) page = (pass.level - 1) / perPage
        page = page.coerceIn(0, pages - 1)

        val needed = BattlePassService.xpNeeded(pass)
        val season = BattlePassService.season?.season ?: 0
        val vars = mapOf(
            "page" to page + 1, "pages" to pages, "level" to pass.level, "max" to config.levels, "xp" to pass.xp, "needed" to needed,
            "season" to season, "time" to if (BattlePassService.isActive()) Durations.format(BattlePassService.timeLeftMs()) else "ended"
        )

        for (i in 0 until perPage) {
            val level = page * perPage + i + 1
            if (level > config.levels) break
            val marker = when {
                level < pass.level -> menu.levelReached
                level == pass.level -> menu.levelCurrent
                else -> menu.levelLocked
            }
            val markerStack = marker.copy().let { spec ->
                val stack = spec.build(vars + ("level" to level))
                if (!stack.isEmpty) stack.count = level.coerceIn(1, 99)
                stack
            }
            if (!markerStack.isEmpty) putSafe(menu.levelSlots[i], GuiElementBuilder.from(markerStack).build())
            putReward(menu.freeSlots[i], pass, level, premium = false)
            if (config.premium.enabled) putReward(menu.premiumSlots[i], pass, level, premium = true)
        }

        val claimable = BattlePassService.claimableCount(player)
        val allVars = vars + mapOf("claimable" to claimable, "premium_status" to premiumStatus())
        putButton(menu.buttons.previous, page > 0, allVars) { page--; refresh() }
        putButton(menu.buttons.next, page < pages - 1, allVars) { page++; refresh() }
        putButton(menu.buttons.close, true, allVars) { close() }
        putButton(menu.buttons.claimAll, claimable > 0, allVars) {
            if (BattlePassService.claimAll(player) == 0) Texts.send(player, config.messages.nothingToClaim)
            refresh()
        }
        putButton(menu.buttons.info, true, allVars) {}
        if (config.premium.enabled) putButton(menu.buttons.premium, true, allVars) { onPremiumClick() }
        setTitle(Texts.render(menu.title, allVars))
    }

    private fun premiumStatus(): String {
        val config = BattlePassService.config
        val states = config.menu.states
        return when {
            BattlePassService.isPremium(player) -> states.premiumActive
            config.premium.price > 0 -> Texts.fill(states.premiumBuyable, mapOf("price" to Economy.format(config.premium.price)))
            else -> states.premiumUnavailable
        }
    }

    private fun putReward(slot: Int, pass: PlayerPass, level: Int, premium: Boolean) {
        val rewards: List<Reward> = BattlePassService.tiers[level]?.let { if (premium) it.premium else it.free }.orEmpty()
        if (rewards.isEmpty()) return
        val menu = BattlePassService.config.menu
        val outcome = BattlePassService.state(player, pass, level, premium)
        val lines = ArrayList<String>()
        lines.add("<gray>Level <white>$level</white> · " + if (premium) "<gold>premium track" else "<green>free track")
        lines.add("")
        rewards.mapNotNull { RewardService.describeText(it) }.forEach { lines.add(" <gray>• <white>$it") }
        val vars = mapOf("level" to level)
        val stateLines = when (outcome) {
            ClaimOutcome.ALREADY -> menu.states.claimed
            ClaimOutcome.OK -> menu.states.claimable
            ClaimOutcome.NOT_REACHED -> menu.states.lockedLevel
            ClaimOutcome.PREMIUM_REQUIRED -> menu.states.lockedPremium
            else -> emptyList()
        }
        stateLines.forEach { lines.add(Texts.fill(it, vars)) }

        val stack: ItemStack = RewardService.icon(rewards)
        if (stack.isEmpty) return
        val name = if (premium) "<gold>Premium reward" else "<green>Free reward"
        stack.set(DataComponents.ITEM_NAME, Texts.parse(name))
        stack.set(DataComponents.LORE, ItemLore(lines.map { Texts.parse(it) }))
        stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, net.minecraft.util.Unit.INSTANCE)
        if (outcome == ClaimOutcome.OK) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        putSafe(slot, GuiElementBuilder.from(stack).setCallback { _ -> onRewardClick(level, premium, outcome) }.build())
    }

    private fun onRewardClick(level: Int, premium: Boolean, outcome: ClaimOutcome) {
        val messages = BattlePassService.config.messages
        val vars = mapOf("level" to level)
        when (outcome) {
            ClaimOutcome.OK -> BattlePassService.claim(player, level, premium)
            ClaimOutcome.ALREADY -> Texts.send(player, messages.alreadyClaimed, vars)
            ClaimOutcome.NOT_REACHED -> Texts.send(player, messages.notReached, vars)
            ClaimOutcome.PREMIUM_REQUIRED -> Texts.send(player, messages.premiumRequired, vars)
            else -> {}
        }
        refresh()
    }

    private fun onPremiumClick() {
        val config = BattlePassService.config
        val messages = config.messages
        val price = Economy.format(config.premium.price)
        if (BattlePassService.isPremium(player)) {
            Texts.send(player, messages.premiumAlready)
            return
        }
        // comprar pede dois cliques seguidos, para ninguém gastar dinheiro sem querer
        val now = System.currentTimeMillis()
        if (config.premium.price > 0 && now - pendingBuy > 10_000) {
            pendingBuy = now
            Texts.send(player, "<yellow>Click the premium pass again to confirm purchase for <white>$price</white>.")
            return
        }
        pendingBuy = 0
        when (BattlePassService.buyPremium(player)) {
            BuyOutcome.OK -> Texts.send(player, messages.premiumBought)
            BuyOutcome.ALREADY -> Texts.send(player, messages.premiumAlready)
            BuyOutcome.NOT_FOR_SALE -> Texts.send(player, messages.premiumNotForSale)
            BuyOutcome.NO_ECONOMY -> Texts.send(player, messages.noEconomy)
            BuyOutcome.NOT_ENOUGH -> Texts.send(player, messages.notEnoughMoney, mapOf("price" to price))
        }
        refresh()
    }
}
