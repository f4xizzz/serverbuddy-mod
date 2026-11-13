package com.f4xizzz.serverbuddy.cobblemon.poke.editor.config;

import java.util.ArrayList;
import java.util.List;

public class EditorServerConfigModel {
   public String impactor_currency = "impactor:dollar";
   public String currency_icon_item = "minecraft:sunflower";
   public String currency_prefix = "<green><b>$<balance></b></green>";
   public String wallet_balance_placeholder = "%impactor:balance_short impactor:dollar%";
   public String permission_dev_mode = "serverbuddy.editor.devmode";
   public String permission_admin_aspects = "serverbuddy.editor.admin.aspects";
   public List<String> command_aliases = new ArrayList<>(List.of("editor", "pokeeditor"));
   public List<String> blocked_species = new ArrayList<>();
}
