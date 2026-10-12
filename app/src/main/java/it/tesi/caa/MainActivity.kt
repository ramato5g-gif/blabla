package it.tesi.caa

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

private enum class Screen { COMMUNICATOR, MENU, EDITOR, STATS, SETTINGS, INFO, STORIES, STORY_READ, STORY_EDIT }

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    private lateinit var profiles: ProfileStore
    private lateinit var profile: Profile
    private lateinit var board: BoardStore
    private lateinit var settings: SettingsStore
    private lateinit var log: UsageLog
    private lateinit var voices: VoiceStore
    private lateinit var stories: StoryStore

    // Lettura parola per parola (storie)
    private var seqCallback: ((Int) -> Unit)? = null
    private var lastSeqId = ""

    private val pinned = mutableStateOf(false)
    /** Destinazione dell'immagine richiesta (galleria/fotocamera). */
    private var imageCallback: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        tts = TextToSpeech(this, this)
        profiles = ProfileStore(this)
        profile = profiles.current()
        val dir = ProfileStore.dirFor(this, profile.id)
        board = BoardStore(dir)
        settings = SettingsStore(dir)
        log = UsageLog(dir)
        voices = VoiceStore(this)
        stories = StoryStore(dir, board)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Accent)) { App() }
        }
    }

    override fun onResume() {
        super.onResume()
        pinned.value = isPinned()
    }

    @Composable
    private fun App() {
        var screen by remember { mutableStateOf(Screen.COMMUNICATOR) }
        var showProfiles by remember { mutableStateOf(false) }
        var showBackup by remember { mutableStateOf(false) }
        var showStartSession by remember { mutableStateOf(false) }
        var voiceFor by remember { mutableStateOf<Card?>(null) }
        var pendingVoice by remember { mutableStateOf<Card?>(null) }
        var reading by remember { mutableStateOf<Story?>(null) }
        var readingFrom by remember { mutableStateOf(Screen.STORIES) }

        // --- Immagini da galleria / fotocamera ---
        val captureUri = remember {
            val f = File(File(cacheDir, "photos").apply { mkdirs() }, "scatto.jpg")
            FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", f)
        }
        val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) deliverImage(uri)
        }
        val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) deliverImage(captureUri)
        }
        val requestImage: (ImageSource, (String) -> Unit) -> Unit = { src, cb ->
            imageCallback = cb
            when (src) {
                ImageSource.GALLERY -> pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                ImageSource.CAMERA -> takePhoto.launch(captureUri)
            }
        }

        // --- Voce: serve il permesso del microfono ---
        val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) voiceFor = pendingVoice else toast("Serve il permesso del microfono per registrare")
            pendingVoice = null
        }
        val openVoice: (Card) -> Unit = { c ->
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) voiceFor = c
            else { pendingVoice = c; micPermission.launch(Manifest.permission.RECORD_AUDIO) }
        }

        // --- Ripristino backup ---
        val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val n = runCatching { Backup.restore(this@MainActivity, uri) }.getOrDefault(0)
                if (n > 0) {
                    toast("Backup ripristinato ($n file). Riavvio…")
                    window.decorView.postDelayed({
                        startActivity(Intent.makeRestartActivityTask(componentName))
                        Runtime.getRuntime().exit(0)
                    }, 1200)
                } else toast("File di backup non valido")
            }
        }

        // Indietro: dal comunicatore non si esce se la protezione è attiva
        BackHandler(enabled = screen != Screen.COMMUNICATOR || settings.p.childLock) {
            screen = when (screen) {
                Screen.COMMUNICATOR -> Screen.COMMUNICATOR
                Screen.MENU, Screen.STORIES -> Screen.COMMUNICATOR
                Screen.STORY_READ -> readingFrom
                Screen.STORY_EDIT -> Screen.MENU
                else -> Screen.MENU
            }
        }

        Surface(Modifier.fillMaxSize(), color = Color.White) {
            Box(Modifier.fillMaxSize()) {
                when (screen) {
                    Screen.COMMUNICATOR -> CommunicatorScreen(
                        board = board, p = settings.p, log = log, say = ::say,
                        onOpenStories = { screen = Screen.STORIES },
                        onOpenMenu = { screen = Screen.MENU }
                    )

                    Screen.STORIES -> StoryLibraryScreen(
                        store = stories,
                        onOpen = { st ->
                            log.log(Ev.STORY, null, "Storie", st.title, st.pages.size)
                            reading = st; readingFrom = Screen.STORIES; screen = Screen.STORY_READ
                        },
                        onBack = { screen = Screen.COMMUNICATOR }
                    )

                    Screen.STORY_READ -> reading?.let { st ->
                        StoryReaderScreen(
                            story = st, board = board, p = settings.p, say = ::say,
                            speakSequence = ::speakSequence, stopSpeaking = ::stopSpeaking,
                            onClose = { screen = readingFrom }
                        )
                    }

                    Screen.STORY_EDIT -> StoryEditorScreen(
                        store = stories, board = board, p = settings.p, requestImage = requestImage,
                        onPreview = { st -> reading = st; readingFrom = Screen.STORY_EDIT; screen = Screen.STORY_READ },
                        onBack = { screen = Screen.MENU }
                    )

                    Screen.MENU -> TherapistMenu(
                        profileName = profile.name,
                        session = log.current,
                        pinned = pinned.value,
                        onCommunicator = { screen = Screen.COMMUNICATOR },
                        onEditor = { screen = Screen.EDITOR },
                        onStats = { screen = Screen.STATS },
                        onSettings = { screen = Screen.SETTINGS },
                        onStories = { screen = Screen.STORY_EDIT },
                        onProfiles = { showProfiles = true },
                        onStartSession = { showStartSession = true },
                        onEndSession = { log.endSession() },
                        onBackup = { showBackup = true },
                        onTogglePin = { togglePin() },
                        onInfo = { screen = Screen.INFO },
                    )

                    Screen.EDITOR -> EditorScreen(
                        board = board, p = settings.p, voices = voices,
                        requestImage = requestImage, openVoice = openVoice,
                        onBack = { screen = Screen.MENU }
                    )

                    Screen.STATS -> StatsScreen(
                        log = log, profileName = profile.name,
                        onExport = { name, csv -> exportText(name, csv) },
                        onBack = { screen = Screen.MENU }
                    )

                    Screen.SETTINGS -> SettingsScreen(
                        store = settings,
                        onTestVoice = { say(null, "Ciao, questa è la mia voce") },
                        onBack = { screen = Screen.MENU }
                    )

                    Screen.INFO -> InfoScreen { screen = Screen.MENU }
                }

                voiceFor?.let { c -> VoiceDialog(c, voices) { voiceFor = null } }

                if (showProfiles) ProfileDialog(
                    store = profiles,
                    onSelect = { pr -> profiles.currentId = pr.id; showProfiles = false; recreate() },
                    onDismiss = { showProfiles = false }
                )

                if (showStartSession) StartSessionDialog(
                    defaultName = "Sessione ${log.sessions.size + 1}",
                    onStart = { n, note -> log.startSession(n, note); showStartSession = false; screen = Screen.COMMUNICATOR },
                    onDismiss = { showStartSession = false }
                )

                if (showBackup) AlertDialog(
                    onDismissRequest = { showBackup = false },
                    title = { Text("Backup") },
                    text = {
                        Text(
                            "Il backup contiene profili, tessere, foto, voci, sessioni e statistiche. " +
                                    "Salvalo su Drive o inviatelo per non perdere i dati. Il ripristino sostituisce i dati attuali."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showBackup = false
                            runCatching { Backup.create(this@MainActivity) }
                                .onSuccess { shareFile(it, "application/zip", "Backup Bla Bla") }
                                .onFailure { toast("Backup non riuscito: ${it.message}") }
                        }) { Text("💾 Esporta backup") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showBackup = false
                            restorePicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        }) { Text("Ripristina…") }
                    }
                )
            }
        }
    }

    private fun deliverImage(uri: Uri) {
        val path = importPhoto(uri)
        if (path == null) toast("Impossibile leggere l'immagine") else imageCallback?.invoke(path)
        imageCallback = null
    }

    /** Voce registrata della tessera se presente, altrimenti sintesi vocale. */
    private fun say(card: Card?, text: String) {
        if (card != null && voices.has(card.id)) {
            if (ttsReady) tts.stop()
            if (voices.play(card.id)) return
        }
        voices.stopPlayback()
        if (ttsReady) {
            tts.setSpeechRate(settings.p.ttsRate)
            tts.setPitch(settings.p.ttsPitch)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "caa-${System.nanoTime()}")
        }
    }

    /** Legge le parole una alla volta, segnalando quale si sta pronunciando (-1 = fine). */
    private fun speakSequence(words: List<String>, onIndex: (Int) -> Unit) {
        voices.stopPlayback()
        if (!ttsReady || words.isEmpty()) return
        seqCallback = onIndex
        tts.setSpeechRate(settings.p.ttsRate * 0.85f)
        tts.setPitch(settings.p.ttsPitch)
        lastSeqId = "seq_${words.size - 1}"
        words.forEachIndexed { i, w ->
            tts.speak(w, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "seq_$i")
            tts.playSilentUtterance(250, TextToSpeech.QUEUE_ADD, "gap_$i")
        }
    }

    private fun stopSpeaking() {
        seqCallback = null
        if (ttsReady) tts.stop()
        voices.stopPlayback()
    }

    private fun isPinned(): Boolean =
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    /** Fissa l'app sullo schermo: il bambino non può uscire. */
    private fun togglePin() {
        try {
            if (isPinned()) stopLockTask() else startLockTask()
        } catch (e: Exception) {
            toast("Fissaggio non disponibile: ${e.message}")
        }
        window.decorView.postDelayed({ pinned.value = isPinned() }, 800)
    }

    /** Copia la foto nella memoria dell'app (max 800 px, raddrizzata). */
    private fun importPhoto(uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 800) sample *= 2
        val raw = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val orientation = contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val deg = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val bmp = if (deg == 0f) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(deg) }, true)
        val out = File(imagesDir(this), "foto_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        out.absolutePath
    }.getOrNull()

    private fun exportText(fileName: String, content: String) {
        try {
            val f = File(File(cacheDir, "export").apply { mkdirs() }, fileName)
            f.writeText(content)
            shareFile(f, "text/csv", "Esporta dati")
        } catch (e: Exception) {
            toast("Esportazione non riuscita: ${e.message}")
        }
    }

    private fun shareFile(file: File, mime: String, title: String) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, title))
        } catch (e: Exception) {
            toast("Condivisione non riuscita: ${e.message}")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts.setLanguage(Locale.ITALIAN)
            ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    val id = utteranceId ?: return
                    if (id.startsWith("seq_")) {
                        val i = id.removePrefix("seq_").toIntOrNull() ?: return
                        runOnUiThread { seqCallback?.invoke(i) }
                    }
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == lastSeqId) runOnUiThread { seqCallback?.invoke(-1) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    runOnUiThread { seqCallback?.invoke(-1) }
                }
            })
        }
    }

    override fun onDestroy() {
        tts.stop(); tts.shutdown()
        voices.stopPlayback(); voices.stopRecording()
        super.onDestroy()
    }
}
