package com.f4xizzz.serverbuddy.cobblemon.poke.utils;

import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

public class PokemonTooltipUtils {
   private static final Stat[] STAT_ORDER;
   private static final String[] IV_KEYS;
   private static final String[] HT_IV_KEYS;
   private static final String[] EV_KEYS;

   public static ItemLore build(List<String> template, Pokemon p, TooltipStrings strings, Map<String, String> extraOverrides) {
      Map<String, String> tokens = new HashMap<>();
      tokens.put("level", String.valueOf(p.getLevel()));
      StringBuilder types = new StringBuilder();

      for(ElementalType type : p.getTypes()) {
         if (types.length() > 0) {
            types.append(" / ");
         }

         types.append(capitalize(type.getName()));
      }

      tokens.put("types", types.toString());
      tokens.put("form", capitalize(p.getForm().getName()));
      String gender;
      if (p.getGender() == Gender.MALE) {
         gender = strings.genderMale;
      } else if (p.getGender() == Gender.FEMALE) {
         gender = strings.genderFemale;
      } else {
         gender = strings.genderNone;
      }

      tokens.put("gender", gender);
      tokens.put("friendship", String.valueOf(p.getFriendship()));
      tokens.put("tradeable", p.getTradeable() ? strings.wordYes : strings.wordNo);
      boolean breedable = !p.getSpecies().getEggGroups().contains(EggGroup.UNDISCOVERED);
      tokens.put("breedable", breedable ? strings.wordYes : strings.wordNo);
      Nature nature = p.getNature();
      tokens.put("nature", capitalize(nature.getName().getPath()));
      tokens.put("up", nature.getIncreasedStat() != null ? capitalize(nature.getIncreasedStat().getIdentifier().getPath()) : "-");
      tokens.put("down", nature.getDecreasedStat() != null ? capitalize(nature.getDecreasedStat().getIdentifier().getPath()) : "-");
      String abilityName = p.getAbility().getTemplate().getName();
      tokens.put("ability", capitalize(abilityName));
      boolean isHidden = false;

      for(PotentialAbility potential : p.getForm().getAbilities()) {
         if (potential.getTemplate().getName().equals(abilityName)) {
            isHidden = potential instanceof HiddenAbility;
            break;
         }
      }

      tokens.put("ha", isHidden ? strings.haSuffix : "");
      int ivSum = 0;
      int evSum = 0;

      for(int i = 0; i < STAT_ORDER.length; ++i) {
         int rawIv = p.getIvs().getOrDefault(STAT_ORDER[i]);
         int effectiveIv = p.getIvs().getEffectiveBattleIV(STAT_ORDER[i]);
         int ev = p.getEvs().getOrDefault(STAT_ORDER[i]);
         tokens.put(IV_KEYS[i], String.valueOf(rawIv));
         tokens.put(HT_IV_KEYS[i], String.valueOf(effectiveIv));
         tokens.put(EV_KEYS[i], String.valueOf(ev));
         ivSum += rawIv;
         evSum += ev;
      }

      double ivAverage = (double)ivSum / 6.0;
      tokens.put("ivs", ivAverage == Math.floor(ivAverage) ? String.valueOf((int)ivAverage) : String.format("%.1f", ivAverage));
      tokens.put("evs", String.valueOf(evSum));
      tokens.put("ball", capitalize(p.getCaughtBall().getName().getPath()));
      float var10002 = p.getScaleModifier();
      tokens.put("size", Math.round(var10002 * 100.0F) + "%");
      ItemStack heldItem = p.heldItem();
      tokens.put("item", heldItem.isEmpty() ? strings.wordNone : heldItem.getHoverName().getString());
      List<Move> moves = p.getMoveSet().getMovesWithNulls();

      for(int i = 0; i < 4; ++i) {
         Move move = i < moves.size() ? (Move)moves.get(i) : null;
         tokens.put("move" + (i + 1), move != null ? move.getDisplayName().getString() : strings.wordNone);
      }

      tokens.putAll(extraOverrides);
      List<Component> lore = new ArrayList<>();

      for(String line : template) {
         String resolved = line;

         for(Map.Entry<String, String> entry : tokens.entrySet()) {
            resolved = resolved.replace("%" + (String)entry.getKey() + "%", (CharSequence)entry.getValue());
         }

         lore.add(TextUtils.parse(resolved));
      }

      return new ItemLore(lore);
   }

   public static String capitalize(String input) {
      if (input != null && !input.isEmpty()) {
         String spaced = input.replace("_", " ");
         String var10000 = spaced.substring(0, 1).toUpperCase();
         return var10000 + spaced.substring(1);
      } else {
         return input;
      }
   }

   static {
      STAT_ORDER = new Stat[]{Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};
      IV_KEYS = new String[]{"ivshp", "ivsatk", "ivsdef", "ivsspa", "ivsspdef", "ivsspeed"};
      HT_IV_KEYS = new String[]{"htivshp", "htivsatk", "htivsdef", "htivsspa", "htivsspdef", "htivsspeed"};
      EV_KEYS = new String[]{"evshp", "evsatk", "evsdef", "evsspa", "evsspdef", "evsspeed"};
   }
}
