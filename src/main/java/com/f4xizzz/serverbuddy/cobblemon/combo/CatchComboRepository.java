package com.f4xizzz.serverbuddy.cobblemon.combo;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.database.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CatchComboRepository {
   private CatchComboRepository() {
   }

   public static void createTable() {
      String sql = "CREATE TABLE IF NOT EXISTS catch_combo (player_uuid VARCHAR(36) PRIMARY KEY, species_id VARCHAR(64) NOT NULL, species_display_name VARCHAR(64) NOT NULL, combo_count INT NOT NULL)";

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
         ServerBuddy.LOGGER.error("Could not create table catch_combo!", e);
      }

   }

   public static Map<UUID, CatchComboState> loadAll() {
      Map<UUID, CatchComboState> result = new HashMap<>();
      String sql = "SELECT player_uuid, species_id, species_display_name, combo_count FROM catch_combo";

      try {
         Connection conn = DatabaseManager.getConnection();

         try {
            Statement stmt = conn.createStatement();

            try {
               ResultSet rs = stmt.executeQuery(sql);

               try {
                  while(rs.next()) {
                     CatchComboState state = new CatchComboState();
                     state.speciesId = rs.getString("species_id");
                     state.speciesDisplayName = rs.getString("species_display_name");
                     state.comboCount = rs.getInt("combo_count");
                     result.put(UUID.fromString(rs.getString("player_uuid")), state);
                  }
               } catch (Throwable var10) {
                  if (rs != null) {
                     try {
                        rs.close();
                     } catch (Throwable var9) {
                        var10.addSuppressed(var9);
                     }
                  }

                  throw var10;
               }

               if (rs != null) {
                  rs.close();
               }
            } catch (Throwable var11) {
               if (stmt != null) {
                  try {
                     stmt.close();
                  } catch (Throwable var8) {
                     var11.addSuppressed(var8);
                  }
               }

               throw var11;
            }

            if (stmt != null) {
               stmt.close();
            }
         } catch (Throwable var12) {
            if (conn != null) {
               try {
                  conn.close();
               } catch (Throwable var7) {
                  var12.addSuppressed(var7);
               }
            }

            throw var12;
         }

         if (conn != null) {
            conn.close();
         }
      } catch (SQLException e) {
         ServerBuddy.LOGGER.error("Could not load catch combos from the database!", e);
      }

      return result;
   }

   public static void saveAsync(UUID playerId, CatchComboState state) {
      CompletableFuture.runAsync(() -> {
         String sql = DatabaseManager.isUsingMySQL() ? "INSERT INTO catch_combo (player_uuid, species_id, species_display_name, combo_count) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE species_id = VALUES(species_id), species_display_name = VALUES(species_display_name), combo_count = VALUES(combo_count)" : "INSERT INTO catch_combo (player_uuid, species_id, species_display_name, combo_count) VALUES (?, ?, ?, ?) ON CONFLICT(player_uuid) DO UPDATE SET species_id = excluded.species_id, species_display_name = excluded.species_display_name, combo_count = excluded.combo_count";

         try {
            Connection conn = DatabaseManager.getConnection();

            try {
               PreparedStatement pstmt = conn.prepareStatement(sql);

               try {
                  pstmt.setString(1, playerId.toString());
                  pstmt.setString(2, state.speciesId);
                  pstmt.setString(3, state.speciesDisplayName);
                  pstmt.setInt(4, state.comboCount);
                  pstmt.executeUpdate();
               } catch (Throwable var9) {
                  if (pstmt != null) {
                     try {
                        pstmt.close();
                     } catch (Throwable var8) {
                        var9.addSuppressed(var8);
                     }
                  }

                  throw var9;
               }

               if (pstmt != null) {
                  pstmt.close();
               }
            } catch (Throwable var10) {
               if (conn != null) {
                  try {
                     conn.close();
                  } catch (Throwable var7) {
                     var10.addSuppressed(var7);
                  }
               }

               throw var10;
            }

            if (conn != null) {
               conn.close();
            }
         } catch (SQLException e) {
            ServerBuddy.LOGGER.error("Could not save catch combo for " + String.valueOf(playerId) + "!", e);
         }

      });
   }

   public static void deleteAsync(UUID playerId) {
      CompletableFuture.runAsync(() -> {
         String sql = "DELETE FROM catch_combo WHERE player_uuid = ?";

         try {
            Connection conn = DatabaseManager.getConnection();

            try {
               PreparedStatement pstmt = conn.prepareStatement(sql);

               try {
                  pstmt.setString(1, playerId.toString());
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
            ServerBuddy.LOGGER.error("Could not remove catch combo for " + String.valueOf(playerId) + "!", e);
         }

      });
   }
}
