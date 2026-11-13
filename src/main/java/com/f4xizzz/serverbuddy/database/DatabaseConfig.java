package com.f4xizzz.serverbuddy.database;

public class DatabaseConfig {
   public boolean useMySQL = false;
   public MySQLSettings mysql = new MySQLSettings();
   public SQLiteSettings sqlite = new SQLiteSettings();

   public static class MySQLSettings {
      public String host = "localhost";
      public int port = 3306;
      public String database = "serverbuddy";
      public String username = "root";
      public String password = "";
      public boolean useSSL = false;
      public int poolSize = 10;
   }

   public static class SQLiteSettings {
      public String fileName = "serverbuddy.db";
   }
}
