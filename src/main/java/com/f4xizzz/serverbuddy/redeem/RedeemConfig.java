package com.f4xizzz.serverbuddy.redeem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RedeemConfig {
   public List<String> aliases = new ArrayList<>(List.of("redeem"));
   public RedeemMessages mensagens = new RedeemMessages();
   public Map<String, RedeemCode> codigos = new LinkedHashMap<>();
}
