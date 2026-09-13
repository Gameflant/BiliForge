package com.feringsapp.biliforge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.feringsapp.biliforge.core.log.ForgeLogger
import com.feringsapp.biliforge.data.model.CacheBundle
import com.feringsapp.biliforge.ui.liquid.ForgeLiquidNavBar
import com.feringsapp.biliforge.ui.screens.AboutScreen
import com.feringsapp.biliforge.ui.screens.CacheActionScreen
import com.feringsapp.biliforge.ui.screens.ConvertScreen
import com.feringsapp.biliforge.ui.screens.GuideScreen
import com.feringsapp.biliforge.ui.screens.HomeScreen
import com.feringsapp.biliforge.ui.screens.LibraryScreen
import com.feringsapp.biliforge.ui.screens.LogScreen
import com.feringsapp.biliforge.ui.screens.ServicesScreen
import com.feringsapp.biliforge.ui.screens.SettingsScreen
import com.feringsapp.biliforge.ui.screens.TasksScreen

/** 三个底层 Tab */
enum class ForgeTab(val label: String, val icon: ImageVector) {
    HOME("主页", MiuixIcons.Home),
    EXTRACT("提取", MiuixIcons.Download),
    SETTINGS("设置", MiuixIcons.Settings),
}

/** 二级页面（可返回） */
enum class ForgeSubPage { SERVICES, TASKS, CONVERT, ABOUT, GUIDE, DEBUG, CACHE }

@Composable
fun ForgeApp() {
    ForgeLogger.render("ForgeApp")

    var subPage by rememberSaveable { mutableStateOf<ForgeSubPage?>(null) }
    var pendingBundle by remember { mutableStateOf<CacheBundle?>(null) }
    val mainPagerState = rememberPagerState(pageCount = { ForgeTab.entries.size })

    // 二级页覆盖层的动画状态：exit 动画期间保留内容不闪空
    var overlayVisible by remember { mutableStateOf(false) }
    var overlayContent by remember { mutableStateOf<ForgeSubPage?>(null) }

    LaunchedEffect(subPage) {
        val target = subPage
        if (target != null) {
            overlayContent = target
            overlayVisible = true
        } else {
            overlayVisible = false
        }
        ForgeLogger.ui1("Nav", if (target == null) "返回主界面" else "进入二级页 $target")
    }

    BackHandler(enabled = subPage != null) { subPage = null }

    // ★ 稳定回调：主界面（MainTabs）不因本层状态变化而重组
    val onOpen: (ForgeSubPage) -> Unit = remember { { subPage = it } }
    val onOpenCacheActions: (CacheBundle) -> Unit = remember {
        { b ->
            pendingBundle = b
            subPage = ForgeSubPage.CACHE
        }
    }
    val onBack: () -> Unit = remember { { subPage = null } }

    val surface = MiuixTheme.colorScheme.surface

    Box(
        Modifier
            .fillMaxSize()
            .background(surface)
    ) {
        // ① 主界面：常驻组合。二级页打开时不再销毁重建（性能关键）
        MainTabs(
            pagerState = mainPagerState,
            onOpen = onOpen,
            onOpenCacheActions = onOpenCacheActions,
            // 二级页在前台时冻结底栏玻璃（省下每帧纹理录制与 shader 采样）
            glassActive = subPage == null,
            subPageOpen = subPage != null,
        )

        // ② 二级页：覆盖层滑入 / 滑出（主界面零重建）
        AnimatedVisibility(
            visible = overlayVisible,
            enter = slideInHorizontally(tween(240)) { it } + fadeIn(tween(180)),
            exit = slideOutHorizontally(tween(240)) { it } + fadeOut(tween(160)),
            modifier = Modifier.fillMaxSize(),
        ) {
            when (overlayContent) {
                ForgeSubPage.SERVICES -> ServicesScreen(onBack = onBack)
                ForgeSubPage.TASKS -> TasksScreen(onBack = onBack)
                ForgeSubPage.CONVERT -> ConvertScreen(onBack = onBack)
                ForgeSubPage.ABOUT -> AboutScreen(onBack = onBack)
                ForgeSubPage.GUIDE -> GuideScreen(onBack = onBack)
                ForgeSubPage.DEBUG -> LogScreen(onBack = onBack)
                ForgeSubPage.CACHE -> {
                    val bundle = pendingBundle
                    if (bundle != null) {
                        CacheActionScreen(bundle = bundle, onBack = onBack)
                    }
                }
                null -> Unit
            }
        }
    }
}

@Composable
private fun MainTabs(
    pagerState: PagerState,
    onOpen: (ForgeSubPage) -> Unit,
    onOpenCacheActions: (CacheBundle) -> Unit,
    glassActive: Boolean,
    subPageOpen: Boolean,
) {
    ForgeLogger.render("MainTabs")

    val tabs = ForgeTab.entries
    val forgePager = rememberForgePagerState(pagerState)
    val navItems = remember { tabs.map { NavigationItem(it.label, it.icon) } }
    val surfaceColor = MiuixTheme.colorScheme.surface
    val glassOn = UiSettings.glassEnabled && glassActive
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    // 跟手同步：currentPage 在滑动过半时即更新（早于手势结束），
    // 快速连划时新同步会无缝接管上一段底栏动画（从当前位置直接追新目标）
    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.currentPage }.collect { forgePager.syncPage() }
    }

    // ★ 稳定回调
    val navClick: (Int) -> Unit = remember(forgePager) { { target: Int -> forgePager.goTo(target) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "BiliForge",
                subtitle = tabs[forgePager.selectedPage].label,
                largeTitle = "BiliForge",
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    // 关闭液态玻璃时完全不挂 backdrop：彻底省去每帧纹理录制
                    .then(if (glassOn) Modifier.layerBackdrop(backdrop) else Modifier)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true,
                    overscrollEffect = null,
                    beyondViewportPageCount = 2,   // 预组合全部页面：消除首次滑动的组合开销
                    key = { tabs[it].name },
                ) { page ->
                    when (tabs[page]) {
                        ForgeTab.HOME -> HomeScreen(
                            topPadding = padding.calculateTopPadding(),
                            onOpenServices = { onOpen(ForgeSubPage.SERVICES) },
                        )
                        ForgeTab.EXTRACT -> LibraryScreen(
                            topPadding = padding.calculateTopPadding(),
                            onOpenTasks = { onOpen(ForgeSubPage.TASKS) },
                            onOpenActions = onOpenCacheActions,
                        )
                        ForgeTab.SETTINGS -> SettingsScreen(
                            topPadding = padding.calculateTopPadding(),
                            onOpenConvert = { onOpen(ForgeSubPage.CONVERT) },
                            onOpenAbout = { onOpen(ForgeSubPage.ABOUT) },
                            onOpenGuide = { onOpen(ForgeSubPage.GUIDE) },
                            onOpenDebug = { onOpen(ForgeSubPage.DEBUG) },
                        )
                    }
                }
            }

            // 独立重组作用域：selectedPage 变化只重组底栏，不带动 MainTabs
            NavBarHost(
                forgePager = forgePager,
                navItems = navItems,
                navClick = navClick,
                backdrop = backdrop,
                glassOn = glassOn,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            // 返回键：仅在二级页关闭 & 非主页 Tab 时回到主页
            BackToHomeEffect(forgePager = forgePager, enabled = !subPageOpen)
        }
    }
}


/** 底栏宿主（独立重组作用域：selectedPage 变化仅重组这里） */
@Composable
private fun NavBarHost(
    forgePager: ForgePagerState,
    navItems: List<NavigationItem>,
    navClick: (Int) -> Unit,
    backdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop,
    glassOn: Boolean,
    modifier: Modifier = Modifier,
) {
    ForgeLogger.render("NavBarHost")
    ForgeLiquidNavBar(
        items = navItems,
        selectedIndex = forgePager.selectedPage,
        onItemClick = navClick,
        backdrop = backdrop,
        isBlurActive = glassOn,
        modifier = modifier,
    )
}

/** 返回键监听（独立作用域：selectedPage 变化仅重组这个空组件） */
@Composable
private fun BackToHomeEffect(forgePager: ForgePagerState, enabled: Boolean) {
    BackHandler(enabled = enabled && forgePager.selectedPage != 0) {
        forgePager.goTo(0)
    }
}
