package com.f4xizzz.serverbuddy.modules.flan

import com.f4xizzz.serverbuddy.core.ItemSpec
import com.f4xizzz.serverbuddy.core.SbGui
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.putSafe
import com.f4xizzz.serverbuddy.modules.tags.TagsService
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.AnvilInputGui
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Items

/**
 * Tela de edição de uma das quatro listas da claim (entrada por permissão, entrada por tag, dar permissão ao entrar, dar tag ao entrar):
 * mostra as regras de agora (clique remove), os botões de adicionar e limpar, e as opções prontas da config.
 */
class RestrictionGui(player: ServerPlayer, private val claim: ClaimRef, private val kind: RuleKind) :
    SimpleGui(FlanBridge.config.menu.type.type, player, !FlanBridge.config.menu.showPlayerInventory) {

    init {
        val menu = FlanBridge.config.menu
        if (!menu.showPlayerInventory) SbGui.hidePlayerInventory(this, menu.playerInventoryFiller)
        refresh()
    }

    private val config get() = FlanBridge.config

    private fun entries(): MutableList<String> = FlanStore.edit(claim.id).list(kind)

    private fun max(): Int = when (kind) {
        RuleKind.PERMISSIONS -> config.permissions.maxEntries
        RuleKind.TAGS -> config.tags.maxEntries
        RuleKind.GRANT_PERMISSIONS -> config.grants.maxPermissions
        RuleKind.GRANT_TAGS -> config.grants.maxTags
    }

    /** As opções prontas da config que aparecem como botões (só clicar). */
    private fun presets(): List<String> = when (kind) {
        RuleKind.PERMISSIONS -> config.permissions.presets
        RuleKind.TAGS -> emptyList()
        RuleKind.GRANT_PERMISSIONS -> config.grants.permissionPresets
        RuleKind.GRANT_TAGS -> config.grants.tagPresets
    }

    /** Dá para digitar qualquer valor? (nas tags de entrada se escolhe no catálogo do /tags, então não digita) */
    private fun canType(): Boolean = when (kind) {
        RuleKind.PERMISSIONS -> config.permissions.allowCustom
        RuleKind.TAGS -> true
        RuleKind.GRANT_PERMISSIONS -> config.grants.allowCustomPermissions
        RuleKind.GRANT_TAGS -> config.grants.allowCustomTags
    }

    private fun title(): String = when (kind) {
        RuleKind.PERMISSIONS -> config.messages.permissionsTitle
        RuleKind.TAGS -> config.messages.tagsTitle
        RuleKind.GRANT_PERMISSIONS -> config.messages.grantPermissionsTitle
        RuleKind.GRANT_TAGS -> config.messages.grantTagsTitle
    }

    fun refresh() {
        for (slot in 0 until virtualSize) clearSlot(slot)
        val menu = config.menu
        val list = entries()
        val last = virtualSize - 1
        val bottom = virtualSize - 9

        list.take(bottom).forEachIndexed { index, entry ->
            val stack = menu.entryIcon.build(mapOf("entry" to Texts.escape(entry)))
            if (!stack.isEmpty) putSafe(index, GuiElementBuilder.from(stack).setCallback { _ -> remove(entry) }.build())
        }
        // opções prontas da config que ainda não estão na lista
        val icon = if (kind.isPermission) "minecraft:book" else "minecraft:name_tag"
        val action = if (kind.isGrant) "grant" else "require"
        presets().filter { preset -> list.none { it.equals(preset, ignoreCase = true) } }.take(9).forEachIndexed { index, preset ->
            val slot = bottom - 9 + index
            if (slot < 0 || slot >= bottom) return@forEachIndexed
            val stack = ItemSpec(icon, name = "<yellow>%entry%", lore = listOf("<gray>Preset from the server list.", "<green>Click to $action")).build(mapOf("entry" to Texts.escape(preset)))
            putSafe(slot, GuiElementBuilder.from(stack).setCallback { _ -> add(preset) }.build())
        }
        val add = menu.addIcon.build()
        if (!add.isEmpty && canType()) putSafe(bottom + 2, GuiElementBuilder.from(add).setCallback { _ -> onAdd() }.build())
        val clear = menu.clearIcon.build()
        if (!clear.isEmpty) putSafe(bottom + 4, GuiElementBuilder.from(clear).setCallback { _ -> clearAll() }.build())
        val back = menu.backIcon.build()
        if (!back.isEmpty) putSafe(bottom, GuiElementBuilder.from(back).setCallback { _ -> goBack() }.build())
        val close = menu.closeIcon.build()
        if (!close.isEmpty) putSafe(last, GuiElementBuilder.from(close).setCallback { _ -> close() }.build())
        setTitle(Texts.render(title()))
    }

    private fun allowed(): Boolean {
        // quem escolhe o que a claim DÁ pode dar poder a si mesmo: só com a permissão da equipe
        if (kind.isGrant && !FlanBridge.canEditGrants(player)) {
            Texts.send(player, config.messages.grantsNotAllowed)
            return false
        }
        if (claim.canEdit(player)) return true
        Texts.send(player, config.messages.notAllowed)
        return false
    }

    private fun add(rawEntry: String) {
        if (!allowed()) return
        val entry = FlanAccess.clean(kind, rawEntry) ?: run {
            Texts.send(player, config.messages.invalid)
            return
        }
        val list = entries()
        if (list.any { it.equals(entry, ignoreCase = true) }) {
            Texts.send(player, config.messages.duplicate)
        } else if (list.size >= max()) {
            Texts.send(player, config.messages.limit, mapOf("max" to max()))
        } else {
            list.add(entry)
            FlanStore.save()
            Texts.send(player, config.messages.added, mapOf("entry" to Texts.escape(entry)))
        }
        refresh()
    }

    private fun remove(entry: String) {
        if (!allowed()) return
        if (entries().remove(entry)) {
            FlanStore.save()
            Texts.send(player, config.messages.removed, mapOf("entry" to Texts.escape(entry)))
        }
        refresh()
    }

    private fun clearAll() {
        if (!allowed()) return
        entries().clear()
        FlanStore.save()
        Texts.send(player, config.messages.cleared)
        refresh()
    }

    private fun onAdd() {
        if (!allowed()) return
        if (kind == RuleKind.TAGS) {
            TagPickerGui(player) { tag -> player.server.execute { RestrictionGui(player, claim, RuleKind.TAGS).also { it.add(tag) }.open() } }.open()
            return
        }
        if (!canType()) {
            Texts.send(player, config.messages.customBlocked)
            return
        }
        val title = if (kind == RuleKind.GRANT_TAGS) config.messages.typeTagTitle else config.messages.typePermissionTitle
        val initial = if (kind == RuleKind.PERMISSIONS) "serverbuddy." else ""
        TextInputGui(player, title, initial) { text ->
            player.server.execute { RestrictionGui(player, claim, kind).also { it.add(text) }.open() }
        }.open()
    }

    private fun goBack() {
        close()
        player.server.execute { FlanMenuBridge.reopenClaimMenu(player, claim) }
    }
}

/** Escolher uma tag do catálogo do /tags para exigir na claim. */
class TagPickerGui(player: ServerPlayer, private val onPick: (String) -> Unit) :
    SimpleGui(MenuType.GENERIC_9x6, player, false) {

    init {
        setTitle(Texts.render(FlanBridge.config.messages.pickTagTitle))
        val tags = try {
            TagsService.catalog().take(45)
        } catch (e: Throwable) {
            emptyList()
        }
        tags.forEachIndexed { index, view ->
            val spec = view.def.icon.copy(name = view.def.name.ifBlank { view.id }, lore = listOf("<gray>id: <white>${view.id}", "", "<green>Click to require this tag"))
            val stack = spec.build()
            if (!stack.isEmpty) setSlot(index, GuiElementBuilder.from(stack).setCallback { _ -> onPick(view.id) }.build())
        }
        FlanBridge.config.menu.closeIcon.build().let { if (!it.isEmpty) setSlot(53, GuiElementBuilder.from(it).setCallback { _ -> close() }.build()) }
    }
}

/** Digitar um texto (uma permissão ou o nome de uma tag) numa bigorna; clicar no resultado confirma. */
class TextInputGui(player: ServerPlayer, title: String, initial: String, private val onDone: (String) -> Unit) : AnvilInputGui(player, false) {
    init {
        setTitle(Component.literal(title))
        setDefaultInputValue(initial)
        setSlot(0, GuiElementBuilder(Items.PAPER).setName(Component.literal(initial)))
        setSlot(2, GuiElementBuilder(Items.LIME_DYE).setName(Component.literal("Confirm")).setCallback { _ ->
            val text = input
            close()
            onDone(text)
        })
    }
}
