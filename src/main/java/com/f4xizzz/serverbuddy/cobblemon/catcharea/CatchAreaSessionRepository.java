package com.f4xizzz.serverbuddy.cobblemon.catcharea;

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

public class CatchAreaSessionRepository {
   private CatchAreaSessionRepository() {
   }

   public static void createTable() {
      String sql = "CREATE TABLE IF NOT EXISTS catch_area_session (player_uuid VARCHAR(36) PRIMARY KEY, area_name VARCHAR(64) NOT NULL, base_species_id VARCHAR(64) NOT NULL, expires_at BIGINT NOT NULL, last_spawn_at BIGINT NOT NULL, offline_since BIGINT, away_since BIGINT)";

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
         ServerBuddy.LOGGER.error("Could not create table catch_area_session!", e);
      }

   }

   public static List<CatchAreaSession> loadAll() {
      List<CatchAreaSession> result = new ArrayList<>();
      String sql = "SELECT * FROM catch_area_session";

      try {
         Connection conn = DatabaseManager.getConnection();

         try {
            Statement stmt = conn.createStatement();

            try {
               ResultSet rs = stmt.executeQuery(sql);

               try {
                  while(rs.next()) {
                     CatchAreaSession session = new CatchAreaSession();
                     session.playerId = UUID.fromString(rs.getString("player_uuid"));
                     session.areaName = rs.getString("area_name");
                     session.baseSpeciesId = rs.getString("base_species_id");
                     session.expiresAtMillis = rs.getLong("expires_at");
                     session.lastSpawnAtMillis = rs.getLong("last_spawn_at");
                     long offline = rs.getLong("offline_since");
                     session.offlineSinceMillis = rs.wasNull() ? null : offline;
                     long away = rs.getLong("away_since");
                     session.awayFromAreaSinceMillis = rs.wasNull() ? null : away;
                     result.add(session);
                  }
               } catch (Throwable var13) {
                  if (rs != null) {
                     try {
                        rs.close();
                     } catch (Throwable var12) {
                        var13.addSuppressed(var12);
                     }
                  }

                  throw var13;
               }

               if (rs != null) {
                  rs.close();
               }
            } catch (Throwable var14) {
               if (stmt != null) {
                  try {
                     stmt.close();
                  } catch (Throwable var11) {
                     var14.addSuppressed(var11);
                  }
               }

               throw var14;
            }

            if (stmt != null) {
               stmt.close();
            }
         } catch (Throwable var15) {
            if (conn != null) {
               try {
                  conn.close();
               } catch (Throwable var10) {
                  var15.addSuppressed(var10);
               }
            }

            throw var15;
         }

         if (conn != null) {
            conn.close();
         }
      } catch (SQLException e) {
         ServerBuddy.LOGGER.error("Error loading the catch area sessions from the database!", e);
      }

      return result;
   }

   public static void saveAsync(CatchAreaSession session) {
      CompletableFuture.runAsync(() -> {
         String sql = DatabaseManager.isUsingMySQL() ? "INSERT INTO catch_area_session (player_uuid, area_name, base_species_id, expires_at, last_spawn_at, offline_since, away_since) VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE area_name = VALUES(area_name), base_species_id = VALUES(base_species_id), expires_at = VALUES(expires_at), last_spawn_at = VALUES(last_spawn_at), offline_since = VALUES(offline_since), away_since = VALUES(away_since)" : "INSERT INTO catch_area_session (player_uuid, area_name, base_species_id, expires_at, last_spawn_at, offline_since, away_since) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT(player_uuid) DO UPDATE SET area_name = excluded.area_name, base_species_id = excluded.base_species_id, expires_at = excluded.expires_at, last_spawn_at = excluded.last_spawn_at, offline_since = excluded.offline_since, away_since = excluded.away_since";

         try {
            Connection conn = DatabaseManager.getConnection();

            try {
               PreparedStatement pstmt = conn.prepareStatement(sql);

               try {
                  pstmt.setString(1, session.playerId.toString());
                  pstmt.setString(2, session.areaName);
                  pstmt.setString(3, session.baseSpeciesId);
                  pstmt.setLong(4, session.expiresAtMillis);
                  pstmt.setLong(5, session.lastSpawnAtMillis);
                  if (session.offlineSinceMillis != null) {
                     pstmt.setLong(6, session.offlineSinceMillis);
                  } else {
                     pstmt.setNull(6, -5);
                  }

                  if (session.awayFromAreaSinceMillis != null) {
                     pstmt.setLong(7, session.awayFromAreaSinceMillis);
                  } else {
                     pstmt.setNull(7, -5);
                  }

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
            ServerBuddy.LOGGER.error("Error saving the catch area session of " + String.valueOf(session.playerId) + "!", e);
         }

      });
   }

   public static void deleteAsync(UUID playerId) {
      CompletableFuture.runAsync(() -> {
         String sql = "DELETE FROM catch_area_session WHERE player_uuid = ?";

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
            ServerBuddy.LOGGER.error("Error removing the catch area session of " + String.valueOf(playerId) + "!", e);
         }

      });
   }
}
