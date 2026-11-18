# Regole per la compattazione del codice (R8)

# WorkManager crea il worker dal nome della classe
-keep class it.furlan.nassync.SyncWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }

# Autenticazione Digest/Basic per il WebDAV
-keep class com.burgstaller.okhttp.** { *; }

# Dipendenze opzionali di OkHttp non presenti su Android
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**

# Messaggi d'errore leggibili
-keepattributes SourceFile,LineNumberTable
