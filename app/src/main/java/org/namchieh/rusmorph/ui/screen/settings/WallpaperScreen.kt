package org.namchieh.rusmorph.ui.screen.settings

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.namchieh.rusmorph.R
import org.namchieh.rusmorph.data.settings.AppSettings
import org.namchieh.rusmorph.ui.design.WerusColors

@Composable
fun WallpaperImage(wallpaper: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val customImage by produceState<ImageBitmap?>(initialValue = null, wallpaper) {
        value = if (wallpaper != AppSettings.DEFAULT_WALLPAPER && wallpaper != AppSettings.NO_WALLPAPER) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(wallpaper)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    val longestSide = maxOf(bounds.outWidth, bounds.outHeight)
                    val sampleSize = generateSequence(1) { it * 2 }.first { longestSide / it <= 2048 }
                    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
                }.getOrNull()
            }
        } else null
    }
    if (wallpaper == AppSettings.NO_WALLPAPER) {
        Box(modifier.background(WerusColors.Canvas))
    } else if (customImage != null && wallpaper != AppSettings.DEFAULT_WALLPAPER) {
        Image(customImage!!, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Image(painterResource(R.drawable.default_wallpaper), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperScreen(appSettings: AppSettings, onBack: () -> Unit) {
    val wallpaper by appSettings.wallpaper.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                appSettings.setWallpaper(uri.toString())
                error = null
            }.onFailure { error = "无法读取所选图片，请重新选择。" }
        }
    }
    Scaffold(
        containerColor = WerusColors.Canvas,
        topBar = {
            TopAppBar(
                title = { Text("壁纸") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WerusColors.Paper),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("首页壁纸")
            WallpaperImage(
                wallpaper,
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp)),
            )
            Text(
                when (wallpaper) {
                    AppSettings.DEFAULT_WALLPAPER -> "正在使用默认壁纸"
                    AppSettings.NO_WALLPAPER -> "正在使用纯色背景"
                    else -> "正在使用自选图片"
                },
                color = WerusColors.InkMuted,
            )
            error?.let { Text(it, color = WerusColors.Error) }
            Button(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("选择本地图片")
            }
            OutlinedButton(onClick = { appSettings.setWallpaper(AppSettings.DEFAULT_WALLPAPER) }, modifier = Modifier.fillMaxWidth()) {
                Text("使用默认壁纸")
            }
            TextButton(onClick = { appSettings.setWallpaper(AppSettings.NO_WALLPAPER) }, modifier = Modifier.fillMaxWidth()) {
                Text("使用纯色背景")
            }
        }
    }
}
