package com.f4xizzz.serverbuddy.redeem;

import com.google.gson.annotations.SerializedName;

/**
 * Texts of /redeem (MiniMessage). Placeholders: {code} (the old {codigo} still works), {player} and, in the permission text, {permission}.
 * The old Portuguese keys of redeem.conf are still read (the "alternate" names); new files are written with the English keys.
 */
public class RedeemMessages {
   @SerializedName(value = "invalid_code", alternate = {"codigo_invalido"})
   public String invalidCode = "<red>The code '<dark_red>{code}</dark_red>' does not exist!";

   @SerializedName(value = "already_redeemed", alternate = {"ja_resgatado"})
   public String alreadyRedeemed = "<red>You have already redeemed the code '<dark_red>{code}</dark_red>'!";

   @SerializedName(value = "usage_limit_reached", alternate = {"limite_atingido"})
   public String usageLimitReached = "<red>The code '<dark_red>{code}</dark_red>' has reached its usage limit!";

   /** Shown when the code asks for a permission the player does not have (the code's "permission" field). */
   @SerializedName(value = "no_permission", alternate = {"sem_permissao_codigo"})
   public String noPermission = "<red>You don't have permission to redeem the code '<dark_red>{code}</dark_red>'.";

   /** Shown after the code's "expires_at" moment. */
   public String expired = "<red>The code '<dark_red>{code}</dark_red>' has expired!";

   @SerializedName(value = "success", alternate = {"sucesso"})
   public String success = "<green>Code '<dark_green>{code}</dark_green>' redeemed successfully!";
}
