package com.f4xizzz.serverbuddy.cobblemon.poke.builder.config;

import java.util.List;

public class BuilderLangModel {
   public String prefix = "<white><bold>[<#3077c2>G<red>r</red>e<red>a</red>t</#3077c2><white>Builder]:</white></bold>";
   public String word_yes = "Yes";
   public String word_no = "No";
   public String word_none = "None";
   public String builder_searching = "%prefix% <gray>Searching competitive builds for: <gold><pokemon_name></gold>...</gray>";
   public String builder_no_builds = "<red>Does not have competitive builds for this Pok\u00e9mon!</red>";
   public String tooltip_gender_male = "<#55ffff><bold>\u2642 Male</bold></#55ffff>";
   public String tooltip_gender_female = "<#ff55ff><bold>\u2640 Female</bold></#ff55ff>";
   public String tooltip_gender_none = "<white><bold>\u26aa Genderless</bold></white>";
   public String pokemon_tooltip_ha_suffix = "(HA)";
   public List<String> pokemon_tooltip = List.of("<#83dcde><lang:cobblemon.ui.lv> &f%level%", "<#14b2dd><lang:cobblemon.ui.info.type>: &f%types%", "<#ecca18>Form: &f%form%", "<#4969dc>Gender: %gender%", "<#D0A5DE><lang:cobblemon.ui.stats.friendship>: &f%friendship%", "<#de896f>Tradeable: &f%tradeable%", "<#b0eb59>Breedable: &f%breedable%", "<#9be8c2><lang:cobblemon.ui.info.nature>: &f%nature% &f(&a\u2191%up%&f/&c\u2193%down%&f)", "<#6fa7de><lang:cobblemon.ui.info.ability>: &f%ability% %ha%", "<#83a7de><lang:cobblemon.ui.stats.ivs>: &e%ivs%&7/&e31", " <#ee8339><lang:cobblemon.ui.stats.hp>: &f%ivshp% (%htivshp%) <#e84b48><lang:cobblemon.ui.stats.atk>: &f%ivsatk% (%htivsatk%) <#5d79e1><lang:cobblemon.ui.stats.def>: &f%ivsdef% (%htivsdef%)", " <#40b5cd><lang:cobblemon.ui.stats.sp_atk>: &f%ivsspa% (%htivsspa%) <#f59bc2><lang:cobblemon.ui.stats.sp_def>: &f%ivsspdef% (%htivsspdef%) <#69cd65><lang:cobblemon.ui.stats.speed>: &f%ivsspeed% (%htivsspeed%)", "<#de8397><lang:cobblemon.ui.stats.evs>: &e%evs%&7/&e510", " <#ee8339><lang:cobblemon.ui.stats.hp>: &f%evshp% <#e84b48><lang:cobblemon.ui.stats.atk>: &f%evsatk% <#5d79e1><lang:cobblemon.ui.stats.def>: &f%evsdef%", " <#40b5cd><lang:cobblemon.ui.stats.sp_atk>: &f%evsspa% <#f59bc2><lang:cobblemon.ui.stats.sp_def>: &f%evsspdef% <#69cd65><lang:cobblemon.ui.stats.speed>: &f%evsspeed%", "<#e35146>Pok\u00e9 Ball: &f%ball%", "<#ecca18>Size: &f%size%", "<#ecca18><lang:cobblemon.held_item>: &f%item%", "<#98eb59><lang:cobblemon.ui.moves>: &f%move1% &f- %move2% &f- %move3% &f- %move4%");
   public String builder_gui_title = "<#3077c2><bold>\u274b Builds:  \u25b6</bold><white> <pokemon_name> <#3077c2><bold>\u25c0</#3077c2>";
   public String builder_status_incorrect = "<red><bold>\u2718 Incorrect Species / Empty Slot \u2718</bold></red>";
   public String builder_status_partial = "<yellow><bold>\u26a0 Correct Species</bold> <gray>(Incomplete Build)</gray></yellow>";
   public String builder_status_correct = "<#55ff55><bold>\u2714 Build 100% Correct!</bold></#55ff55>";
   public String builder_empty_slot = "<dark_gray>Empty Slot</dark_gray>";
   public String builder_button_close = "<red><bold>\u2718 Close Builder \u2718</bold></red>";
   public String builder_lore_format = "<dark_gray>\u25aa</dark_gray> <gray>Format:</gray> <aqua><battle_type></aqua>";
   public String builder_lore_generation = "<dark_gray>\u25aa</dark_gray> <gray>Generation:</gray> <yellow>Gen <generation></yellow>";
   public String builder_lore_item = "<dark_gray>\u25aa</dark_gray> <gray>Item:</gray> <gold><item></gold>";
   public String builder_lore_ability = "<dark_gray>\u25aa</dark_gray> <gray>Ability:</gray> <#ff55ff><ability></#ff55ff>";
   public String builder_lore_nature = "<dark_gray>\u25aa</dark_gray> <gray>Nature:</gray> <#55ff55><nature></#55ff55>";
   public String builder_lore_evs_title = "<aqua><bold>\u2756 Recommended EVs:</bold></aqua>";
   public String builder_lore_evs_none = "<dark_gray>\u25aa</dark_gray> <gray>None specific</gray>";
   public String builder_lore_evs_line = "<dark_gray>\u25aa</dark_gray> <gray><stat>:</gray> <white><value></white>";
   public String builder_lore_moves_title = "<gold><bold>\u2756 Suggested Moves:</bold></gold>";
   public String builder_lore_moves_line = "<dark_gray>\u25aa</dark_gray> <gray><move></gray>";
   public String builder_lore_selected = "<#55ff55>\u2714 Build selected for comparison!</#55ff55>";
   public String builder_lore_unselected = "<yellow>\u2726 Click to select this build!</yellow>";
}
