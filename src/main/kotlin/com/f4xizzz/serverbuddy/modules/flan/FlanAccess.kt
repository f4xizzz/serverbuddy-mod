package com.f4xizzz.serverbuddy.modules.flan

import com.google.gson.annotations.SerializedName

/** As quatro listas de uma claim: as duas regras de entrada e as duas coisas que o mod dá a quem entra. */
enum class RuleKind {
    /** Só entra quem tem uma destas permissões. */
    PERMISSIONS,

    /** Só entra quem tem uma destas tags. */
    TAGS,

    /** Quem entra ganha estas permissões (enquanto estiver dentro). */
    GRANT_PERMISSIONS,

    /** Quem entra ganha estas tags do scoreboard (enquanto estiver dentro). */
    GRANT_TAGS;

    /** É uma lista do que se DÁ ao entrar (e não de quem pode entrar)? */
    val isGrant: Boolean get() = this == GRANT_PERMISSIONS || this == GRANT_TAGS

    /** É uma lista de permissões (e não de tags)? */
    val isPermission: Boolean get() = this == PERMISSIONS || this == GRANT_PERMISSIONS
}

/**
 * As regras de uma claim. Entrada: listas de permissões e de tags (lista vazia = aquela regra não vale). Ao entrar: tags e permissões
 * que o mod dá a quem está dentro.
 */
class ClaimRestriction(
    val permissions: MutableList<String> = ArrayList(),
    val tags: MutableList<String> = ArrayList(),
    @SerializedName("grant_permissions")
    val grantPermissions: MutableList<String> = ArrayList(),
    @SerializedName("grant_tags")
    val grantTags: MutableList<String> = ArrayList()
) {
    /** Tem alguma regra de entrada? (só as de entrada decidem quem pode ficar na claim) */
    val hasEntryRules: Boolean get() = permissions.isNotEmpty() || tags.isNotEmpty()

    /** Dá alguma coisa a quem entra? */
    val hasGrants: Boolean get() = grantPermissions.isNotEmpty() || grantTags.isNotEmpty()

    val isEmpty: Boolean get() = !hasEntryRules && !hasGrants

    fun list(kind: RuleKind): MutableList<String> = when (kind) {
        RuleKind.PERMISSIONS -> permissions
        RuleKind.TAGS -> tags
        RuleKind.GRANT_PERMISSIONS -> grantPermissions
        RuleKind.GRANT_TAGS -> grantTags
    }
}

/** A decisão de entrada (lógica pura, para testar). */
object FlanAccess {
    /**
     * Pode entrar? Sem regras de entrada, sim. Com uma lista só, vale a lista. Com as duas, basta passar em uma. [hasPermission] e
     * [hasTag] dizem se o jogador tem cada permissão e cada tag das listas. As listas do que se dá ao entrar não influem aqui.
     */
    @JvmStatic
    fun canEnter(restriction: ClaimRestriction?, hasPermission: (String) -> Boolean, hasTag: (String) -> Boolean): Boolean {
        if (restriction == null || !restriction.hasEntryRules) return true
        val byPermission = restriction.permissions.isNotEmpty() && restriction.permissions.any(hasPermission)
        val byTag = restriction.tags.isNotEmpty() && restriction.tags.any(hasTag)
        return byPermission || byTag
    }

    /** O que a claim pede, em texto, para a mensagem de quem foi impedido ("permissão a ou b, tag c"). */
    @JvmStatic
    fun describe(restriction: ClaimRestriction): String {
        val parts = ArrayList<String>()
        if (restriction.permissions.isNotEmpty()) parts.add("permission " + restriction.permissions.joinToString(" or "))
        if (restriction.tags.isNotEmpty()) parts.add("tag " + restriction.tags.joinToString(" or "))
        return parts.joinToString(" / ")
    }

    /** Nome de permissão ou de tag que cabe numa regra: sem espaços, até 64 caracteres. */
    @JvmStatic
    fun cleanEntry(text: String): String? {
        val entry = text.trim().lowercase()
        return if (entry.isNotEmpty() && entry.length <= 64 && !entry.any { it.isWhitespace() }) entry else null
    }

    private val SCOREBOARD_TAG = Regex("[a-z0-9_.+-]+")

    /**
     * Como [cleanEntry], para [kind]. As tags que a claim DÁ são tags do scoreboard, e o /tag do Minecraft só aceita letras, números e
     * `_ . + -` (assim o admin sempre consegue ver e tirar a tag pelo comando): outros caracteres são recusados.
     */
    @JvmStatic
    fun clean(kind: RuleKind, text: String): String? {
        val entry = cleanEntry(text) ?: return null
        return if (kind == RuleKind.GRANT_TAGS && !SCOREBOARD_TAG.matches(entry)) null else entry
    }
}

/** O que dar e tirar de quem está numa claim (lógica pura, para testar). */
object FlanGrantLogic {
    /**
     * Cada tag que o mod dá leva uma marca `sb_claim_grant.<tag>` na própria lista de tags do jogador. Assim o mod só tira o que ele
     * mesmo deu (nunca uma tag que o jogador já tinha) e continua certo depois de um reinício ou de uma queda do servidor. A marca
     * só usa caracteres que o /tag do Minecraft aceita, então o admin também consegue vê-la e tirá-la por comando.
     */
    const val MARK = "sb_claim_grant."

    /** O plano para as tags: [add] = tags para dar (e marcar), [remove] = tags que demos e a claim já não dá (sai a tag e a marca). */
    class TagPlan(val add: List<String>, val remove: List<String>) {
        val isEmpty: Boolean get() = add.isEmpty() && remove.isEmpty()
    }

    /** [desired]: o que a claim onde o jogador está dá agora. [playerTags]: todas as tags do scoreboard dele. */
    @JvmStatic
    fun planTags(desired: Collection<String>, playerTags: Set<String>): TagPlan {
        val want = desired.map { it.trim() }.filter { it.isNotEmpty() }.toCollection(LinkedHashSet())
        val marked = playerTags.filter { it.startsWith(MARK) }.map { it.removePrefix(MARK) }
        return TagPlan(
            add = want.filter { it !in playerTags },
            remove = marked.filter { it !in want }
        )
    }

    /** O que as claims em que o jogador está (a dele e a claim-mãe) dão juntas: (tags, permissões). */
    @JvmStatic
    fun union(rules: List<ClaimRestriction>): Pair<Set<String>, Set<String>> {
        val tags = LinkedHashSet<String>()
        val permissions = LinkedHashSet<String>()
        for (rule in rules) {
            tags.addAll(rule.grantTags)
            permissions.addAll(rule.grantPermissions)
        }
        return tags to permissions
    }
}
