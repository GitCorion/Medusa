# Reglas de ProGuard/R8 para Medusa

# ---------------------------------------------------------------------------
# Retrofit & OkHttp
# ---------------------------------------------------------------------------
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ---------------------------------------------------------------------------
# Kotlinx Serialization
# Preserva todas las clases de datos serializables, constructores, campos
# @SerialName y sus Serializers / Companions generados contra R8 full mode.
# ---------------------------------------------------------------------------
-keepattributes *Annotation*, ElementValueAttribute, RuntimeVisibleAnnotations, AnnotationDefault
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    ** Companion;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep class kotlinx.serialization.** { *; }

# ---------------------------------------------------------------------------
# Gson (prevención de crashes si se usa en dependencias transitivas o modelos)
# ---------------------------------------------------------------------------
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.Expose <fields>;
}

# ---------------------------------------------------------------------------
# Modelos de datos, DTOs y Servicios de Medusa
# ---------------------------------------------------------------------------
-keep class tv.medusa.app.data.remote.** { *; }
-keep interface tv.medusa.app.data.remote.** { *; }
-keep class tv.medusa.app.data.model.** { *; }
-keep class tv.medusa.app.data.demo.** { *; }
-keep class tv.medusa.app.domain.model.** { *; }

# ---------------------------------------------------------------------------
# Room Database Entities, DAOs and Generated Impl Classes
# ---------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class tv.medusa.app.data.local.db.** { *; }
-keep class **_Impl { *; }

# ---------------------------------------------------------------------------
# Media3 / ExoPlayer / MediaSession
# ---------------------------------------------------------------------------
-dontwarn androidx.media3.**
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer {
    public <init>(...);
}
-keep class androidx.media3.decoder.ffmpeg.FfmpegLibrary {
    *;
}

# ---------------------------------------------------------------------------
# Coil Image Loader
# ---------------------------------------------------------------------------
-dontwarn coil.**

# ---------------------------------------------------------------------------
# WorkManager: los InputMerger se instancian por reflexión a partir del nombre
# de clase persistido. La regla consumer no preserva el constructor sin argumentos.
# ---------------------------------------------------------------------------
-keep class * extends androidx.work.InputMerger {
    <init>();
}

# ---------------------------------------------------------------------------
# ViewModels: se instancian por reflexión (AndroidViewModel(Application)),
# por lo que sus constructores deben conservarse aunque no se llamen de forma directa.
# ---------------------------------------------------------------------------
-keepclassmembers,allowobfuscation class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ---------------------------------------------------------------------------
# Enums: preservar methods values() y valueOf() contra optimizaciones agresivas
# ---------------------------------------------------------------------------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------------------------------------------------------------------------
# Logging: eliminar Log.d() y Log.v() del bytecode en release para no filtrar
# información de debug (IDs de items, tokens parciales, metadata) en logcat.
# Se conservan Log.e(), Log.w() y Log.i() para diagnóstico vía Crashlytics.
# ---------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# OkHttp HttpLoggingInterceptor: eliminar logs de red en release.
-assumenosideeffects class okhttp3.logging.HttpLoggingInterceptor {
    public *** log(...);
}
