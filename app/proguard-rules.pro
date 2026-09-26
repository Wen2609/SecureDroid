# 安卫安全助手 — 发布版混淆规则(Apache-2.0 开源)
-optimizationpasses 5
-allowaccessmodification

# Room 实体与 DAO 保留
-keep class com.armorlab.securedroid.data.** { *; }

# 保留注解与签名信息
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# 安全库相关
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.naming.**
