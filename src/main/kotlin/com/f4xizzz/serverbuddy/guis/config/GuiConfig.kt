package com.f4xizzz.serverbuddy.guis.config

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.f4xizzz.serverbuddy.guis.config.actions.Action
import com.f4xizzz.serverbuddy.guis.config.requirements.RequirementOptions
import com.f4xizzz.serverbuddy.guis.gui.ChestGUI
import com.f4xizzz.serverbuddy.guis.utils.FlexibleListAdaptorFactory
import com.f4xizzz.serverbuddy.guis.utils.Scheduler
import com.f4xizzz.serverbuddy.core.SbPermissions as Permissions
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer

class GuiConfig(
    val title: String = "",
    val size: Int = 6,
    /**
     * `true` (padrão) mostra o inventário do jogador embaixo do menu. `false` esvazia essa parte (e bloqueia os
     * cliques nela): o Minecraft sempre desenha os 36 espaços, então o que dá para esconder são os itens.
     */
    @SerializedName("show_player_inventory")
    val showPlayerInventory: Boolean = true,
    /** Só vale com `show_player_inventory: false`: id de um item que preenche a parte de baixo (ex.: "minecraft:black_stained_glass_pane"). */
    @SerializedName("player_inventory_filler")
    val playerInventoryFiller: String? = null,
    @SerializedName("alias_permission")
    val aliasPermission: String? = null,
    @JsonAdapter(FlexibleListAdaptorFactory::class)
    @SerializedName("alias_commands")
    val aliasCommands: List<String> = listOf(),
    @SerializedName("open_requirements")
    val openRequirements: RequirementOptions? = null,
    @SerializedName("open_actions")
    val openActions: Map<String, Action> = emptyMap(),
    @SerializedName("close_actions")
    val closeActions: Map<String, Action> = emptyMap(),
    val items: Map<String, GuiItem> = emptyMap()
) {
    lateinit var id: String

    fun openGUI(player: ServerPlayer, id: String) {
        val gui = ChestGUI(player, id, this)
        if (openRequirements?.checkRequirements(player, gui) == false) {
            openRequirements.executeDenyActions(player, gui)
            return
        }
        openRequirements?.executeSuccessActions(player, gui)
        gui.open()
        Scheduler.scheduleTask(1, Scheduler.DelayedAction({ executeOpenActions(player, gui) }))
    }

    fun hasAliasPermission(ctx: CommandSourceStack): Boolean {
        if (aliasPermission == null) return Permissions.check(ctx, "serverbuddy.guis.open.$id", 2)
        return Permissions.check(ctx, aliasPermission, 2)
    }

    private fun executeOpenActions(player: ServerPlayer, gui: ChestGUI) {
        for (actionEntry in openActions) {
            actionEntry.value.attemptExecution(player, gui)
        }
    }

    fun executeCloseActions(player: ServerPlayer, gui: ChestGUI) {
        for (actionEntry in closeActions) {
            actionEntry.value.attemptExecution(player, gui)
        }
    }

    override fun toString(): String {
        return "GuiConfig(title='$title', size=$size, alias_commands=$aliasCommands, open_requirements=$openRequirements, open_actions=$openActions, close_actions=$closeActions, items=$items)"
    }
}
