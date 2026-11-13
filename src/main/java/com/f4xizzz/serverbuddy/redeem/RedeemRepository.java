package com.f4xizzz.serverbuddy.redeem;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.database.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class RedeemRepository {
   private RedeemRepository() {
   }

   public static void createTable() {
      String sql = "CREATE TABLE IF NOT EXISTS redeem_claims (player_uuid VARCHAR(36) NOT NULL, code VARCHAR(64) NOT NULL, redeemed_at BIGINT NOT NULL, PRIMARY KEY (player_uuid, code))";

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
         ServerBuddy.LOGGER.error("Could not create table redeem_claims!", e);
      }

   }

   public static Map<UUID, Set<String>> loadAll() {
      Map<UUID, Set<String>> result = new HashMap<>();
      String sql = "SELECT player_uuid, code FROM redeem_claims";

      try {
         Connection conn = DatabaseManager.getConnection();

         try {
            Statement stmt = conn.createStatement();

            try {
               ResultSet rs = stmt.executeQuery(sql);

               try {
                  while(rs.next()) {
                     UUID playerId = UUID.fromString(rs.getString("player_uuid"));
                     String code = rs.getString("code");
                     ((Set)result.computeIfAbsent(playerId, (id) -> new HashSet<>())).add(code);
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
         ServerBuddy.LOGGER.error("Could not load redeem_claims from the database!", e);
      }

      return result;
   }

   public static void saveAsync(UUID playerId, String code) {
      CompletableFuture.runAsync(() -> {
         String sql = "INSERT INTO redeem_claims (player_uuid, code, redeemed_at) VALUES (?, ?, ?)";

         try {
            Connection conn = DatabaseManager.getConnection();

            try {
               PreparedStatement pstmt = conn.prepareStatement(sql);

               try {
                  pstmt.setString(1, playerId.toString());
                  pstmt.setString(2, code);
                  pstmt.setLong(3, Instant.now().getEpochSecond());
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
            ServerBuddy.LOGGER.error("Could not save redemption claim (" + String.valueOf(playerId) + ", " + code + ")!", e);
         }

      });
   }

   public static void deleteAsync(UUID playerId, String code) {
      CompletableFuture.runAsync(() -> {
         String sql = "DELETE FROM redeem_claims WHERE player_uuid = ? AND code = ?";

         try {
            Connection conn = DatabaseManager.getConnection();

            try {
               PreparedStatement pstmt = conn.prepareStatement(sql);

               try {
                  pstmt.setString(1, playerId.toString());
                  pstmt.setString(2, code);
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
            ServerBuddy.LOGGER.error("Could not remove redemption claim (" + String.valueOf(playerId) + ", " + code + ")!", e);
         }

      });
   }
}
