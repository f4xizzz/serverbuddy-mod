package com.f4xizzz.serverbuddy.redeem;

import com.f4xizzz.serverbuddy.core.ItemSpec;
import com.f4xizzz.serverbuddy.storage.CommandRewardSpec;
import java.util.ArrayList;
import java.util.List;

public class RedeemCode {
   public int max_usos = 0;
   public String permissao = null;
   /** Comandos do resgate (rodam como console; %player% e o jogador). Com o /storage ligado, viram UMA recompensa no /storage. */
   public List<String> comandos = new ArrayList<>();
   /** Itens do resgate (mesmo formato dos itens dos menus: item, amount, name, lore, custom_model_data...). Cada um vira uma recompensa no /storage. */
   public List<ItemSpec> itens = new ArrayList<>();
   /** Como as recompensas de "comandos" aparecem no /storage (titulo, icone, lore). */
   public CommandRewardSpec exibicao = null;
   /** Recompensas de comando separadas, cada uma com o seu titulo/icone/lore e os seus comandos. */
   public List<CommandRewardSpec> entradas = new ArrayList<>();
}
