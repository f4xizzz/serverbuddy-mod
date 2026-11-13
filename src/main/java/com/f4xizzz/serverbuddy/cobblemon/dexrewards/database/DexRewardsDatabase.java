package com.f4xizzz.serverbuddy.cobblemon.dexrewards.database;

import com.f4xizzz.serverbuddy.cobblemon.dexrewards.DexRewards;
import com.f4xizzz.serverbuddy.cobblemon.dexrewards.datatypes.PlayerData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;

public class DexRewardsDatabase {
   private static final Gson GSON = new Gson();
   private static boolean isMySQL = false;
   private static String dbUrl;
   private static String dbUser;
   private static String dbPassword;

   public static void init(boolean useMySQL, String host, int port, String database, String user, String pass) {
      isMySQL = useMySQL;
      dbUser = user;
      dbPassword = pass;
      if (isMySQL) {
         dbUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + "?autoReconnect=true&useSSL=false";
      } else {
         File dir = FabricLoader.getInstance().getConfigDir().resolve("ServerBuddy/DexRewards").toFile();
         if (!dir.exists()) {
            dir.mkdirs();
         }

         File dbFile = new File(dir, "players.db");
         dbUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
      }

      createTable();
   }

   private static Connection getConnection() throws SQLException {
      return isMySQL ? DriverManager.getConnection(dbUrl, dbUser, dbPassword) : DriverManager.getConnection(dbUrl);
   }

   private static void createTable() {
      String sql = "CREATE TABLE IF NOT EXISTS dexrewards_players (uuid VARCHAR(36) PRIMARY KEY, username VARCHAR(16) NOT NULL, caught_count INT NOT NULL, claimed_rewards TEXT NOT NULL, claimable_rewards TEXT NOT NULL)";

      try {
         Connection conn = getConnection();

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
         DexRewards.INSTANCE.logInfo("[DexRewardsDatabase] Could not create database table: " + e.getMessage());
      }

   }

   public static PlayerData loadPlayer(UUID uuid, String username) {
      String sql = "SELECT * FROM dexrewards_players WHERE uuid = ?";

      try {
         Connection conn = getConnection();

         PlayerData var11;
         label128: {
            try {
               PreparedStatement pstmt;
               label118: {
                  pstmt = conn.prepareStatement(sql);

                  try {
                     pstmt.setString(1, uuid.toString());
                     ResultSet rs = pstmt.executeQuery();

                     label98: {
                        try {
                           if (rs.next()) {
                              String dbUsername = rs.getString("username");
                              int caughtCount = rs.getInt("caught_count");
                              Type listType = (new TypeToken<List<String>>() {
                              }).getType();
                              List<String> claimedRewards = (List)GSON.fromJson(rs.getString("claimed_rewards"), listType);
                              List<String> claimableRewards = (List)GSON.fromJson(rs.getString("claimable_rewards"), listType);
                              if (claimedRewards == null) {
                                 claimedRewards = new ArrayList<>();
                              }

                              if (claimableRewards == null) {
                                 claimableRewards = new ArrayList<>();
                              }

                              if (!dbUsername.equals(username)) {
                                 dbUsername = username;
                              }

                              var11 = new PlayerData(uuid, dbUsername, caughtCount, claimedRewards, claimableRewards);
                              break label98;
                           }
                        } catch (Throwable var15) {
                           if (rs != null) {
                              try {
                                 rs.close();
                              } catch (Throwable var14) {
                                 var15.addSuppressed(var14);
                              }
                           }

                           throw var15;
                        }

                        if (rs != null) {
                           rs.close();
                        }
                        break label118;
                     }

                     if (rs != null) {
                        rs.close();
                     }
                  } catch (Throwable var16) {
                     if (pstmt != null) {
                        try {
                           pstmt.close();
                        } catch (Throwable var13) {
                           var16.addSuppressed(var13);
                        }
                     }

                     throw var16;
                  }

                  if (pstmt != null) {
                     pstmt.close();
                  }
                  break label128;
               }

               if (pstmt != null) {
                  pstmt.close();
               }
            } catch (Throwable var17) {
               if (conn != null) {
                  try {
                     conn.close();
                  } catch (Throwable var12) {
                     var17.addSuppressed(var12);
                  }
               }

               throw var17;
            }

            if (conn != null) {
               conn.close();
            }

            return new PlayerData(uuid, username, 0, new ArrayList<>(), new ArrayList<>());
         }

         if (conn != null) {
            conn.close();
         }

         return var11;
      } catch (SQLException e) {
         DexRewards.INSTANCE.logInfo("[DexRewardsDatabase] Error loading data of player " + username + ": " + e.getMessage());
         return new PlayerData(uuid, username, 0, new ArrayList<>(), new ArrayList<>());
      }
   }

   public static void savePlayerAsync(PlayerData data) {
      CompletableFuture.runAsync(() -> {
         String sql = "REPLACE INTO dexrewards_players (uuid, username, caught_count, claimed_rewards, claimable_rewards) VALUES (?, ?, ?, ?, ?)";
         if (!isMySQL) {
            sql = "INSERT OR REPLACE INTO dexrewards_players (uuid, username, caught_count, claimed_rewards, claimable_rewards) VALUES (?, ?, ?, ?, ?)";
         }

         try {
            Connection conn = getConnection();

            try {
               PreparedStatement pstmt = conn.prepareStatement(sql);

               try {
                  pstmt.setString(1, data.uuid.toString());
                  pstmt.setString(2, data.username);
                  pstmt.setInt(3, data.caught_count);
                  pstmt.setString(4, GSON.toJson(data.claimed_rewards));
                  pstmt.setString(5, GSON.toJson(data.claimable_rewards));
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
            String var10001 = data.username;
            DexRewards.INSTANCE.logInfo("[DexRewardsDatabase] Error saving data of player " + var10001 + ": " + e.getMessage());
         }

      });
   }
}
