package com.f4xizzz.serverbuddy.database;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.ModPaths;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {
   private static HikariDataSource dataSource;
   private static boolean usingMySQL;

   private DatabaseManager() {
   }

   public static void init() {
      DatabaseConfigManager.loadConfig();
      DatabaseConfig config = DatabaseConfigManager.getConfig();
      if (config.useMySQL) {
         if (tryStart(buildMySQLConfig(config))) {
            usingMySQL = true;
            ServerBuddy.LOGGER.info("Database: connected to MySQL ({}:{}/{}).", new Object[]{config.mysql.host, config.mysql.port, config.mysql.database});
            return;
         }

         ServerBuddy.LOGGER.error("Could not connect to the configured MySQL! Falling back to the default SQLite.");
      }

      usingMySQL = false;
      if (!tryStart(buildSQLiteConfig(config))) {
         throw new IllegalStateException("Could not start even the default SQLite database. Check the permissions of the config folder.");
      } else {
         ServerBuddy.LOGGER.info("Database: using SQLite ({}).", config.sqlite.fileName);
      }
   }

   private static boolean tryStart(HikariConfig hikariConfig) {
      try {
         HikariDataSource candidate = new HikariDataSource(hikariConfig);
         Connection connection = candidate.getConnection();

         try {
            connection.isValid(2);
         } catch (Throwable var6) {
            if (connection != null) {
               try {
                  connection.close();
               } catch (Throwable var5) {
                  var6.addSuppressed(var5);
               }
            }

            throw var6;
         }

         if (connection != null) {
            connection.close();
         }

         if (dataSource != null) {
            dataSource.close();
         }

         dataSource = candidate;
         return true;
      } catch (Exception e) {
         ServerBuddy.LOGGER.error("Failed to start the database connection pool.", e);
         return false;
      }
   }

   private static HikariConfig buildMySQLConfig(DatabaseConfig config) {
      HikariConfig hikariConfig = new HikariConfig();
      String url = "jdbc:mysql://" + config.mysql.host + ":" + config.mysql.port + "/" + config.mysql.database + "?useSSL=" + config.mysql.useSSL + "&autoReconnect=true&characterEncoding=utf8";
      hikariConfig.setJdbcUrl(url);
      hikariConfig.setUsername(config.mysql.username);
      hikariConfig.setPassword(config.mysql.password);
      hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
      hikariConfig.setMaximumPoolSize(Math.max(1, config.mysql.poolSize));
      hikariConfig.setPoolName("serverbuddy-MySQL");
      return hikariConfig;
   }

   private static HikariConfig buildSQLiteConfig(DatabaseConfig config) {
      File dir = ModPaths.subDir("Database");
      if (!dir.exists()) {
         dir.mkdirs();
      }

      File dbFile = new File(dir, config.sqlite.fileName);
      HikariConfig hikariConfig = new HikariConfig();
      hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
      hikariConfig.setDriverClassName("org.sqlite.JDBC");
      hikariConfig.setMaximumPoolSize(1);
      hikariConfig.setPoolName("serverbuddy-SQLite");
      return hikariConfig;
   }

   public static Connection getConnection() throws SQLException {
      if (dataSource == null) {
         throw new IllegalStateException("DatabaseManager.init() has not been called yet.");
      } else {
         return dataSource.getConnection();
      }
   }

   public static boolean isUsingMySQL() {
      return usingMySQL;
   }

   public static void shutdown() {
      if (dataSource != null) {
         dataSource.close();
         dataSource = null;
      }

   }
}
