package com.f4xizzz.serverbuddy.redeem;

import com.f4xizzz.serverbuddy.core.ItemSpec;
import com.f4xizzz.serverbuddy.core.config.Doc;
import com.f4xizzz.serverbuddy.storage.CommandRewardSpec;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * One redeem code (config/ServerBuddy/Redeem/redeem.conf, inside "codes"). The old Portuguese keys (max_usos, permissao, comandos, itens,
 * exibicao, entradas) are still read (the "alternate" names); new files are written with the English keys.
 */
public class RedeemCode {
   /** How many players can redeem it in total. 0 = no limit. */
   @Doc("How many players can redeem the code in total. 0 = no limit.")
   @SerializedName(value = "max_uses", alternate = {"max_usos"})
   public int maxUses = 0;

   /**
    * Permission node the player needs (LuckPerms, e.g. "vip.codes" or "group.vip"). Empty = everybody. Without a permissions mod only
    * operators (level 2) pass.
    */
   @Doc("Permission node the player needs (LuckPerms: e.g. vip.codes or group.vip). Empty = everybody. Without a permissions mod only operators pass.")
   @SerializedName(value = "permission", alternate = {"permissao"})
   public String permission = null;

   /**
    * Optional. After this moment the code can no longer be redeemed. Accepts "2026-12-31" (until the end of that day), "2026-12-31 18:30",
    * "2026-12-31T18:30:00" (server time) or "2026-12-31T18:30:00Z" / "...-03:00" (with the zone). Empty = never expires. A value that
    * cannot be read blocks the code (and is reported in the console) instead of letting it live forever by mistake.
    */
   @Doc("Optional. After this moment the code can no longer be redeemed: 2026-12-31 (until the end of that day), 2026-12-31 18:30, 2026-12-31T18:30:00 (server time) or with a zone (Z, -03:00). Empty = never. A date the mod cannot read blocks the code.")
   @SerializedName("expires_at")
   public String expiresAt = null;

   /** Commands run when the code is redeemed (as the console; %player% is the player). With /storage on, they become ONE reward there. */
   @Doc("Commands run as the console when the code is redeemed (%player% is the player). With /storage on they become ONE reward there.")
   @SerializedName(value = "commands", alternate = {"comandos"})
   public List<String> commands = new ArrayList<>();

   /** Items of the code (same format as the menu items: item, amount, name, lore, custom_model_data...). Each one becomes a reward in /storage. */
   @Doc("Items of the code (item, amount, name, lore, custom_model_data...). Each one becomes a reward in /storage.")
   @SerializedName(value = "items", alternate = {"itens"})
   public List<ItemSpec> items = new ArrayList<>();

   /** How the rewards of "commands" look in /storage (title, icon, lore). */
   @Doc("How the rewards of commands look in /storage (title, icon, lore).")
   @SerializedName(value = "display", alternate = {"exibicao"})
   public CommandRewardSpec display = null;

   /** Separate command rewards, each one with its own title/icon/lore and commands. */
   @Doc("Separate command rewards, each one with its own title, icon, lore and commands.")
   @SerializedName(value = "entries", alternate = {"entradas"})
   public List<CommandRewardSpec> entries = new ArrayList<>();
}
