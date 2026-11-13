package com.f4xizzz.serverbuddy.cobblemon.combo;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.database.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CatchComboLeaderboardRepository {
   private CatchComboLeaderboardRepository() {
   }

   public static void createTable() {
      String sql = "CREATE TABLE IF NOT EXISTS catch_combo_leaderboard (player_uuid VARCHAR(36) PRIMARY KEY, player_name VARCHAR(64) NOT NULL, best_species_id VARCHAR(64), best_species_name VARCHAR(64), best_combo INT NOT NULL)";

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
         ServerBuddy.LOGGER.error("Could not create table catch_combo_leaderboard!", e);
      }

   }

   public static void recordAttemptAsync(UUID playerId, String playerName, String speciesId, String speciesName, int comboCount) {
      CompletableFuture.runAsync(() -> {
         String sql = DatabaseManager.isUsingMySQL() ? "INSERT INTO catch_combo_leaderboard (player_uuid, player_name, best_species_id, best_species_name, best_combo) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE player_name = IF(VALUES(best_combo) > best_combo, VALUES(player_name), player_name), best_species_id = IF(VALUES(best_combo) > best_combo, VALUES(best_species_id), best_species_id), best_species_name = IF(VALUES(best_combo) > best_combo, VALUES(best_species_name), best_species_name), best_combo = GREATEST(best_combo, VALUES(best_combo))" : "INSERT INTO catch_combo_leaderboard (player_uuid, player_name, best_species_id, best_species_name, best_combo) VALUES (?, ?, ?, ?, ?) ON CONFLICT(player_uuid) DO UPDATE SET player_name = CASE WHEN excluded.best_combo > best_combo THEN excluded.player_name ELSE player_name END, best_species_id = CASE WHEN excluded.best_combo > best_combo THEN excluded.best_species_id ELSE best_species_id END, best_species_name = CASE WHEN excluded.best_combo > best_combo THEN excluded.best_species_name ELSE best_species_name END, best_combo = MAX(best_combo, excluded.best_combo)";

         try {
            Connection conn = DatabaseManager.getConnection();

            try {
               PreparedStatement pstmt = conn.prepareStatement(sql);

               try {
                  pstmt.setString(1, playerId.toString());
                  pstmt.setString(2, playerName);
                  pstmt.setString(3, speciesId);
                  pstmt.setString(4, speciesName);
                  pstmt.setInt(5, comboCount);
                  pstmt.executeUpdate();
               } catch (Throwable var12) {
                  if (pstmt != null) {
                     try {
                        pstmt.close();
                     } catch (Throwable var11) {
                        var12.addSuppressed(var11);
                     }
                  }

                  throw var12;
               }

               if (pstmt != null) {
                  pstmt.close();
               }
            } catch (Throwable var13) {
               if (conn != null) {
                  try {
                     conn.close();
                  } catch (Throwable var10) {
                     var13.addSuppressed(var10);
                  }
               }

               throw var13;
            }

            if (conn != null) {
               conn.close();
            }
         } catch (SQLException e) {
            ServerBuddy.LOGGER.error("Could not save catch combo record for " + String.valueOf(playerId) + "!", e);
         }

      });
   }

   public static List<CatchComboLeaderboardEntry> getTop(int limit) {
      List<CatchComboLeaderboardEntry> result = new ArrayList<>();
      String sql = "SELECT player_name, best_species_name, best_combo FROM catch_combo_leaderboard ORDER BY best_combo DESC LIMIT ?";

      try {
         Connection conn = DatabaseManager.getConnection();

         try {
            PreparedStatement pstmt = conn.prepareStatement(sql);

            try {
               pstmt.setInt(1, limit);
               ResultSet rs = pstmt.executeQuery();

               try {
                  while(rs.next()) {
                     CatchComboLeaderboardEntry entry = new CatchComboLeaderboardEntry();
                     entry.playerName = rs.getString("player_name");
                     entry.speciesName = rs.getString("best_species_name");
                     entry.bestCombo = rs.getInt("best_combo");
                     result.add(entry);
                  }
               } catch (Throwable var11) {
                  if (rs != null) {
                     try {
                        rs.close();
                     } catch (Throwable var10) {
                        var11.addSuppressed(var10);
                     }
                  }

                  throw var11;
               }

               if (rs != null) {
                  rs.close();
               }
            } catch (Throwable var12) {
               if (pstmt != null) {
                  try {
                     pstmt.close();
                  } catch (Throwable var9) {
                     var12.addSuppressed(var9);
                  }
               }

               throw var12;
            }

            if (pstmt != null) {
               pstmt.close();
            }
         } catch (Throwable var13) {
            if (conn != null) {
               try {
                  conn.close();
               } catch (Throwable var8) {
                  var13.addSuppressed(var8);
               }
            }

            throw var13;
         }

         if (conn != null) {
            conn.close();
         }
      } catch (SQLException e) {
         ServerBuddy.LOGGER.error("Could not load catch combo leaderboard!", e);
      }

      return result;
   }

   public static boolean removeByName(String playerName) {
      String sql = "DELETE FROM catch_combo_leaderboard WHERE LOWER(player_name) = LOWER(?)";

      try {
         Connection conn = DatabaseManager.getConnection();

         boolean var4;
         try {
            PreparedStatement pstmt = conn.prepareStatement(sql);

            try {
               pstmt.setString(1, playerName);
               var4 = pstmt.executeUpdate() > 0;
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

         return var4;
      } catch (SQLException e) {
         ServerBuddy.LOGGER.error("Could not remove " + playerName + " from the leaderboard!", e);
         return false;
      }
   }
}
