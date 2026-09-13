package com.feringsapp.biliforge.core.shizuku

import com.feringsapp.biliforge.core.log.ForgeLogger
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.feringsapp.biliforge.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import rikka.sui.Sui

private const val TAG = "BiliForge/Shizuku"
private const val SERVICE_TAG = "biliforge-fs"

/**
 * Shizuku / Sui 桥接层。
 *
 * 三条链路：
 *  1. Binder 存活监听（Shizuku 本体是否在跑）
 *  2. 权限申请（与运行时权限同一套心智模型）
 *  3. 绑定 [RemoteFsService]（以 ADB shell 身份运行的文件服务，用于读取 B 站缓存目录）
 */
object ShizukuBridge {

    const val PERMISSION_REQUEST_CODE = 0xB71F

    enum class Phase {
        /** Shizuku 未安装 / 未运行 / 版本过低 */
        NO_BINDER,

        /** 有 Binder，等待用户授权 */
        NEED_PERMISSION,

        /** 用户明确拒绝 */
        DENIED,

        /** 文件服务拉起中 */
        BINDING,

        /** 文件服务就绪 */
        READY,
    }

    private val _phase = MutableStateFlow(Phase.NO_BINDER)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _detail = MutableStateFlow("等待 Shizuku 服务…")
    val detail: StateFlow<String> = _detail.asStateFlow()

    private val _service = MutableStateFlow<IRemoteService?>(null)
    val service: StateFlow<IRemoteService?> = _service.asStateFlow()

    private var appContext: Context? = null
    private var args: Shizuku.UserServiceArgs? = null
    private var binding = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "user service connected")
            ForgeLogger.sys("Shizuku", "文件服务已连接 · ADB shell 身份", 1)
            binding = false
            _service.value = IRemoteService.Stub.asInterface(binder)
            _phase.value = Phase.READY
            _detail.value = "文件服务在线 · ADB shell 身份"
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "user service disconnected")
            ForgeLogger.sys("Shizuku", "文件服务已断开", 1)
            binding = false
            _service.value = null
            refresh()
        }
    }

    /** 在 Application#onCreate 里调用一次 */
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        runCatching { Sui.init(context.packageName) }
        runCatching { Shizuku.addBinderReceivedListenerSticky { onBinderAlive(true) } }
        runCatching { Shizuku.addBinderDeadListener { onBinderAlive(false) } }
        runCatching {
            Shizuku.addRequestPermissionResultListener { code, result ->
                Log.i(TAG, "permission result: code=$code result=$result")
                ForgeLogger.sys("Shizuku", "权限申请结果 code=$code result=$result", 1)
                if (code == PERMISSION_REQUEST_CODE) refresh()
            }
        }
        refresh()
    }

    val permissionGranted: Boolean
        get() = runCatching {
            Shizuku.pingBinder() && !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    /** 重新评估当前状态（UI 可随时调用） */
    fun refresh() {
        if (appContext == null) return
        try {
            if (!Shizuku.pingBinder()) {
                _phase.value = Phase.NO_BINDER
                _detail.value = "Shizuku 未运行（或未安装）"
                return
            }
            if (Shizuku.isPreV11()) {
                _phase.value = Phase.NO_BINDER
                _detail.value = "Shizuku 版本过低（需要 v11+）"
                return
            }
            when {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ensureBound()
                Shizuku.shouldShowRequestPermissionRationale() -> {
                    _phase.value = Phase.DENIED
                    _detail.value = "权限被拒绝 · 请在 Shizuku 中授权本应用"
                }
                else -> {
                    _phase.value = Phase.NEED_PERMISSION
                    _detail.value = "等待授权"
                }
            }
        } catch (t: Throwable) {
            _phase.value = Phase.NO_BINDER
            _detail.value = "Shizuku 不可用: ${t.javaClass.simpleName}"
        }
    }

    fun requestPermission() {
        try {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        } catch (t: Throwable) {
            _detail.value = "请求权限失败: ${t.message}"
        }
    }

    /** 拉起（或复用）文件服务 */
    fun ensureBound(force: Boolean = false) {
        val a = ensureArgs() ?: return
        if (!force && (_service.value != null || binding)) {
            if (_service.value != null) _phase.value = Phase.READY
            return
        }
        binding = true
        _phase.value = Phase.BINDING
        _detail.value = "正在拉起文件服务…"
        try {
            Shizuku.bindUserService(a, connection)
        } catch (t: Throwable) {
            binding = false
            _phase.value = Phase.NEED_PERMISSION
            _detail.value = "绑定失败: ${t.message}"
            Log.e(TAG, "bindUserService failed", t)
        }
    }

    fun unbind(remove: Boolean = true) {
        val a = args ?: return
        runCatching { Shizuku.unbindUserService(a, connection, remove) }
        _service.value = null
        binding = false
    }

    /** 挂起等待服务就绪（会先触发一次状态刷新） */
    suspend fun awaitService(timeoutMs: Long = 15_000): IRemoteService {
        _service.value?.let { return it }
        refresh()
        return withTimeout(timeoutMs) { _service.filterNotNull().first() }
    }

    private fun onBinderAlive(alive: Boolean) {
        Log.i(TAG, "binder alive=$alive")
        ForgeLogger.sys("Shizuku", "binder alive=$alive", 1)
        if (!alive) {
            _service.value = null
            binding = false
            _phase.value = Phase.NO_BINDER
            _detail.value = "Shizuku 服务已断开"
        } else {
            refresh()
        }
    }

    private fun ensureArgs(): Shizuku.UserServiceArgs? {
        args?.let { return it }
        val ctx = appContext ?: return null
        val a = Shizuku.UserServiceArgs(
            ComponentName(ctx.packageName, RemoteFsService::class.java.name)
        )
            .daemon(false)                 // App 进程退出后自动回收
            .tag(SERVICE_TAG)              // 防止混淆后类名变化导致服务错乱
            .version(2)                    // 服务代码升级时 +1，Shizuku 会重建
            .processNameSuffix("fs")
            .debuggable(BuildConfig.DEBUG)
        args = a
        return a
    }
}
