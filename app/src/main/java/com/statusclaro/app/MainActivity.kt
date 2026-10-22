package com.statusclaro.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.media3.common.OverlaySettings
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.Presentation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { StatusClaroTheme { StatusClaroApp() } }
    }
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF126B57),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F2E7),
    onPrimaryContainer = Color(0xFF073D31),
    secondary = Color(0xFF52665F),
    background = Color(0xFFF6F8F5),
    surface = Color.White,
    surfaceVariant = Color(0xFFE6ECE8),
    onSurface = Color(0xFF18201D),
    onSurfaceVariant = Color(0xFF56615D),
    outline = Color(0xFF84918C),
    outlineVariant = Color(0xFFD6DEDA),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82D7BC),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF00513F),
    onPrimaryContainer = Color(0xFFA0F4D7),
    secondary = Color(0xFFB7CCC4),
    background = Color(0xFF101512),
    surface = Color(0xFF171C19),
    surfaceVariant = Color(0xFF28312D),
    onSurface = Color(0xFFE1E9E4),
    onSurfaceVariant = Color(0xFFBDC9C4),
    outline = Color(0xFF89958F),
    outlineVariant = Color(0xFF3D4944),
)

@Composable
private fun StatusClaroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}

private data class VideoInfo(
    val name: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val bytes: Long,
    val frameRate: Float?,
    val thumbnail: Bitmap?,
) {
    val isPortrait: Boolean get() = height > width
}

private enum class QualityProfile(
    val title: String,
    val subtitle: String,
    val bitrate: Int,
    val portraitHeight: Int,
    val landscapeHeight: Int,
) {
    BALANCED("Equilibrado", "720p · archivo más ligero", 2_500_000, 1280, 720),
    DETAIL("Alta calidad", "1080p · mejor para movimiento", 5_000_000, 1920, 1080),
    ACTION("Acción", "1080p · más detalle en movimiento", 6_000_000, 1920, 1080),
}

private enum class FramingMode(val title: String, val subtitle: String) {
    COMPLETE("Video completo", "Conserva toda la imagen; puede dejar franjas"),
    FILL("Llenar pantalla", "Formato 9:16; recorta los laterales"),
    CINEMATIC("Fondo cinematográfico", "Fondo desenfocado y video nítido al centro"),
}

@Composable
private fun StatusClaroApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var videoInfo by remember { mutableStateOf<VideoInfo?>(null) }
    var quality by remember { mutableStateOf(QualityProfile.DETAIL) }
    var framing by remember { mutableStateOf(FramingMode.COMPLETE) }
    var isExporting by remember { mutableStateOf(false) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            selectedUri = uri
            videoInfo = readVideoInfo(context, uri)
            outputFile = null
            errorMessage = if (videoInfo == null) "No pudimos leer este video. Prueba con otro archivo." else null
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                BrandHeader()
                HeroCard()

                StepCard(number = "1", title = "Elige tu video") {
                    if (videoInfo == null) {
                        EmptyVideoState(onSelect = { picker.launch(arrayOf("video/*")) })
                    } else {
                        VideoSummary(videoInfo!!, onChange = { picker.launch(arrayOf("video/*")) })
                    }
                }

                if (videoInfo != null) {
                    StepCard(number = "2", title = "Define el resultado") {
                        SectionLabel("Calidad")
                        QualityProfile.entries.forEach { option ->
                            ChoiceRow(
                                selected = quality == option,
                                title = option.title,
                                subtitle = option.subtitle,
                                trailing = if (option == QualityProfile.DETAIL) "Recomendado" else null,
                                onClick = { quality = option },
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(6.dp))
                        SectionLabel("Encuadre")
                        FramingMode.entries.forEach { option ->
                            ChoiceRow(
                                selected = framing == option,
                                title = option.title,
                                subtitle = option.subtitle,
                                onClick = { framing = option },
                            )
                        }
                    }

                    ExportCard(
                        info = videoInfo!!,
                        quality = quality,
                        framing = framing,
                        isExporting = isExporting,
                        outputFile = outputFile,
                        errorMessage = errorMessage,
                        onExport = {
                            isExporting = true
                            outputFile = null
                            errorMessage = null
                            VideoOptimizer(context).export(
                                input = selectedUri!!,
                                info = videoInfo!!,
                                quality = quality,
                                framing = framing,
                                onSuccess = { file ->
                                    isExporting = false
                                    outputFile = file
                                },
                                onError = { error ->
                                    isExporting = false
                                    errorMessage = error
                                },
                            )
                        },
                        onShare = { outputFile?.let { shareVideo(context, it) } },
                    )
                }

                PrivacyNote()
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(42.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("S", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Status Claro", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Videos listos para tu Estado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
            Text(
                "Local",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun HeroCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Dale a tu Estado la mejor oportunidad de verse nítido.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Preparamos el video antes de enviarlo a WhatsApp. El proceso ocurre completamente en tu teléfono.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun StepCard(number: String, title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(30.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(number, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun EmptyVideoState(onSelect: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("MP4 · MOV · videos de tu galería", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onSelect) { Text("Seleccionar video") }
        }
    }
}

@Composable
private fun VideoSummary(info: VideoInfo, onChange: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        info.thumbnail?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Vista previa de ${info.name}",
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium).background(Color.Black),
                contentScale = ContentScale.Crop,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(info.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildVideoDescription(info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onChange) { Text("Cambiar") }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent, shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.let {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ExportCard(
    info: VideoInfo,
    quality: QualityProfile,
    framing: FramingMode,
    isExporting: Boolean,
    outputFile: File?,
    errorMessage: String?,
    onExport: () -> Unit,
    onShare: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (outputFile != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                isExporting -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Preparando tu video", fontWeight = FontWeight.Bold)
                            Text(
                                if (framing == FramingMode.CINEMATIC) "El fondo cinematográfico requiere un poco más de tiempo."
                                else "Puede tardar unos minutos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                outputFile != null -> {
                    Text("✓ Video listo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        "${quality.title} · ${framing.title} · ${formatBytes(outputFile.length())}",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onShare, modifier = Modifier.fillMaxWidth()) { Text("Compartir en WhatsApp") }
                    OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Exportar otra vez") }
                }
                else -> {
                    Text("Todo listo para optimizar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Salida estimada: ${estimatedOutput(info, quality)} · ${quality.title} · ${framing.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onExport,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) { Text("Preparar para Estado") }
                }
            }
            errorMessage?.let {
                Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f), shape = MaterialTheme.shapes.small) {
                    Text(
                        it,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyNote() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(
            "Privado por diseño: sin cuenta, sin nube y sin subir tus videos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun buildVideoDescription(info: VideoInfo): String {
    val fps = info.frameRate?.roundToInt()?.let { " · $it fps" }.orEmpty()
    return "${info.width}×${info.height} · ${formatDuration(info.durationMs)}$fps · ${formatBytes(info.bytes)}"
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000
    return "%d:%02d".format(Locale.US, totalSeconds / 60, totalSeconds % 60)
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "tamaño desconocido"
    bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    else -> String.format(Locale.US, "%.0f KB", bytes / 1_000.0)
}

private fun estimatedOutput(info: VideoInfo, quality: QualityProfile): String {
    val videoBytes = quality.bitrate.toLong() * info.durationMs / 8_000
    val audioBytes = 128_000L * info.durationMs / 8_000
    return formatBytes(videoBytes + audioBytes)
}

private fun readVideoInfo(context: Context, uri: Uri): VideoInfo? = runCatching {
    val resolver = context.contentResolver
    var name = "Video seleccionado"
    var bytes = 0L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            name = cursor.getString(0) ?: name
            bytes = if (cursor.isNull(1)) 0L else cursor.getLong(1)
        }
    }

    MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(context, uri)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
        val scale = minOf(720f / width.coerceAtLeast(1), 720f / height.coerceAtLeast(1), 1f)
        val thumbWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val thumbHeight = (height * scale).roundToInt().coerceAtLeast(1)
        val thumbnail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, thumbWidth, thumbHeight)
        } else {
            retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { raw ->
                if (raw.width == thumbWidth && raw.height == thumbHeight) raw
                else Bitmap.createScaledBitmap(raw, thumbWidth, thumbHeight, true).also { raw.recycle() }
            }
        }
        VideoInfo(
            name = name,
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L,
            width = width,
            height = height,
            bytes = bytes,
            frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull(),
            thumbnail = thumbnail,
        )
    }
}.getOrNull()

private fun shareVideo(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Compartir video preparado"))
}

private class VideoOptimizer(private val context: Context) {
    fun export(
        input: Uri,
        info: VideoInfo,
        quality: QualityProfile,
        framing: FramingMode,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit,
    ) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val output = File(exportDir, "status-claro-${System.currentTimeMillis()}.mp4")
        val visualOnlyOutput = File(exportDir, "status-claro-visual-${System.currentTimeMillis()}.mp4")
        val transformedOutput = if (framing == FramingMode.CINEMATIC) visualOnlyOutput else output
        val targetHeight = if (info.isPortrait) quality.portraitHeight else quality.landscapeHeight
        val videoEffect = when (framing) {
            FramingMode.COMPLETE -> Presentation.createForHeight(targetHeight)
            FramingMode.FILL -> Presentation.createForWidthAndHeight(
                if (quality == QualityProfile.BALANCED) 720 else 1080,
                if (quality == QualityProfile.BALANCED) 1280 else 1920,
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
            )
            FramingMode.CINEMATIC -> Presentation.createForHeight(targetHeight)
        }
        val item = EditedMediaItem.Builder(MediaItem.fromUri(input))
            .setEffects(Effects(emptyList(), listOf(videoEffect)))
            .build()
        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder().setBitrate(quality.bitrate).build(),
            )
            .build()

        val transformerBuilder = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setEncoderFactory(encoderFactory)
            .setPortraitEncodingEnabled(true)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    activeTransformer = null
                    if (framing == FramingMode.CINEMATIC) {
                        // Media3's multi-layer compositor is stable for video, but on this device
                        // its audio graph fails when it is part of the same composition. Copy the
                        // original AAC track after the visual export instead of reprocessing it.
                        remuxExecutor.execute {
                            val result = runCatching {
                                muxVisualVideoWithOriginalAudio(visualOnlyOutput, input, output)
                            }
                            visualOnlyOutput.delete()
                            if (result.isSuccess) {
                                deliverOnMain { onSuccess(output) }
                            } else {
                                output.delete()
                                deliverOnMain {
                                    onError(result.exceptionOrNull()?.message ?: "No pudimos añadir el audio al video.")
                                }
                            }
                        }
                    } else {
                        deliverOnMain { onSuccess(output) }
                    }
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    activeTransformer = null
                    transformedOutput.delete()
                    deliverOnMain { onError(exportException.message ?: "No pudimos exportar el video.") }
                }
            })
        if (framing != FramingMode.CINEMATIC) {
            transformerBuilder.setAudioMimeType(MimeTypes.AUDIO_AAC)
        }
        activeTransformer = transformerBuilder.build()
        if (framing == FramingMode.CINEMATIC) {
            activeTransformer!!.start(buildCinematicComposition(input, quality), transformedOutput.absolutePath)
        } else {
            activeTransformer!!.start(item, transformedOutput.absolutePath)
        }
    }

    private fun buildCinematicComposition(input: Uri, quality: QualityProfile): Composition {
        val outputWidth = if (quality == QualityProfile.BALANCED) 720 else 1080
        val outputHeight = if (quality == QualityProfile.BALANCED) 1280 else 1920

        val foreground = EditedMediaItem.Builder(MediaItem.fromUri(input))
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(
                        Presentation.createForWidthAndHeight(
                            outputWidth,
                            outputHeight,
                            Presentation.LAYOUT_SCALE_TO_FIT,
                        ),
                    ),
                ),
            )
            .build()
        val background = EditedMediaItem.Builder(MediaItem.fromUri(input))
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(
                        Presentation.createForWidthAndHeight(
                            outputWidth,
                            outputHeight,
                            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
                        ),
                        GaussianBlur(18f),
                    ),
                ),
            )
            .build()

        // Esta primera pasada compone exclusivamente las capas visuales.
        // El audio se copia sin recodificar en una segunda pasada segura.
        val foregroundSequence = EditedMediaItemSequence.withVideoFrom(listOf(foreground))
        val backgroundSequence = EditedMediaItemSequence.withVideoFrom(listOf(background))
        return Composition.Builder(listOf(foregroundSequence, backgroundSequence))
            .setVideoCompositorSettings(
                CinematicCompositorSettings(
                    outputWidth = outputWidth,
                    outputHeight = outputHeight,
                ),
            )
            .build()
    }

    private fun muxVisualVideoWithOriginalAudio(
        visualOnlyVideo: File,
        originalVideo: Uri,
        destination: File,
    ) {
        val visualExtractor = MediaExtractor()
        val originalExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            visualExtractor.setDataSource(visualOnlyVideo.absolutePath)
            originalExtractor.setDataSource(context, originalVideo, null)

            val visualTrack = findTrack(visualExtractor, "video/")
                ?: error("No se encontró el video procesado.")
            val audioTrack = findTrack(originalExtractor, "audio/")
            if (audioTrack == null) {
                visualOnlyVideo.copyTo(destination, overwrite = true)
                return
            }

            val localMuxer = MediaMuxer(destination.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = localMuxer
            val muxedVisualTrack = localMuxer.addTrack(visualExtractor.getTrackFormat(visualTrack))
            val muxedAudioTrack = localMuxer.addTrack(originalExtractor.getTrackFormat(audioTrack))
            localMuxer.start()
            muxerStarted = true

            copyTrack(visualExtractor, visualTrack, localMuxer, muxedVisualTrack)
            copyTrack(originalExtractor, audioTrack, localMuxer, muxedAudioTrack)
        } finally {
            if (muxerStarted) runCatching { muxer?.stop() }
            muxer?.release()
            visualExtractor.release()
            originalExtractor.release()
        }
    }

    private fun findTrack(extractor: MediaExtractor, mimePrefix: String): Int? =
        (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(android.media.MediaFormat.KEY_MIME)
                ?.startsWith(mimePrefix) == true
        }

    private fun copyTrack(
        extractor: MediaExtractor,
        sourceTrack: Int,
        muxer: MediaMuxer,
        targetTrack: Int,
    ) {
        val buffer = ByteBuffer.allocateDirect(4 * 1024 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()
        extractor.selectTrack(sourceTrack)
        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        while (true) {
            buffer.clear()
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                MediaCodec.BUFFER_FLAG_KEY_FRAME
            } else {
                0
            }
            bufferInfo.set(0, sampleSize, extractor.sampleTime, flags)
            muxer.writeSampleData(targetTrack, buffer, bufferInfo)
            extractor.advance()
        }
        extractor.unselectTrack(sourceTrack)
    }

    private fun deliverOnMain(action: () -> Unit) {
        mainHandler.post(action)
    }

    private companion object {
        var activeTransformer: Transformer? = null
        val remuxExecutor = Executors.newSingleThreadExecutor()
        val mainHandler = Handler(Looper.getMainLooper())
    }
}

private class CinematicCompositorSettings(
    private val outputWidth: Int,
    private val outputHeight: Int,
) : VideoCompositorSettings {
    override fun getOutputSize(inputSizes: MutableList<Size>): Size = Size(outputWidth, outputHeight)

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings =
        StaticOverlaySettings.Builder().build()
}
