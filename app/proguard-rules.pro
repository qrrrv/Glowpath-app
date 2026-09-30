# ── Glowpath R8 / ProGuard rules ──────────────────────────────────────────

# Читаемые стектрейсы при крашах в release
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
-renamesourcefileattribute SourceFile

# NanoHTTPD (локальный сервер now-playing для Hikka/ExteraGram)
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# Jsoup ссылается на необязательные аннотации/библиотеки
-dontwarn org.jspecify.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.re2j.**

# Lottie / Coil / OkHttp / Okio — свои consumer-rules подхватываются автоматически,
# здесь только подавляем предупреждения об опциональных зависимостях
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Material Color Utilities (HCT) лежит в исходниках проекта и вызывается статически
-keep class com.google.material.color.** { *; }
