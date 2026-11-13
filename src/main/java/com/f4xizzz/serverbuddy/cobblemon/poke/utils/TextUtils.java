package com.f4xizzz.serverbuddy.cobblemon.poke.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.platform.fabric.FabricAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class TextUtils {
   private static final Pattern LEGACY_CODE = Pattern.compile("(?i)&([0-9a-fk-or])");

   private static String translateLegacy(String text) {
      Matcher matcher = LEGACY_CODE.matcher(text);
      StringBuilder result = new StringBuilder();

      while(matcher.find()) {
         String var10000;
         switch (Character.toLowerCase(matcher.group(1).charAt(0))) {
            case '0':
               var10000 = "<black>";
               break;
            case '1':
               var10000 = "<dark_blue>";
               break;
            case '2':
               var10000 = "<dark_green>";
               break;
            case '3':
               var10000 = "<dark_aqua>";
               break;
            case '4':
               var10000 = "<dark_red>";
               break;
            case '5':
               var10000 = "<dark_purple>";
               break;
            case '6':
               var10000 = "<gold>";
               break;
            case '7':
               var10000 = "<gray>";
               break;
            case '8':
               var10000 = "<dark_gray>";
               break;
            case '9':
               var10000 = "<blue>";
               break;
            case ':':
            case ';':
            case '<':
            case '=':
            case '>':
            case '?':
            case '@':
            case 'A':
            case 'B':
            case 'C':
            case 'D':
            case 'E':
            case 'F':
            case 'G':
            case 'H':
            case 'I':
            case 'J':
            case 'K':
            case 'L':
            case 'M':
            case 'N':
            case 'O':
            case 'P':
            case 'Q':
            case 'R':
            case 'S':
            case 'T':
            case 'U':
            case 'V':
            case 'W':
            case 'X':
            case 'Y':
            case 'Z':
            case '[':
            case '\\':
            case ']':
            case '^':
            case '_':
            case '`':
            case 'g':
            case 'h':
            case 'i':
            case 'j':
            case 'p':
            case 'q':
            default:
               var10000 = matcher.group();
               break;
            case 'a':
               var10000 = "<green>";
               break;
            case 'b':
               var10000 = "<aqua>";
               break;
            case 'c':
               var10000 = "<red>";
               break;
            case 'd':
               var10000 = "<light_purple>";
               break;
            case 'e':
               var10000 = "<yellow>";
               break;
            case 'f':
               var10000 = "<white>";
               break;
            case 'k':
               var10000 = "<obfuscated>";
               break;
            case 'l':
               var10000 = "<bold>";
               break;
            case 'm':
               var10000 = "<strikethrough>";
               break;
            case 'n':
               var10000 = "<underlined>";
               break;
            case 'o':
               var10000 = "<italic>";
               break;
            case 'r':
               var10000 = "<reset>";
         }

         String tag = var10000;
         matcher.appendReplacement(result, Matcher.quoteReplacement(tag));
      }

      matcher.appendTail(result);
      return result.toString();
   }

   public static net.minecraft.network.chat.Component parse(String text) {
      if (text == null) {
         return net.minecraft.network.chat.Component.empty();
      } else {
         Component kyoriComponent = MiniMessage.miniMessage().deserialize(translateLegacy(text));
         return (net.minecraft.network.chat.Component)FabricAudiences.nonWrappingSerializer().serialize(kyoriComponent);
      }
   }
}
