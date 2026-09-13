# ---- Shizuku UserService（反射实例化，必须保类名与构造）----
-keep class com.feringsapp.biliforge.core.shizuku.RemoteFsService { *; }
-keepclassmembers class com.feringsapp.biliforge.core.shizuku.RemoteFsService { <init>(...); }

# ---- AIDL 生成类 ----
-keep class com.feringsapp.biliforge.core.shizuku.IRemoteService { *; }
-keep class com.feringsapp.biliforge.core.shizuku.IRemoteService$* { *; }

# ---- Shizuku / Sui ----
-keep class rikka.shizuku.** { *; }
-keep class rikka.sui.** { *; }

# 保留 @Keep 注解语义
-keep @androidx.annotation.Keep class * { *; }
