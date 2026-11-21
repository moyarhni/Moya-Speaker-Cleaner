package com.moya.speakercleaner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class Mode { WATER, DUST, TEST }
private enum class Channel { BOTH, LEFT, RIGHT }
private enum class Language { EN, FA }

class MainActivity : ComponentActivity() {
    private lateinit var engine: ToneEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = ToneEngine(this)
        setContent { CleanerApp(engine) }
    }

    override fun onStop() {
        engine.stop()
        super.onStop()
    }
}

@Composable
private fun CleanerApp(engine: ToneEngine) {
    var language by remember { mutableStateOf(Language.EN) }
    var settingsPage by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(Mode.WATER) }
    var channel by remember { mutableStateOf(Channel.BOTH) }
    var frequency by remember { mutableFloatStateOf(165f) }
    var seconds by remember { mutableIntStateOf(15) }
    var intensity by remember { mutableFloatStateOf(0.25f) }
    var playing by remember { mutableStateOf(false) }
    var remaining by remember { mutableIntStateOf(0) }
    var startedAt by remember { mutableStateOf(0L) }

    val fa = language == Language.FA
    val direction = if (fa) LayoutDirection.Rtl else LayoutDirection.Ltr
    val labels = if (fa) Persian else English

    LaunchedEffect(playing) {
        if (playing) {
            startedAt = System.currentTimeMillis()
            while (playing) {
                val elapsed = ((System.currentTimeMillis() - startedAt) / 1000L).toInt()
                remaining = (seconds - elapsed).coerceAtLeast(0)
                if (!engine.isPlaying || remaining <= 0) {
                    engine.stop()
                    playing = false
                    remaining = 0
                } else {
                    delay(200)
                }
            }
        }
    }

    MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme(
        primary = Color(0xFF087E8B),
        secondary = Color(0xFF55C1B3),
        background = Color(0xFFF4F8FA),
        surface = Color.White
    )) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            Surface(Modifier.fillMaxSize(), color = Color(0xFFF4F8FA)) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Speaker Cleaner", style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold, color = Color(0xFF14323A))
                            Text("MOYA  •  ${if (fa) "آفلاین" else "OFFLINE AUDIO TOOL"}",
                                fontSize = 12.sp, color = Color(0xFF648087))
                        }
                        TextButton(onClick = { if (!playing) settingsPage = !settingsPage }) {
                            Text(if (settingsPage) labels.home else labels.settings)
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    if (settingsPage) {
                        Text(labels.settings, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(18.dp)) {
                                Text(labels.language, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(onClick = { language = Language.EN }) { Text("English") }
                                    OutlinedButton(onClick = { language = Language.FA }) { Text("فارسی") }
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(labels.safety, color = Color(0xFF64777C))
                            }
                        }
                    } else {
                        Text(labels.chooseMode, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Mode.entries.forEach { item ->
                                val title = when (item) {
                                    Mode.WATER -> labels.water
                                    Mode.DUST -> labels.dust
                                    Mode.TEST -> labels.test
                                }
                                val desc = when (item) {
                                    Mode.WATER -> labels.waterDesc
                                    Mode.DUST -> labels.dustDesc
                                    Mode.TEST -> labels.testDesc
                                }
                                Card(
                                    onClick = {
                                        if (!playing) {
                                            mode = item
                                            frequency = when (item) {
                                                Mode.WATER -> 165f
                                                Mode.DUST -> 1000f
                                                Mode.TEST -> 1000f
                                            }
                                        }
                                    },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (mode == item) Color(0xFFE1F4F2) else Color.White
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(when (item) {
                                            Mode.WATER -> "💧"
                                            Mode.DUST -> "〰"
                                            Mode.TEST -> "♫"
                                        }, fontSize = 24.sp)
                                        Column(Modifier.padding(horizontal = 12.dp)) {
                                            Text(title, fontWeight = FontWeight.Bold,
                                                color = Color(0xFF163940))
                                            Text(desc, style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF64777C))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(labels.frequency, fontWeight = FontWeight.SemiBold)
                                    Text("${frequency.roundToInt()} Hz", color = Color(0xFF087E8B),
                                        fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = frequency,
                                    onValueChange = { frequency = it },
                                    valueRange = when (mode) {
                                        Mode.WATER -> 100f..200f
                                        Mode.DUST -> 200f..20_000f
                                        Mode.TEST -> 20f..20_000f
                                    },
                                    enabled = !playing
                                )

                                if (mode == Mode.TEST) {
                                    Text(labels.channel, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                        Channel.entries.forEach { item ->
                                            val text = when (item) {
                                                Channel.BOTH -> labels.both
                                                Channel.LEFT -> labels.left
                                                Channel.RIGHT -> labels.right
                                            }
                                            if (channel == item) {
                                                Button(onClick = { channel = item }, enabled = !playing) { Text(text) }
                                            } else {
                                                OutlinedButton(onClick = { channel = item }, enabled = !playing) { Text(text) }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }

                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(labels.duration, fontWeight = FontWeight.SemiBold)
                                    Text("$seconds ${labels.seconds}", color = Color(0xFF087E8B),
                                        fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = seconds.toFloat(),
                                    onValueChange = { seconds = it.roundToInt() },
                                    valueRange = 5f..60f,
                                    steps = 10,
                                    enabled = !playing
                                )

                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(labels.intensity, fontWeight = FontWeight.SemiBold)
                                    Text("${(intensity * 100).roundToInt()}%", color = Color(0xFF087E8B),
                                        fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = intensity,
                                    onValueChange = { intensity = it.coerceIn(0f, ToneEngine.MAX_GAIN) },
                                    valueRange = 0f..ToneEngine.MAX_GAIN,
                                    enabled = !playing
                                )
                                Text(labels.cap, style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF64777C))

                                Spacer(Modifier.height(10.dp))
                                if (playing) {
                                    val transition = rememberInfiniteTransition(label = "pulse")
                                    val alpha by transition.animateFloat(
                                        initialValue = 0.35f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
                                        label = "alpha"
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Spacer(Modifier.size(10.dp).padding(0.dp))
                                        Text(labels.playing + "  •  " + labels.remaining + " $remaining " + labels.seconds,
                                            color = Color(0xFF087E8B), fontWeight = FontWeight.SemiBold)
                                    }
                                } else {
                                    Text(labels.ready, color = Color(0xFF64777C))
                                }

                                Spacer(Modifier.height(12.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = {
                                            val ok = engine.start(
                                                frequency,
                                                seconds,
                                                intensity,
                                                if (mode == Mode.DUST) "dust" else "fixed",
                                                channel.name.lowercase()
                                            )
                                            playing = ok
                                            remaining = seconds
                                        },
                                        enabled = !playing && intensity > 0f,
                                        modifier = Modifier.weight(1f)
                                    ) { Text(labels.start) }

                                    OutlinedButton(
                                        onClick = {
                                            engine.stop()
                                            playing = false
                                            remaining = 0
                                        },
                                        enabled = playing,
                                        modifier = Modifier.weight(1f)
                                    ) { Text(labels.stop) }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(labels.warning, color = Color(0xFF8A5A18),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

private data class Copy(
    val home: String, val settings: String, val language: String, val safety: String,
    val chooseMode: String, val water: String, val dust: String, val test: String,
    val waterDesc: String, val dustDesc: String, val testDesc: String,
    val frequency: String, val channel: String, val both: String, val left: String,
    val right: String, val duration: String, val seconds: String, val intensity: String,
    val cap: String, val playing: String, val remaining: String, val ready: String,
    val start: String, val stop: String, val warning: String
)

private val English = Copy(
    "Home", "Settings", "Language",
    "Use short, low-volume sessions. This tool cannot guarantee water or dust removal and does not replace professional repair. Stop if anything sounds or feels unusual.",
    "Choose a mode", "Water Mode", "Dust Mode", "Speaker Test",
    "Low tone · 100–200 Hz", "Variable tone · 200 Hz–20 kHz",
    "Check audible frequencies and stereo channels", "Frequency", "Output channel",
    "Both", "Left", "Right", "Duration", "sec", "Intensity",
    "Hard limit: 70% digital amplitude. Device loudness varies.",
    "Playing", "Time left", "Ready", "Start", "Stop",
    "Warning: Loud or prolonged tones can damage hearing or speakers. Keep volume low; results are not guaranteed."
)

private val Persian = Copy(
    "خانه", "تنظیمات", "زبان",
    "از زمان کوتاه و شدت کم استفاده کنید. خروج آب یا گردوغبار تضمین نمی‌شود و این ابزار جایگزین تعمیر تخصصی نیست. اگر صدای غیرعادی شنیدید، فوراً متوقف کنید.",
    "یک حالت انتخاب کنید", "حالت آب", "حالت گردوغبار", "آزمایش اسپیکر",
    "صدای بم · ۱۰۰ تا ۲۰۰ هرتز", "صدای متغیر · ۲۰۰ هرتز تا ۲۰ کیلوهرتز",
    "بررسی شنیداری فرکانس‌ها و کانال‌ها", "فرکانس", "کانال خروجی",
    "هر دو", "چپ", "راست", "مدت", "ثانیه", "شدت صدا",
    "محدودیت سخت: ۷۰٪ دامنه دیجیتال. بلندی واقعی به دستگاه بستگی دارد.",
    "در حال پخش", "زمان باقی‌مانده", "آماده", "شروع", "توقف",
    "هشدار: صدای بلند یا طولانی می‌تواند به شنوایی یا اسپیکر آسیب بزند. شدت را کم نگه دارید؛ نتیجه تضمین‌شده نیست."
)
