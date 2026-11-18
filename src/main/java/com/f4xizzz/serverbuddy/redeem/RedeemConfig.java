package com.f4xizzz.serverbuddy.redeem;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RedeemConfig {
   public List<String> aliases = new ArrayList<>(List.of("redeem"));

   @SerializedName(value = "messages", alternate = {"mensagens"})
   public RedeemMessages messages = new RedeemMessages();

   /** The codes by name. The name is what the player types (capital letters do not matter). */
   @SerializedName(value = "codes", alternate = {"codigos"})
   public Map<String, RedeemCode> codes = new LinkedHashMap<>();
}
