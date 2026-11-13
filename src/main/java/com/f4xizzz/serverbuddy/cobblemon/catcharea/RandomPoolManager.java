package com.f4xizzz.serverbuddy.cobblemon.catcharea;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.database.DatabaseManager;
import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public class RandomPoolManager {
   private static final Gson GSON = new Gson();
   private static List<String> currentPool = new ArrayList<>();
   private static long rotatedAtMillis = 0L;

   private RandomPoolManager() {
   }

   public static void createTable() {
      String sql = "CREATE TABLE IF NOT EXISTS catch_area_pool_state (id INTEGER PRIMARY KEY, species_json TEXT NOT NULL, rotated_at BIGINT NOT NULL)";

      try {
         Connection conn = DatabaseManager.getConnection();

         try {
            Statement stmt = conn.createStatement();

            try {
               stmt.execute(sql);
            } catch (Throwable var7) {
               if (stmt != null) {
                  try {
                     stmt.close();
                  } catch (Throwable var6) {
                     var7.addSuppressed(var6);
                  }
               }

               throw var7;
            }

            if (stmt != null) {
               stmt.close();
            }
         } catch (Throwable var8) {
            if (conn != null) {
               try {
                  conn.close();
               } catch (Throwable var5) {
                  var8.addSuppressed(var5);
               }
            }

            throw var8;
         }

         if (conn != null) {
            conn.close();
         }
      } catch (SQLException e) {
         ServerBuddy.LOGGER.error("Could not create table catch_area_pool_state!", e);
      }

   }

   public static void load() {
      String sql = "SELECT species_json, rotated_at FROM catch_area_pool_state WHERE id = 1";

      try {
         Connection conn = DatabaseManager.getConnection();

         try {
            Statement stmt = conn.createStatement();

            try {
               ResultSet rs = stmt.executeQuery(sql);

               try {
                  if (rs.next()) {
                     currentPool = new ArrayList<>(Arrays.asList((String[])GSON.fromJson(rs.getString("species_json"), String[].class)));
                     rotatedAtMillis = rs.getLong("rotated_at");
                  }
               } catch (Throwable var9) {
                  if (rs != null) {
                     try {
                        rs.close();
                     } catch (Throwable var8) {
                        var9.addSuppressed(var8);
                     }
                  }

                  throw var9;
               }

               if (rs != null) {
                  rs.close();
               }
            } catch (Throwable var10) {
               if (stmt != null) {
                  try {
                     stmt.close();
                  } catch (Throwable var7) {
                     var10.addSuppressed(var7);
                  }
               }

               throw var10;
            }

            if (stmt != null) {
               stmt.close();
            }
         } catch (Throwable var11) {
            if (conn != null) {
               try {
                  conn.close();
               } catch (Throwable var6) {
                  var11.addSuppressed(var6);
               }
            }

            throw var11;
         }

         if (conn != null) {
            conn.close();
         }
      } catch (SQLException e) {
         ServerBuddy.LOGGER.error("Error loading the random catch pool!", e);
      }

      if (currentPool.isEmpty()) {
         rotate();
      }

   }

   public static void refreshIfDue() {
      int hours = CatchAreaConfigManager.getConfig().randomPoolRotationHours;
      long intervalMillis = (long)hours * 3600000L;
      if (System.currentTimeMillis() - rotatedAtMillis >= intervalMillis) {
         rotate();
      }

   }

   public static void rotate() {
      int size = CatchAreaConfigManager.getConfig().randomPoolSize;
      List<Species> candidates = new ArrayList<>();

      for(Species species : PokemonSpecies.getSpecies()) {
         if (!BannedSpeciesConfigManager.isBlocked(species)) {
            candidates.add(species);
         }
      }

      Collections.shuffle(candidates);
      List<String> newPool = new ArrayList<>();

      for(int i = 0; i < Math.min(size, candidates.size()); ++i) {
         newPool.add(((Species)candidates.get(i)).getResourceIdentifier().toString());
      }

      currentPool = newPool;
      rotatedAtMillis = System.currentTimeMillis();
      persist();
      ServerBuddy.LOGGER.info("Random catch area pool rotated: {}", currentPool);
   }

   private static void persist() {
      String json = GSON.toJson(currentPool);
      String sql = DatabaseManager.isUsingMySQL() ? "INSERT INTO catch_area_pool_state (id, species_json, rotated_at) VALUES (1, ?, ?) ON DUPLICATE KEY UPDATE species_json = VALUES(species_json), rotated_at = VALUES(rotated_at)" : "INSERT INTO catch_area_pool_state (id, species_json, rotated_at) VALUES (1, ?, ?) ON CONFLICT(id) DO UPDATE SET species_json = excluded.species_json, rotated_at = excluded.rotated_at";

      try {
         Connection conn = DatabaseManager.getConnection();

         try {
            PreparedStatement pstmt = conn.prepareStatement(sql);

            try {
               pstmt.setString(1, json);
               pstmt.setLong(2, rotatedAtMillis);
               pstmt.executeUpdate();
            } catch (Throwable var8) {
               if (pstmt != null) {
                  try {
                     pstmt.close();
                  } catch (Throwable var7) {
                     var8.addSuppressed(var7);
                  }
               }

               throw var8;
            }

            if (pstmt != null) {
               pstmt.close();
            }
         } catch (Throwable var9) {
            if (conn != null) {
               try {
                  conn.close();
               } catch (Throwable var6) {
                  var9.addSuppressed(var6);
               }
            }

            throw var9;
         }

         if (conn != null) {
            conn.close();
         }
      } catch (SQLException e) {
         ServerBuddy.LOGGER.error("Error saving the random catch pool!", e);
      }

   }

   public static List<Species> getCurrentPoolSpecies() {
      List<Species> result = new ArrayList<>();

      for(String id : currentPool) {
         Species species = PokemonSpecies.getByIdentifier(ResourceLocation.parse(id));
         if (species != null) {
            result.add(species);
         }
      }

      return result;
   }

   public static long getNextRotationMillis() {
      int hours = CatchAreaConfigManager.getConfig().randomPoolRotationHours;
      return rotatedAtMillis + (long)hours * 3600000L;
   }
}
