# ServerBuddy: rules that rename (obfuscate) the classes and methods of each plan's jar. No shrinking and no optimizing: the only
# goal is that the jar can not be read like the source. (Preverifying stays ON: Java 21 class files need their StackMapTable.) The per-plan parts (input, output, libraries, mapping) are written by
# tiers.gradle into build/tiers/<plan>/proguard.pro, which includes this file.
#
# Rule of thumb: a name stays when something reads it BY NAME at run time (Fabric, Mixin, Gson, kotlin-reflect, Class.forName).

-dontshrink
-dontoptimize
-dontnote
-dontusemixedcaseclassnames
-allowaccessmodification

# The jar keeps its directory entries: the modules copy their default config folders out of it through the class loader.
-keepdirectories assets/**

# All renamed classes land in one package, so the module structure of the jar does not show.
-repackageclasses 'com.f4xizzz.serverbuddy.x'

# Stack traces keep their line numbers (the mapping file turns the names back); the source file name is hidden.
-keepattributes Signature,InnerClasses,EnclosingMethod,Exceptions,RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations,AnnotationDefault,Record,PermittedSubclasses,NestHost,NestMembers,SourceFile,LineNumberTable
-renamesourcefileattribute SB

# --- Fabric and Mixin read these by name ---------------------------------------------------------------------------------
-keep class com.f4xizzz.serverbuddy.ServerBuddy { *; }
-keep class com.f4xizzz.serverbuddy.mixin.** { *; }
-keep class com.f4xizzz.serverbuddy.crates.mixins.** { *; }
-keep class com.f4xizzz.serverbuddy.crates.utils.CratesMixinPlugin { *; }

# --- the modules are created by class name (see Modules.kt) --------------------------------------------------------------
-keep class * implements com.f4xizzz.serverbuddy.core.SbModule { <init>(); }

# --- the dashboard providers are loaded by name and registered through INSTANCE ------------------------------------------
-keep class com.f4xizzz.serverbuddy.dashboard.BasicProviders { public static ** INSTANCE; public void register(); }
-keep class com.f4xizzz.serverbuddy.dashboard.PlusProviders { public static ** INSTANCE; public void register(); }
-keep class com.f4xizzz.serverbuddy.dashboard.ProProviders { public static ** INSTANCE; public void register(); }

# --- Gson, the config documentation and the databases read the field names ----------------------------------------------
-keepclassmembers class com.f4xizzz.serverbuddy.** { <fields>; }
-keepclassmembers enum com.f4xizzz.serverbuddy.** { *; }
-keepclassmembers class * extends java.lang.Enum {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep @interface com.f4xizzz.serverbuddy.** { *; }

# --- kotlin-reflect reads these classes by property name ----------------------------------------------------------------
-keep class com.f4xizzz.serverbuddy.**.Lang { *; }
-keep class * implements com.f4xizzz.serverbuddy.**.IEconomyService { *; }

# --- the Plan (analytics) addon is read through its annotations ---------------------------------------------------------
-keep class com.f4xizzz.serverbuddy.shop.addons.plan.** { *; }

# --- classes of the higher plans are not in the lower plans' jars (their use is guarded at run time) --------------------
-dontwarn com.f4xizzz.serverbuddy.**
