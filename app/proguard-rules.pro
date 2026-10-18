# Reglas de ProGuard/R8 para Medusa

# Retrofit & OkHttp
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Kotlinx Serialization
-keepattributes *Annotation*, ElementValueAttribute
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep class kotlinx.serialization.** { *; }
-keep class tv.medusa.app.data.remote.** { *; }

# Room Database Entities, DAOs and Generated Impl Classes
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class tv.medusa.app.data.local.db.** { *; }
-keep class **_Impl { *; }

# Media3 / ExoPlayer
-dontwarn androidx.media3.**
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer {
    public <init>(...);
}
-keep class androidx.media3.decoder.ffmpeg.FfmpegLibrary {
    *;
}

# Coil Image Loader
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
# Kotlinx Serialization: reglas oficiales para serializadores generados.
# (Los DTOs viven en tv.medusa.app.data.remote.**, ya protegido arriba,
#  pero estas reglas cubren cualquier @Serializable fuera de ese paquete.)
# ---------------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
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
