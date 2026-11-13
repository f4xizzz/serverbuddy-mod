package com.f4xizzz.serverbuddy.cobblemon.poke.editor.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.f4xizzz.serverbuddy.cobblemon.poke.editor.config.EditorConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.poke.utils.SoundUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class RegionalFormsGui {
   public static void open(ServerPlayer player, Pokemon originalPokemon, Pokemon editingPokemon, boolean isDevMode) {
      openInternal(player, originalPokemon, editingPokemon, isDevMode, 0);
   }

   public static void openInternal(ServerPlayer player, Pokemon originalPokemon, Pokemon editingPokemon, boolean isDevMode, int page) {
      ChestTemplate.Builder template = ChestTemplate.builder(6);
      List<FormData> validForms = new ArrayList<>();

      for(Object formObj : editingPokemon.getSpecies().getForms()) {
         if (formObj instanceof FormData form) {
            String formName = form.getName().toLowerCase().trim();
            if (!formName.equals("normal") && !formName.equals("base")) {
               boolean isRegional = false;

               for(Object labelObj : form.getLabels()) {
                  String label = labelObj.toString().toLowerCase();
                  if (label.contains("region") && label.contains("bias")) {
                     isRegional = true;
                     break;
                  }
               }

               if (!isRegional) {
                  for(Object aspectObj : form.getAspects()) {
                     String aspect = aspectObj.toString().toLowerCase();
                     if (aspect.contains("region") && aspect.contains("bias")) {
                        isRegional = true;
                        break;
                     }
                  }
               }

               if (isRegional) {
                  validForms.add(form);
               }
            }
         }
      }

      List<FormData> displayForms = new ArrayList<>();
      displayForms.add(null);
      displayForms.addAll(validForms);
      int pageSize = 21;
      int maxPages = (int)Math.ceil((double)displayForms.size() / (double)pageSize);
      if (maxPages == 0) {
         maxPages = 1;
      }

      int start = page * pageSize;
      int end = Math.min(start + pageSize, displayForms.size());
      ItemStack bgBlue = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.background_primary : null, Items.BLUE_STAINED_GLASS_PANE));
      bgBlue.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnBlue = GooeyButton.builder().display(bgBlue).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();
      ItemStack bgRed = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.background_secondary : null, Items.RED_STAINED_GLASS_PANE));
      bgRed.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnRed = GooeyButton.builder().display(bgRed).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();
      ItemStack bgBlack = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.background_dark : null, Items.BLACK_STAINED_GLASS_PANE));
      bgBlack.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnBlack = GooeyButton.builder().display(bgBlack).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();
      ItemStack bgWhite = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.background_white : null, Items.WHITE_STAINED_GLASS_PANE));
      bgWhite.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
      GooeyButton btnWhite = GooeyButton.builder().display(bgWhite).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(" ")).build();

      for(int i = 0; i < 54; ++i) {
         if (i != 0 && i != 3 && i != 4 && i != 5 && i != 8 && i != 9 && i != 17) {
            if (i != 1 && i != 2 && i != 6 && i != 7) {
               if (i != 18 && i != 26 && (i < 45 || i > 53 || i == 48 || i == 49 || i == 50)) {
                  if (i >= 27 && i <= 44) {
                     template.set(i, btnWhite);
                  }
               } else {
                  template.set(i, btnBlack);
               }
            } else {
               template.set(i, btnRed);
            }
         } else {
            template.set(i, btnBlue);
         }
      }

      int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

      for(int i = start; i < end; ++i) {
         final int i_ = i;
         FormData form = (FormData)displayForms.get(i_);
         int slotIndex = i_ - start;
         boolean isBase = form == null;
         String currentFormName = editingPokemon.getForm().getName().toLowerCase();
         boolean isCurrent;
         if (!isBase) {
            isCurrent = currentFormName.equals(form.getName().toLowerCase());
         } else {
            isCurrent = currentFormName.equals("normal") || currentFormName.equals("base");
         }

         Pokemon preview = editingPokemon.clone(false, player.getServer().registryAccess());
         Set<String> forced = new HashSet<>(preview.getForcedAspects());

         for(FormData regForm : validForms) {
            for(Object aspectObj : regForm.getAspects()) {
               forced.remove(aspectObj.toString().toLowerCase().trim());
            }
         }

         if (!isBase) {
            for(Object aspectObj : form.getAspects()) {
               forced.add(aspectObj.toString().toLowerCase().trim());
            }
         }

         preview.setForcedAspects(forced);
         preview.updateAspects();
         preview.updateForm();
         ItemStack pokeItem = PokemonItem.from(preview);
         pokeItem.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
         String rawName;
         if (isBase) {
            rawName = EditorConfigManager.lang.form_base_name != null ? EditorConfigManager.lang.form_base_name : "Normal Form";
         } else {
            String formNameStr = form.getName();
            String var10000 = formNameStr.substring(0, 1).toUpperCase();
            String capitalized = var10000 + formNameStr.substring(1);
            String formFormat = EditorConfigManager.lang.form_name_format != null ? EditorConfigManager.lang.form_name_format : "<form_name> Form";
            rawName = formFormat.replace("<form_name>", capitalized);
         }

         String nameFormat;
         if (isCurrent) {
            nameFormat = EditorConfigManager.lang.form_current != null ? EditorConfigManager.lang.form_current : "<green><name> (Current)</green>";
            pokeItem.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
         } else {
            nameFormat = EditorConfigManager.lang.form_available != null ? EditorConfigManager.lang.form_available : "<yellow><name></yellow>";
         }

         String displayName = nameFormat.replace("<name>", rawName);
         template.set(slots[slotIndex], GooeyButton.builder().display(pokeItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(displayName)).onClick((a) -> {
            if (!isCurrent) {
               Set<String> newForced = new HashSet<>(editingPokemon.getForcedAspects());

               for(FormData regForm : validForms) {
                  for(Object aspectObj : regForm.getAspects()) {
                     newForced.remove(aspectObj.toString().toLowerCase().trim());
                  }
               }

               if (!isBase) {
                  for(Object aspectObj : form.getAspects()) {
                     newForced.add(aspectObj.toString().toLowerCase().trim());
                  }
               }

               editingPokemon.setForcedAspects(newForced);
               editingPokemon.updateAspects();
               editingPokemon.updateForm();
               editingPokemon.heal();
               SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
               openInternal(player, originalPokemon, editingPokemon, isDevMode, page);
            }
         }).build());
      }

      if (page > 0) {
         ItemStack prevItem = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.previous_page : null, Items.ARROW));
         String prevName = EditorConfigManager.lang.form_page_prev != null ? EditorConfigManager.lang.form_page_prev : "<yellow>Previous</yellow>";
         template.set(48, GooeyButton.builder().display(prevItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(prevName)).onClick((a) -> {
            SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
            openInternal(player, originalPokemon, editingPokemon, isDevMode, page - 1);
         }).build());
      } else {
         template.set(48, btnBlack);
      }

      if (page < maxPages - 1) {
         ItemStack nextItem = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.next_page : null, Items.ARROW));
         String nextName = EditorConfigManager.lang.form_page_next != null ? EditorConfigManager.lang.form_page_next : "<yellow>Next</yellow>";
         template.set(50, GooeyButton.builder().display(nextItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(nextName)).onClick((a) -> {
            SoundUtils.playAnySound(player, EditorConfigManager.sounds.click);
            openInternal(player, originalPokemon, editingPokemon, isDevMode, page + 1);
         }).build());
      } else {
         template.set(50, btnBlack);
      }

      ItemStack closeItem = new ItemStack(getSafeItem(EditorConfigManager.guiconfig.icons != null ? EditorConfigManager.guiconfig.icons.close_button : null, Items.BARRIER));
      String backText = EditorConfigManager.lang.button_discard_return != null ? EditorConfigManager.lang.button_discard_return : "<red>Return</red>";
      template.set(49, GooeyButton.builder().display(closeItem).with(DataComponents.CUSTOM_NAME, EditorConfigManager.parse(backText)).onClick((a) -> {
         SoundUtils.playAnySound(player, EditorConfigManager.sounds.error);
         PokemonEditorGui.openInternal(player, originalPokemon, editingPokemon, isDevMode);
      }).build());
      String titleFormat = EditorConfigManager.lang.form_gui_title != null ? EditorConfigManager.lang.form_gui_title : "<dark_gray>Form Editor (<current_page>/<total_pages>)</dark_gray>";
      String guiTitle = titleFormat.replace("<current_page>", String.valueOf(page + 1)).replace("<total_pages>", String.valueOf(maxPages));
      GooeyPage gooeyPage = GooeyPage.builder().template(template.build()).title(EditorConfigManager.parse(guiTitle)).build();
      UIManager.openUIForcefully(player, gooeyPage);
   }

   private static Item getSafeItem(String iconId, Item fallback) {
      if (iconId != null && !iconId.trim().isEmpty()) {
         try {
            Item item = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.parse(iconId));
            return item != null && item != Items.AIR ? item : fallback;
         } catch (Exception var3) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }
}
