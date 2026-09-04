package org.namchieh.rusmorph.wordcard.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.namchieh.rusmorph.ui.theme.RusMorphColors
import org.namchieh.rusmorph.wordcard.model.*

/**
 * 带有物理手势与 3D 翻转动效的单词卡片容器。
 *
 * 支持交互手势：
 * 1. 单击卡面 / 翻面键：3D 翻转 (rotationY 260ms)；
 * 2. 左右拖拽：X 位移跟随 + Z 轴微倾斜旋转；
 * 3. 拖拽释放：超过 25% 宽度触发复习提交 (左滑 Again，右滑 Good)；不足阈值弹性回弹 (springBack)；
 * 4. 双击主词：朗读标准发音；
 * 5. 长按主词：唤出 AI 形态工作区。
 */
@Composable
fun WordCardContainer(
    lexeme: Lexeme,
    form: WordForm,
    cardMode: CardMode,
    reviewState: ReviewState,
    isFavorite: Boolean,
    isSpeaking: Boolean,
    onToggleFavorite: () -> Unit,
    onPlayAudio: () -> Unit,
    onFollowAlong: () -> Unit,
    onReviewResult: (ReviewResult) -> Unit,
    onOpenAiWorkspace: () -> Unit,
    onNavigateToRule: ((category: String, ruleId: String) -> Unit)? = null,
    onSwipeUpExpand: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isFlipped by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val density = LocalDensity.current.density
    val dragThresholdPx = with(LocalDensity.current) { (screenWidth * 0.25f).toPx() }

    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    // 3D Flip 动画 (260ms)
    val rotationY by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "card_flip"
    )

    // 滑动倾斜与透明度
    val rotationZ = (offsetX.value / dragThresholdPx) * 6f
    val cardAlpha = (1f - (kotlin.math.abs(offsetX.value) / (dragThresholdPx * 3f))).coerceIn(0.7f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        // 单击卡片翻转
                        isFlipped = !isFlipped
                    },
                    onDoubleTap = {
                        // 双击朗读发音
                        onPlayAudio()
                    },
                    onLongPress = {
                        // 长按打开 AI 形态工作区
                        onOpenAiWorkspace()
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                            offsetY.snapTo(offsetY.value + dragAmount.y * 0.2f)
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            val currentX = offsetX.value
                            val currentY = offsetY.value
                            when {
                                // 上滑手势检测
                                currentY < -80f -> {
                                    onSwipeUpExpand()
                                    offsetY.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                                    offsetX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                                }
                                // 左滑超过阈值：提交 AGAIN
                                currentX < -dragThresholdPx -> {
                                    offsetX.animateTo(-dragThresholdPx * 2.5f, tween(160))
                                    onReviewResult(ReviewResult.AGAIN)
                                    offsetX.snapTo(0f)
                                    offsetY.snapTo(0f)
                                }
                                // 右滑超过阈值：提交 GOOD
                                currentX > dragThresholdPx -> {
                                    offsetX.animateTo(dragThresholdPx * 2.5f, tween(160))
                                    onReviewResult(ReviewResult.GOOD)
                                    offsetX.snapTo(0f)
                                    offsetY.snapTo(0f)
                                }
                                // 未达阈值：物理弹性回弹
                                else -> {
                                    offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                                    offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                                }
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // 拖拽提示层 (红橙: 不熟悉 / 绿: 已掌握)
        if (kotlin.math.abs(offsetX.value) > 20f) {
            val isGood = offsetX.value > 0
            Surface(
                color = if (isGood) RusMorphColors.AccentGreen else RusMorphColors.AccentOrange,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(if (isGood) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = if (isGood) "已掌握 GOOD" else "不熟悉 AGAIN",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        // 3D 渲染卡面
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = offsetX.value
                    translationY = offsetY.value
                    this.rotationZ = rotationZ
                    this.rotationY = rotationY
                    cameraDistance = 14f * density
                    alpha = cardAlpha
                }
        ) {
            if (rotationY <= 90f) {
                // 正面视图
                WordCardFront(
                    lexeme = lexeme,
                    form = form,
                    cardMode = cardMode,
                    isFavorite = isFavorite,
                    isSpeaking = isSpeaking,
                    onToggleFavorite = onToggleFavorite,
                    onPlayAudio = onPlayAudio,
                    onFollowAlong = onFollowAlong,
                    onFlip = { isFlipped = true },
                )
            } else {
                // 背面视图 (反向旋转 180 度避免镜像翻转)
                Box(modifier = Modifier.graphicsLayer { this.rotationY = 180f }) {
                    WordCardBack(
                        lexeme = lexeme,
                        form = form,
                        reviewState = reviewState,
                        onReviewResult = onReviewResult,
                        onOpenAiWorkspace = onOpenAiWorkspace,
                        onPlayAudio = onPlayAudio,
                        onNavigateToRule = onNavigateToRule,
                        onFlip = { isFlipped = false },
                    )
                }
            }
        }
    }
}
