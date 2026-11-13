package com.f4xizzz.serverbuddy.cobblemon.hunt;

import java.util.ArrayList;
import java.util.List;

public class HuntConfig {
   public boolean individual = false;
   public int quantidadeHunts = 7;
   public int duracaoMinutos = 60;
   public String moeda = "impactor:coins";
   public HuntRarityWeights raridade = new HuntRarityWeights();
   public HuntRewardsConfig recompensas = new HuntRewardsConfig();
   public List<HuntCustomPrice> precosCustomizados = new ArrayList<>();
   public HuntMatchProperties propriedadesVerificadas = new HuntMatchProperties();
   public List<String> listaNegra = new ArrayList<>();
   public boolean avisarInicio = true;
   public boolean avisarFim = true;
   // quando varias cacadas vencem juntas (o normal: elas nascem juntas), manda UMA mensagem so (mensagens.resetadas)
   // em vez de um "expirou" + um "nova cacada" para cada uma
   public boolean agruparAvisos = true;
   public HuntMessages mensagens = new HuntMessages();
}
