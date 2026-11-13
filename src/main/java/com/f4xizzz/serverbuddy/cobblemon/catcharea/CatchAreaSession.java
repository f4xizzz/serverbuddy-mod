package com.f4xizzz.serverbuddy.cobblemon.catcharea;

import java.util.UUID;

public class CatchAreaSession {
   public UUID playerId;
   public String areaName;
   public String baseSpeciesId;
   public long expiresAtMillis;
   public long lastSpawnAtMillis;
   public Long offlineSinceMillis;
   public Long awayFromAreaSinceMillis;
}
