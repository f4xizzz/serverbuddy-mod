package com.f4xizzz.serverbuddy.cobblemon.catcharea;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public class CatchAreaRegion {
   public String name;
   public String dimension;
   public int pos1X;
   public int pos1Y;
   public int pos1Z;
   public int pos2X;
   public int pos2Y;
   public int pos2Z;
   public boolean enabled = true;

   public CatchAreaRegion() {
   }

   public CatchAreaRegion(String name, String dimension, BlockPos pos1, BlockPos pos2) {
      this.name = name;
      this.dimension = dimension;
      this.pos1X = pos1.getX();
      this.pos1Y = pos1.getY();
      this.pos1Z = pos1.getZ();
      this.pos2X = pos2.getX();
      this.pos2Y = pos2.getY();
      this.pos2Z = pos2.getZ();
   }

   public int getMinX() {
      return Math.min(this.pos1X, this.pos2X);
   }

   public int getMinY() {
      return Math.min(this.pos1Y, this.pos2Y);
   }

   public int getMinZ() {
      return Math.min(this.pos1Z, this.pos2Z);
   }

   public int getMaxX() {
      return Math.max(this.pos1X, this.pos2X);
   }

   public int getMaxY() {
      return Math.max(this.pos1Y, this.pos2Y);
   }

   public int getMaxZ() {
      return Math.max(this.pos1Z, this.pos2Z);
   }

   public boolean contains(BlockPos pos) {
      return pos.getX() >= this.getMinX() && pos.getX() <= this.getMaxX() && pos.getY() >= this.getMinY() && pos.getY() <= this.getMaxY() && pos.getZ() >= this.getMinZ() && pos.getZ() <= this.getMaxZ();
   }

   public Vec3 getCenter() {
      return new Vec3((double)(this.getMinX() + this.getMaxX()) / 2.0 + 0.5, (double)(this.getMinY() + this.getMaxY()) / 2.0, (double)(this.getMinZ() + this.getMaxZ()) / 2.0 + 0.5);
   }

   public ServerLevel resolveWorld(MinecraftServer server) {
      return server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(this.dimension)));
   }

   public void ensureMinHeight(int minHeight) {
      int floor = this.getMinY();
      int ceiling = this.getMaxY();
      if (ceiling - floor < minHeight) {
         int newCeiling = floor + minHeight;
         if (this.pos1Y >= this.pos2Y) {
            this.pos1Y = newCeiling;
         } else {
            this.pos2Y = newCeiling;
         }

      }
   }
}
