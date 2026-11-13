package com.f4xizzz.serverbuddy.modules.missions

import com.f4xizzz.serverbuddy.core.Durations
import com.f4xizzz.serverbuddy.core.ItemSpec
import com.f4xizzz.serverbuddy.core.SbGui
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.putButton
import com.f4xizzz.serverbuddy.core.putFillers
import com.f4xizzz.serverbuddy.core.putSafe
import com.f4xizzz.serverbuddy.core.rewards.RewardService
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import kotlin.math.max

/**
 * O menu de baú das missões: uma aba por categoria em cima e as missões embaixo, com o progresso de cada objetivo.
 * Clicar numa missão pronta resgata o prêmio. Tudo (posições, ícones, textos) vem de [MissionsMenu].
 */
class MissionsGui(player: ServerPlayer) : SimpleGui(MissionsService.config.menu.type.type, player, !MissionsService.config.menu.showPlayerInventory) {
    private var categoryId: String? = null
    private var page = 0
    private var ticks = 0

    init {
        val menu = MissionsService.config.menu
        if (!menu.showPlayerInventory) SbGui.hidePlayerInventory(this, menu.playerInventoryFiller)
        refresh()
    }

    /** O progresso anda enquanto o menu está aberto: atualiza de tempos em tempos. */
    override fun onTick() {
        super.onTick()
        if (++ticks % 40 == 0) refresh()
    }

    private fun priority(state: State): Int = when (state) {
        State.READY -> 0
        State.OFFER -> 0
        State.ACTIVE -> 1
        State.COOLDOWN -> 2
        State.LOCKED -> 3
        State.CLAIMED -> 4
    }

    fun refresh() {
        val menu = MissionsService.config.menu
        for (slot in 0 until virtualSize) clearSlot(slot)
        putFillers(menu.fillers)

        val categories = MissionsService.visibleCategories(player)
        if (categoryId == null || categories.none { it.id == categoryId }) categoryId = categories.firstOrNull()?.id
        val current = categories.firstOrNull { it.id == categoryId }

        // abas
        categories.forEachIndexed { index, category ->
            val slot = menu.categorySlots.getOrNull(index) ?: return@forEachIndexed
            val ready = MissionsService.views(player, category.id).count { it.state == State.READY }
            val lore = category.def.description.map { "<gray>$it" } + if (ready > 0) listOf("", "<green>$ready ready to claim") else emptyList()
            val resetIn = MissionsService.resetIn(category)
            val extra = if (resetIn != null) listOf("<dark_gray>${if (category.def.global) "Termina" else "Troca"} em ${Durations.format(resetIn)}") else emptyList()
            val spec = ItemSpec(item = category.def.icon, name = category.displayName, lore = lore + extra, hideAdditionalTooltip = true, glint = if (category.id == categoryId) true else null)
            val stack = spec.build()
            if (!stack.isEmpty) putSafe(slot, GuiElementBuilder.from(stack).setCallback { _ -> categoryId = category.id; page = 0; refresh() }.build())
        }

        // missões da categoria aberta
        val views = (current?.let { MissionsService.views(player, it.id) } ?: emptyList()).sortedBy { priority(it.state) }
        val perPage = max(1, menu.missionSlots.size)
        val pages = max(1, (views.size + perPage - 1) / perPage)
        page = page.coerceIn(0, pages - 1)
        if (views.isEmpty()) {
            val stack = menu.empty.build()
            if (!stack.isEmpty) putSafe(menu.emptySlot, GuiElementBuilder.from(stack).build())
        } else {
            views.drop(page * perPage).take(perPage).forEachIndexed { index, view ->
                val slot = menu.missionSlots.getOrNull(index) ?: return@forEachIndexed
                val stack = missionStack(view)
                if (!stack.isEmpty) putSafe(slot, GuiElementBuilder.from(stack).setCallback { _ -> onClick(view) }.build())
            }
        }

        val ready = MissionsService.readyCount(player.uuid)
        val readyHere = views.count { it.state == State.READY }
        val vars = mapOf("page" to page + 1, "pages" to pages, "category" to (current?.displayName ?: ""), "ready" to readyHere)
        putButton(menu.buttons.previous, page > 0, vars) { page--; refresh() }
        putButton(menu.buttons.next, page < pages - 1, vars) { page++; refresh() }
        putButton(menu.buttons.close, true, vars) { close() }
        putButton(menu.buttons.claimAll, readyHere > 0, vars) {
            if (MissionsService.claimAll(player, categoryId) == 0) Texts.send(player, MissionsService.config.messages.nothingToClaim)
            refresh()
        }
        setTitle(Texts.render(menu.title, vars + ("ready_total" to ready)))
    }

    private fun missionStack(view: MissionView): ItemStack {
        val menu = MissionsService.config.menu
        val states = menu.states
        val mission = view.mission
        val lines = ArrayList<String>()
        mission.def.description.forEach { lines.add("<gray>$it") }
        if (lines.isNotEmpty()) lines.add("")
        lines.add(states.objectivesHeader)
        for (objective in view.objectives) {
            lines.add(
                Texts.fill(
                    states.objectiveLine,
                    mapOf(
                        "done" to if (objective.done) "<green>✔" else "<yellow>▪",
                        "name" to ObjectiveLabels.label(objective.objective),
                        "current" to objective.current,
                        "amount" to objective.objective.amount,
                        "bar" to ProgressBar.render(objective.current, objective.objective.amount)
                    )
                )
            )
        }
        val rewards = mission.def.rewards.mapNotNull { RewardService.describeText(it) }
        if (rewards.isNotEmpty()) {
            lines.add("")
            lines.add(states.rewardsHeader)
            rewards.forEach { lines.add(" <gray>• <white>$it") }
        }
        val stateLines = when (view.state) {
            State.ACTIVE -> states.active
            State.READY -> states.ready
            State.CLAIMED -> states.claimed
            State.COOLDOWN -> states.cooldown
            State.LOCKED -> states.locked
            State.OFFER -> states.offer
        }
        val vars = mapOf("time" to Durations.format(view.cooldownLeftMs), "requirement" to Texts.escape(view.requirement))
        stateLines.forEach { lines.add(Texts.fill(it, vars)) }
        // missão global: quem vence, quanto falta e quem está na frente
        if (MissionsService.categories[mission.categoryId]?.def?.global == true) {
            val globalVars = GlobalMissions.menuVars(player, player.server)
            states.global.forEach { lines.add(Texts.fill(it, globalVars)) }
        }
        val icon = if (view.state == State.LOCKED) "minecraft:barrier" else mission.def.icon
        return ItemSpec(item = icon, name = mission.displayName, lore = lines, hideAdditionalTooltip = true, glint = if (view.state == State.READY) true else null).build()
    }

    private fun onClick(view: MissionView) {
        val messages = MissionsService.config.messages
        when (view.state) {
            State.READY -> {
                MissionsService.claim(player, view.mission.key)
                refresh()
            }
            State.OFFER -> {
                GlobalMissions.accept(player)
                refresh()
            }
            else -> Texts.send(player, messages.notReady)
        }
    }
}
