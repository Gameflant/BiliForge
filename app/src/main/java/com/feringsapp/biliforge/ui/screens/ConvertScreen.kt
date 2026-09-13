package com.feringsapp.biliforge.ui.screens

import androidx.compose.runtime.DisposableEffect
import com.feringsapp.biliforge.core.log.ForgeLogger
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import com.feringsapp.biliforge.ui.components.HeroColors
import com.feringsapp.biliforge.ui.components.HeroCard
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.feringsapp.biliforge.ui.components.OneLineItem
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.feringsapp.biliforge.core.shizuku.ShizukuBridge
import com.feringsapp.biliforge.media.ffmpeg.TranscodePreset
import com.feringsapp.biliforge.media.ffmpeg.TranscodePresets
import com.feringsapp.biliforge.work.ForgeCore

enum class FileKind(val label: String) {
    VIDEO("视频"),
    AUDIO("音频"),
    UNSUPPORTED("不支持的格式"),
}

/** 后缀识别：视频 / 音频 / 不支持 */
fun classifyFile(name: String?): FileKind {
    val ext = name?.substringAfterLast('.', "")?.lowercase() ?: return FileKind.UNSUPPORTED
    return when (ext) {
        "mp4", "mkv", "mov", "webm", "ts", "m2ts", "flv", "avi", "m4v", "3gp",
        "mpg", "mpeg", "wmv", "rmvb", "rm", "vob", "ogv", "mts" -> FileKind.VIDEO
        "mp3", "flac", "wav", "aac", "m4a", "ogg", "opus", "wma", "ape", "alac", "mka" -> FileKind.AUDIO
        else -> FileKind.UNSUPPORTED
    }
}

private val REMUX = TranscodePreset(
    id = "remux",
    label = "MP4 快速重封装",
    desc = "不重编码，仅换容器（最快）",
    videoArgs = listOf("-c:v", "copy"),
    audioArgs = listOf("-c:a", "copy"),
)

/** 二级页：格式转换 */
@Composable
fun ConvertScreen(onBack: () -> Unit) {
    ForgeLogger.render("ConvertScreen")
    DisposableEffect(Unit) {
        ForgeLogger.trace("Screen", "ConvertScreen 进入组合")
        onDispose { ForgeLogger.trace("Screen", "ConvertScreen 离开组合") }
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickedName by remember { mutableStateOf<String?>(null) }
    var pickedSize by remember { mutableLongStateOf(0L) }
    var kind by remember { mutableStateOf(FileKind.UNSUPPORTED) }
    var selected by remember { mutableStateOf<TranscodePreset?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var copiedBytes by remember { mutableLongStateOf(0L) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            pickedUri = uri
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    pickedName = if (ni >= 0) c.getString(ni) else uri.lastPathSegment
                    pickedSize = if (si >= 0) c.getLong(si) else 0L
                }
            }
            kind = classifyFile(pickedName)
            ForgeLogger.ui2("Convert", "选择文件: $pickedName（${kind.label}）")
            selected = null
            status = null
        }
    }

    val presets = when (kind) {
        FileKind.VIDEO -> listOf(REMUX) + TranscodePresets.ALL
        FileKind.AUDIO -> listOf(TranscodePresets.AUDIO_M4A, TranscodePresets.AUDIO_MP3)
        FileKind.UNSUPPORTED -> emptyList()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "格式转换",
                navigationIcon = {
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .clip(CircleShape)
                            .clickable { onBack() }
                            .padding(8.dp),
                    ) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                HeroCard(
                    title = pickedName ?: "格式转换",
                    subtitle = if (pickedName != null) {
                        "${kind.label} · ${fmtBytes(pickedSize)}"
                    } else {
                        "选择视频 / 音频，转换为通用格式"
                    },
                    icon = MiuixIcons.ConvertFile,
                    onClick = { launcher.launch(arrayOf("*/*")) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Button(
                        onClick = { launcher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (pickedName == null) "选择文件" else "重新选择")
                    }
                }
            }

            if (pickedName != null && kind == FileKind.UNSUPPORTED) {
                item {
                    Card(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(
                            text = "不支持该格式。可用：mp4 / mkv / mov / ts / flv / mp3 / flac / m4a 等",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }

            if (kind != FileKind.UNSUPPORTED && presets.isNotEmpty()) {
                item { SmallTitle("转换目标") }
                item {
                    Card(Modifier.padding(horizontal = 16.dp)) {
                        presets.forEach { preset ->
                            OneLineItem(
                                title = preset.label,
                                summary = if (selected?.id == preset.id) "已选择" else preset.desc,
                                onClick = { selected = preset },
                            )
                        }
                    }
                }
                item {
                    Card(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Button(
                                onClick = {
                                    val uri = pickedUri ?: return@Button
                                    val preset = selected ?: return@Button
                                    if (busy) return@Button
                                    ForgeLogger.ui1("Convert", "开始转换: $pickedName → ${preset.label}")
                                    busy = true
                                    status = "正在拷贝文件到工作目录…"
                                    scope.launch {
                                        val remotePath = withContext(Dispatchers.IO) {
                                            copyUriToWorkDir(ctx, uri, pickedName ?: "input.bin") { done ->
                                                copiedBytes = done
                                                status = "正在拷贝：${fmtBytes(done)}"
                                            }
                                        }
                                        if (remotePath == null) {
                                            status = "拷贝失败（存储空间或权限问题）"
                                        } else {
                                            ForgeCore.enqueueTranscode(
                                                input = remotePath,
                                                preset = preset,
                                                title = pickedName ?: "转换任务",
                                            )
                                            status = "已加入转换任务 → 返回「提取」页查看进度"
                                        }
                                        busy = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = selected != null && !busy,
                            ) {
                                Text(if (busy) "处理中…" else "开始转换")
                            }
                        }
                        status?.let {
                            Text(
                                text = it,
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 把系统文件选择器的内容流式拷贝到 shell 可读的工作目录 */
private suspend fun copyUriToWorkDir(
    ctx: android.content.Context,
    uri: Uri,
    name: String,
    onProgress: (done: Long) -> Unit,
): String? = withContext(Dispatchers.IO) {
    val svc = ShizukuBridge.service.value ?: return@withContext null
    val safeName = name.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
    val dir = "/sdcard/Download/BiliForge/inbox"
    val remotePath = "$dir/$safeName"
    svc.mkdirs(dir)
    try {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(192 * 1024)
            var offset = 0L
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                val chunk = if (n == buf.size) buf else buf.copyOf(n)
                if (!svc.writeChunk(remotePath, offset, chunk)) return@withContext null
                offset += n
                onProgress(offset)
            }
        } ?: return@withContext null
    } catch (t: Throwable) {
        return@withContext null
    }
    remotePath
}
