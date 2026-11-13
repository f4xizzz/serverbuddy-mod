package com.f4xizzz.serverbuddy.modules.trivia

import com.f4xizzz.serverbuddy.core.SoundSpec
import com.f4xizzz.serverbuddy.core.config.Doc
import com.f4xizzz.serverbuddy.core.rewards.Reward
import com.f4xizzz.serverbuddy.core.rewards.RewardDocs
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/** `config/ServerBuddy/Trivia/config.json` */
@Doc(
    "Trivia · questions in chat\n" +
        "\n" +
        "HOW IT WORKS: from time to time the server asks a question in chat; whoever answers correctly first wins a reward.\n" +
        "The questions and rewards live in Trivia/pools/*.json (each file is a \"pool\"; see the examples there).\n" +
        "Answers are accepted regardless of capitals, accents and symbols, and with a small typo (see \"matching\").\n" +
        "\n" +
        "PLAYERS: /trivia (next question and your wins), /trivia top, /trivia stats [player] and /trivia toggle (mutes the\n" +
        "trivia just for you). STAFF: /trivia start [pool], /trivia stop, /trivia info, /trivia pools and /trivia reload.\n" +
        "VALUES in the texts: %question% %time% %hint% %answer% %winner% %time_taken% %streak% %reward% %pool%.\n" +
        "\n" +
        RewardDocs.TEXT
)
class TriviaConfig(
    @SerializedName("interval_time")
    @Doc("Time between one question and the next, in seconds (picks a value between min and max).")
    val interval: TriviaInterval = TriviaInterval(),
    @SerializedName("answer_time")
    @Doc("How long players have to answer, in seconds. Each pool can use another value.")
    val answerTime: Int = 60,
    @SerializedName("min_players")
    @Doc("Only asks questions if at least this many players are online.")
    val minPlayers: Int = 1,
    @SerializedName("avoid_repeating")
    @Doc("How many recent questions can't come back (0 = no limit).")
    val avoidRepeating: Int = 10,
    @SerializedName("hide_correct_answer")
    @Doc("true = the message of whoever answers right does not show in chat (so chat doesn't give the answer away before the win message).")
    val hideCorrectAnswer: Boolean = false,
    @Doc("How answers are compared.")
    val matching: TriviaMatching = TriviaMatching(),
    @Doc("Hints shown during the question.")
    val hints: TriviaHints = TriviaHints(),
    @Doc("A time bar at the top of the screen during the question (the question stays visible even with chat scrolling).")
    val bossbar: TriviaBossbar = TriviaBossbar(),
    @Doc("Sounds (null turns the sound off). Each one is \"name\" or { \"sound\": \"name\", \"volume\": 1, \"pitch\": 1 }.")
    val sounds: TriviaSounds = TriviaSounds(),
    @Doc("Texts (MiniMessage). Each list is one message with several lines.")
    val messages: TriviaMessages = TriviaMessages(),
    @SerializedName("fast_bonus")
    @Doc("Extra reward for whoever answers really fast.")
    val fastBonus: TriviaFastBonus = TriviaFastBonus(),
    @SerializedName("streak_bonus")
    @Doc("Extra reward for consecutive wins: the key is the number of consecutive wins (the streak breaks when someone else wins).")
    val streakBonus: Map<String, List<Reward>> = emptyMap()
) {
    companion object {
        /** A new file is born with the example extra rewards turned on (in an old file, without these keys, they stay off). */
        fun example() = TriviaConfig(
            fastBonus = TriviaFastBonus(
                enabled = true,
                withinPercent = 25,
                rewards = listOf(Reward.of("message", "text" to "<aqua>Lightning answer! Speed bonus."), Reward.of("xp", "amount" to 2, "levels" to true))
            ),
            streakBonus = linkedMapOf(
                "3" to listOf(Reward.of("message", "text" to "<gold>Three wins in a row! You are unstoppable."), Reward.of("item", "item" to "minecraft:golden_apple", "amount" to 2)),
                "5" to listOf(Reward.of("broadcast", "text" to "<gold>%player% won <white>%streak%</white> trivias in a row!"), Reward.of("item", "item" to "minecraft:diamond", "amount" to 3))
            )
        )
    }
}

class TriviaInterval(
    val min: Int = 600,
    val max: Int = 1200
)

class TriviaMatching(
    @SerializedName("typo_tolerance")
    @Doc("0 = the answer must be exact (not counting capitals, accents and symbols). 1 = accepts 1 wrong letter in answers of 6+ letters. 2 = up to 2 letters in answers of 12+. Numbers never have tolerance.")
    val typoTolerance: Int = 1
)

class TriviaHints(
    val enabled: Boolean = true,
    @SerializedName("at_percent")
    @Doc("When to show each hint, in % of the question's time (50 = halfway). Each hint reveals more letters.")
    val atPercent: List<Int> = listOf(50, 80)
)

class TriviaBossbar(
    val enabled: Boolean = true,
    @Doc("Color: PINK, BLUE, RED, GREEN, YELLOW, PURPLE or WHITE.")
    val color: String = "PURPLE",
    @Doc("Text of the bar. %question% = the question and %seconds% = seconds left.")
    val text: String = "<yellow>Trivia: <white>%question% <gray>(%seconds%s)"
)

class TriviaSounds(
    val start: SoundSpec? = SoundSpec("minecraft:block.note_block.pling", 1f, 1.4f),
    val hint: SoundSpec? = SoundSpec("minecraft:block.note_block.hat", 1f, 1.2f),
    val win: SoundSpec? = SoundSpec("minecraft:entity.player.levelup", 1f, 1f),
    val lose: SoundSpec? = SoundSpec("minecraft:block.note_block.bass", 1f, 0.8f),
    @SerializedName("timed_out")
    val timedOut: SoundSpec? = SoundSpec("minecraft:entity.villager.no", 1f, 1f)
)

class TriviaMessages(
    @Doc("When the question starts (for everyone).")
    val start: List<String> = listOf(
        "",
        "<gradient:#a855f7:#ec4899><bold>TRIVIA</bold></gradient> <gray>· <yellow>%question%",
        "<gray>Answer in chat! You have <white>%time%</white>.",
        ""
    ),
    @Doc("Hint during the question.")
    val hint: List<String> = listOf("<gradient:#a855f7:#ec4899><bold>TRIVIA</bold></gradient> <gray>· hint: <yellow>%hint%"),
    @Doc("For the winner.")
    val win: List<String> = listOf(
        "<green>[Trivia] You got it right in <white>%time_taken%</white>! <gray>Win streak: <white>%streak%",
        "<gray>Reward: <white>%reward%"
    ),
    @Doc("For everyone else, saying who won.")
    val lose: List<String> = listOf("<gray>[Trivia] <white>%winner%</white> got it right in <white>%time_taken%</white>! The answer was <green>%answer%</green>."),
    @SerializedName("timed_out")
    @Doc("When time runs out and nobody got it right.")
    val timedOut: List<String> = listOf("<gray>[Trivia] Nobody got it right in time. The answer was <green>%answer%</green>."),
    @Doc("/trivia toggle: when the player mutes the trivia.")
    val muted: String = "<gray>Trivia <red>muted</red> for you. Use <white>/trivia toggle</white> to turn it back on.",
    @SerializedName("unmuted")
    @Doc("/trivia toggle: when the player starts receiving the trivia again.")
    val unmuted: String = "<gray>Trivia <green>on</green> for you.",
    @SerializedName("next_in")
    @Doc("/trivia: when there is no question running. %time% = time left.")
    val nextIn: String = "<gray>The next trivia question comes in about <white>%time%</white>.",
    @SerializedName("running_now")
    @Doc("/trivia: when a question is running. %question% = the question and %time% = what is left.")
    val runningNow: String = "<yellow>Question running: <white>%question% <gray>(%time% left)"
)

class TriviaFastBonus(
    val enabled: Boolean = false,
    @SerializedName("within_percent")
    @Doc("Whoever answers right in the first X% of the time also gets these rewards.")
    val withinPercent: Int = 25,
    val rewards: List<Reward> = emptyList()
)

/** Um arquivo de `Trivia/pools/`. */
@Doc(
    "Trivia question pool: the file name is the pool's name.\n" +
        "\n" +
        "QUESTIONS (each round the server picks one of them):\n" +
        "  { \"question\": \"What is the biggest planet?\", \"answers\": [\"jupiter\"] }     a question with correct answers\n" +
        "     (optional: \"hint\": \"starts with J\" replaces the automatic hint; \"hint\": false turns the hint off)\n" +
        "  { \"generator\": \"equation\", \"difficulty\": \"easy\" }      a sum (easy, medium or hard)\n" +
        "  { \"generator\": \"typing\", \"length\": 6 }                   typing race\n" +
        "  { \"generator\": \"unscramble\", \"words\": [\"diamond\", \"creeper\"] }   a scrambled word (without \"words\" it uses a Minecraft list)\n" +
        "  With Cobblemon: dex_number_to_species, species_to_dex_number, description_to_species, description_to_move, species_to_type,\n" +
        "  species_to_ability, species_to_egg_group, move_to_type, unscramble_species, unscramble_move\n" +
        "  (they accept \"exclude\": [\"cobblemon:mewtwo\"] and \"text\": \"another wording with %values%\").\n" +
        "\n" +
        "REWARDS: \"rewards\" picks ONE reward by weight (\"weight\"); \"extra_rewards\" gives ALL of them (each with its own \"chance\").\n" +
        "Rewards are explained in config.json."
)
class TriviaPool(
    @Doc("false = the pool is not used.")
    val enabled: Boolean = true,
    @Doc("Nice name of the pool (shows in %pool%). Empty = the file name.")
    val name: String? = null,
    @Doc("Relative chance of this pool being the one picked (bigger = more often).")
    val weight: Int = 1,
    @SerializedName("answer_time")
    @Doc("Seconds to answer in this pool. null = the one from config.json.")
    val answerTime: Int? = null,
    @Doc("The questions (see the top of the file).")
    val questions: List<JsonObject> = emptyList(),
    @Doc("Rewards: ONE of them comes out, picked by \"weight\".")
    val rewards: List<Reward> = emptyList(),
    @SerializedName("extra_rewards")
    @Doc("Rewards that ALWAYS come out together with the picked one (each with its own \"chance\").")
    val extraRewards: List<Reward> = emptyList(),
    @Doc("Texts just for this pool (start, hint, win, lose, timed_out). null = the ones in config.json.")
    val messages: PoolMessages? = null,
    @Doc("Sounds just for this pool (start, hint, win, lose, timed_out). null = the ones in config.json.")
    val sounds: PoolSounds? = null
)

class PoolMessages(
    val start: List<String>? = null,
    val hint: List<String>? = null,
    val win: List<String>? = null,
    val lose: List<String>? = null,
    @SerializedName("timed_out")
    val timedOut: List<String>? = null
)

class PoolSounds(
    val start: SoundSpec? = null,
    val hint: SoundSpec? = null,
    val win: SoundSpec? = null,
    val lose: SoundSpec? = null,
    @SerializedName("timed_out")
    val timedOut: SoundSpec? = null
)

/** As pools que vêm prontas na primeira vez. */
object TriviaDefaults {
    private fun q(question: String, vararg answers: String): JsonObject {
        val json = JsonObject()
        json.addProperty("question", question)
        json.add("answers", com.google.gson.JsonArray().also { array -> answers.forEach { array.add(it) } })
        return json
    }

    private fun gen(id: String, vararg extra: Pair<String, Any>): JsonObject {
        val json = JsonObject()
        json.addProperty("generator", id)
        for ((key, value) in extra) if (value is Number) json.addProperty(key, value) else json.addProperty(key, value.toString())
        return json
    }

    private val ITEM_PRIZES = listOf(
        Reward.of("item", "item" to "minecraft:iron_ingot", "amount" to 8, "weight" to 5),
        Reward.of("item", "item" to "minecraft:golden_carrot", "amount" to 6, "weight" to 4),
        Reward.of("xp", "amount" to 5, "levels" to true, "weight" to 4),
        Reward.of("item", "item" to "minecraft:diamond", "amount" to 2, "weight" to 2)
    )

    fun general(): TriviaPool = TriviaPool(
        name = "Minecraft",
        weight = 3,
        questions = listOf(
            q("What is the rarest ore in the Overworld, used to make netherite tools?", "ancient debris", "debris"),
            q("Which mob explodes when it gets near the player?", "creeper"),
            q("How many eyes of ender are used to activate an End portal?", "12", "twelve"),
            q("What item do you need to tame a horse?", "nothing", "none", "hand", "saddle"),
            q("What is the block that can't be broken in survival and sits at the bottom of the world called?", "bedrock"),
            q("What is the name of the boss of The End dimension?", "ender dragon", "dragon", "the ender dragon"),
            q("Which block is needed to make a portal to the Nether?", "obsidian"),
            q("How many hearts of health does a player have at the start?", "10", "ten"),
            q("Which animal gives wool when sheared?", "sheep"),
            q("What material is made by smelting logs, and can craft a torch with a stick?", "charcoal", "coal"),
            gen("equation", "difficulty" to "easy"),
            gen("equation", "difficulty" to "medium"),
            gen("typing", "length" to 6),
            gen("unscramble")
        ),
        rewards = ITEM_PRIZES,
        extraRewards = emptyList()
    )

    fun pokemon(): TriviaPool = TriviaPool(
        name = "Pokémon",
        weight = 3,
        answerTime = 45,
        questions = listOf(
            gen("species_to_type"), gen("move_to_type"), gen("dex_number_to_species"), gen("species_to_dex_number"),
            gen("description_to_species"), gen("species_to_ability"), gen("unscramble_species"), gen("unscramble_move"),
            gen("species_to_egg_group"), gen("description_to_move")
        ),
        rewards = listOf(
            Reward.of("item", "item" to "cobblemon:poke_ball", "amount" to 10, "weight" to 5),
            Reward.of("item", "item" to "cobblemon:great_ball", "amount" to 5, "weight" to 3),
            Reward.of("item", "item" to "cobblemon:rare_candy", "amount" to 1, "weight" to 2)
        )
    )
}
