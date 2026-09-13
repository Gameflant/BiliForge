package com.feringsapp.biliforge.ui

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 高性能 Tab 切换状态（参考 KernelSU / InstallerX-Revived 的 MainPagerState 方案，独立实现）。
 *
 * 性能要点：
 *  1. 用 [PagerState.scroll] + [animate] **手动驱动滚动**（绕过 animateScrollToPage 的宽松
 *     弹簧插值，时长精确可控：100ms × 距离 + 100ms）；
 *  2. [selectedPage] 与 [isNavigating] 独立管理——导航动画期间底栏选中态保持稳定，
 *     不读取 pagerState.currentPage（避免每帧重组传播）；
 *  3. 快速连点安全：旧动画 Job 立即取消。
 */
class ForgePagerState(
    val pagerState: PagerState,
    private val scope: CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    fun goTo(index: Int) {
        if (index == selectedPage) return

        navJob?.cancel()
        selectedPage = index
        isNavigating = true

        navJob = scope.launch {
            val self = coroutineContext.job
            try {
                pagerState.scroll(MutatePriority.UserInput) {
                    val info = pagerState.layoutInfo
                    val pageSize = (info.pageSize + info.pageSpacing).coerceAtLeast(1)
                    val remainingPages =
                        index - pagerState.currentPage - pagerState.currentPageOffsetFraction
                    val targetPx = remainingPages * pageSize
                    val durationMs = 100 * abs(index - pagerState.currentPage).coerceAtLeast(1) + 100
                    var consumed = 0f
                    animate(
                        initialValue = 0f,
                        targetValue = targetPx,
                        animationSpec = tween(easing = EaseInOut, durationMillis = durationMs),
                    ) { value, _ ->
                        consumed += scrollBy(value - consumed)
                    }
                }
                if (pagerState.currentPage != index) {
                    pagerState.scrollToPage(index)
                }
            } finally {
                if (navJob == self) {
                    isNavigating = false
                    if (pagerState.currentPage != index) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    /** 手势滑动结束后同步选中态 */
    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
fun rememberForgePagerState(
    pagerState: PagerState,
    scope: CoroutineScope = rememberCoroutineScope(),
): ForgePagerState = remember(pagerState, scope) {
    ForgePagerState(pagerState, scope)
}
