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

public class CatchComboLeaderboardDisplayRepository {
   private CatchComboLeaderboardDisplayRepository() {
   }

   public static void createTable() {
      String sql = "CREATE TABLE IF NOT EXISTS catch_combo_leaderboard_display (display_id VARCHAR(36) PRIMARY KEY, dimension VARCHAR(64) NOT NULL, x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, yaw REAL NOT NULL, entity_uuid VARCHAR(36))";

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
         ServerBuddy.LOGGER.error("Could not create table catch_combo_leaderboard_display!", e);
      }

   }

   public static List<CatchComboLeaderboardDisplay> loadAll() {
      List<CatchComboLeaderboardDisplay> result = new ArrayList<>();
      String sql = "SELECT * FROM catch_combo_leaderboard_display";

      try {
         Connection conn = DatabaseManager.getConnection();

         try {
            Statement stmt = conn.createStatement();

            try {
               ResultSet rs = stmt.executeQuery(sql);

               try {
                  while(rs.next()) {
                     CatchComboLeaderboardDisplay display = new CatchComboLeaderboardDisplay();
                     display.displayId = rs.getString("display_id");
                     display.dimension = rs.getString("dimension");
                     display.x = rs.getDouble("x");
                     display.y = rs.getDouble("y");
                     display.z = rs.getDouble("z");
                     display.yaw = rs.getFloat("yaw");
                     String uuidStr = rs.getString("entity_uuid");
                     display.entityUuid = uuidStr == null ? null : UUID.fromString(uuidStr);
                     result.add(display);
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
         ServerBuddy.LOGGER.error("Could not load leaderboard displays!", e);
      }

      return result;
   }

   public static void save(CatchComboLeaderboardDisplay display) {
      String sql = DatabaseManager.isUsingMySQL() ? "INSERT INTO catch_combo_leaderboard_display (display_id, dimension, x, y, z, yaw, entity_uuid) VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE dimension = VALUES(dimension), x = VALUES(x), y = VALUES(y), z = VALUES(z), yaw = VALUES(yaw), entity_uuid = VALUES(entity_uuid)" : "INSERT INTO catch_combo_leaderboard_display (display_id, dimension, x, y, z, yaw, entity_uuid) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT(display_id) DO UPDATE SET dimension = excluded.dimension, x = excluded.x, y = excluded.y, z = excluded.z, yaw = excluded.yaw, entity_uuid = excluded.entity_uuid";

      try {
         Connection conn = DatabaseManager.getConnection();

         try {
            PreparedStatement pstmt = conn.prepareStatement(sql);

            try {
               pstmt.setString(1, display.displayId);
               pstmt.setString(2, display.dimension);
               pstmt.setDouble(3, display.x);
               pstmt.setDouble(4, display.y);
               pstmt.setDouble(5, display.z);
               pstmt.setFloat(6, display.yaw);
               pstmt.setString(7, display.entityUuid == null ? null : display.entityUuid.toString());
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
         ServerBuddy.LOGGER.error("Could not save leaderboard display '" + display.displayId + "' in the leaderboard!", e);
      }

   }
}
