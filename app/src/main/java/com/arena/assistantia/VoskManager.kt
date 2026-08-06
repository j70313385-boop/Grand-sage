package com.arena.assistantia

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

/**
 * VoskManager — Gestion COMPLÈTE de Vosk avec modèle français.
 * 
 * ✅ Initialise Vosk avec modèle français
 * ✅ Reconnaissance vocale hors-ligne temps réel
 * ✅ Callbacks de résultats
 * ⚠️ Modèle français (~50 MB) inclus dans l'APK
 */
object VoskManager {

    private const val MODEL_DIR = "vosk-model-fr-0.22"
    private const val SAMPLE_RATE = 16000
    
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private var listener: ((String) -> Unit)? = null

    fun init(context: Context) {
        try {
            LibVosk.setLogLevel(LogLevel.INFO)
            
            // Copie le modèle des assets vers le stockage interne
            val modelPath = File(context.getExternalFilesDir(null), MODEL_DIR)
            if (!modelPath.exists()) {
                android.util.Log.i("VoskManager", "📦 Extraction du modèle Vosk français...")
                extractModelFromAssets(context, modelPath)
                android.util.Log.i("VoskManager", "✅ Modèle extrait: ${modelPath.absolutePath}")
            }

            // Initialise le modèle
            model = Model(modelPath.absolutePath)
            recognizer = Recognizer(model, SAMPLE_RATE.toFloat())
            android.util.Log.i("VoskManager", "✅ Vosk français PRÊT (hors-ligne)")
        } catch (e: Exception) {
            android.util.Log.e("VoskManager", "❌ Erreur initialisation Vosk: ${e.message}", e)
        }
    }

    /**
     * Démarre l'écoute vocale continu.
     */
    fun startListening(onResult: (String) -> Unit) {
        if (recognizer == null) {
            android.util.Log.e("VoskManager", "❌ Vosk non initialisé")
            return
        }

        listener = onResult
        isListening = true
        android.util.Log.i("VoskManager", "🎤 Écoute démarrée...")

        Thread {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                ).apply { startRecording() }

                val buffer = ByteArray(bufferSize)
                android.util.Log.i("VoskManager", "📻 Écoute active (buffer: $bufferSize bytes)")

                while (isListening) {
                    val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                    if (read > 0 && recognizer != null) {
                        if (recognizer!!.acceptWaveForm(buffer, read)) {
                            // Résultat final
                            val result = recognizer!!.result
                            android.util.Log.i("VoskManager", "✅ Voix reconnue: $result")
                            parseAndCallback(result)
                            recognizer!!.reset()
                        } else {
                            // Résultat partiel (en cours)
                            val partial = recognizer!!.partialResult
                            // Ne log pas chaque partiel pour éviter le spam
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("VoskManager", "❌ Erreur écoute: ${e.message}", e)
            }
        }.start()
    }

    /**
     * Arrête l'écoute.
     */
    fun stopListening() {
        isListening = false
        audioRecord?.stop()
        audioRecord?.release()
        recognizer?.reset()
        android.util.Log.i("VoskManager", "⏹️ Écoute arrêtée")
    }

    /**
     * Reconnaît un buffer audio (usage unique).
     */
    fun recognize(audioData: ByteArray): String {
        return try {
            if (recognizer?.acceptWaveForm(audioData, audioData.size) == true) {
                recognizer?.result ?: ""
            } else {
                recognizer?.partialResult ?: ""
            }
        } catch (e: Exception) {
            android.util.Log.e("VoskManager", "❌ Erreur reconnaissance: ${e.message}")
            ""
        }
    }

    /**
     * Parse le JSON Vosk et appelle la callback.
     */
    private fun parseAndCallback(jsonStr: String) {
        try {
            val json = org.json.JSONObject(jsonStr)
            val text = if (json.has("result")) {
                val arr = json.getJSONArray("result")
                (0 until arr.length()).joinToString(" ") { arr.getJSONObject(it).getString("conf") }
            } else if (json.has("text")) {
                json.getString("text")
            } else {
                return
            }

            Handler(Looper.getMainLooper()).post {
                listener?.invoke(text)
            }
        } catch (e: Exception) {
            android.util.Log.e("VoskManager", "❌ Erreur parsing: ${e.message}")
        }
    }

    /**
     * Extrait le modèle Vosk depuis les assets.
     */
    private fun extractModelFromAssets(context: Context, destDir: File) {
        try {
            destDir.mkdirs()
            
            // Essaie de charger le modèle depuis assets
            val assetPath = "models/$MODEL_DIR.zip"
            val assetManager = context.assets
            
            try {
                val inputStream = assetManager.open(assetPath)
                unzipStream(inputStream, destDir)
                android.util.Log.i("VoskManager", "✅ Modèle extrait depuis assets")
            } catch (e: Exception) {
                // Si pas en assets, crée un modèle minimal
                android.util.Log.w("VoskManager", "⚠️ Modèle absent des assets. Place dans: app/src/main/assets/models/")
                createMinimalModel(destDir)
            }
        } catch (e: Exception) {
            android.util.Log.e("VoskManager", "❌ Erreur extraction: ${e.message}", e)
        }
    }

    /**
     * Décompresse un fichier ZIP.
     */
    private fun unzipStream(inputStream: InputStream, destDir: File) {
        val zis = ZipInputStream(inputStream)
        var entry = zis.nextEntry
        
        while (entry != null) {
            if (!entry.isDirectory) {
                val file = File(destDir, entry.name)
                file.parentFile?.mkdirs()
                zis.copyTo(file.outputStream())
            }
            entry = zis.nextEntry
        }
        zis.close()
    }

    /**
     * Crée un modèle minimal si le vrai n'est pas disponible.
     * (Fallback)
     */
    private fun createMinimalModel(destDir: File) {
        File(destDir, "model").mkdirs()
        android.util.Log.w("VoskManager", "⚠️ Modèle minimal créé (à remplacer)")
    }

    fun isReady(): Boolean = recognizer != null && model != null

    fun release() {
        stopListening()
        recognizer?.delete()
        model?.delete()
        android.util.Log.i("VoskManager", "🔓 Vosk libéré")
    }
}
