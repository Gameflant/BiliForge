// BiliForge · Shizuku 远程服务（运行在 ADB shell 身份进程）
// 职责：① 访问 /sdcard/Android/data/tv.danmaku.bili 缓存目录
//       ② 以 shell 身份执行 ffmpeg、管理工具链（/data/local/tmp/biliforge）
package com.feringsapp.biliforge.core.shizuku;

import android.os.ParcelFileDescriptor;

interface IRemoteService {

    /** Shizuku 保留方法：结束用户服务进程（code 由 Shizuku 定义，不可改） */
    void destroy() = 16777114;

    /** 普通退出 */
    void exit() = 1;

    /** 环境信息：uid / pid / sdk / abi / selinux */
    String info() = 2;

    boolean pathExists(String path) = 3;

    boolean isDirectory(String path) = 4;

    /** 文件大小；不存在或目录返回 -1 */
    long fileSize(String path) = 5;

    /** 列目录；条目编码: name \u0001 size \u0001 mtime \u0001 isDir(0|1) */
    String[] listDir(String path) = 6;

    /** 从 offset 读 length 字节；失败返回 null */
    byte[] readChunk(String path, long offset, int length) = 7;

    /** 读小文本文件（entry.json 等），最多 maxBytes 字节 */
    String readTextFile(String path, int maxBytes) = 8;

    boolean deleteRecursive(String path) = 9;

    boolean mkdirs(String path) = 10;

    /** 高吞吐通道：从 offset 读 length 字节写入调用方提供的 fd，返回字节数；失败 -1 */
    int readIntoFd(String path, long offset, int length, in ParcelFileDescriptor fd) = 11;

    // ---------- 进程 / 工具链（ffmpeg 引擎）----------

    /** 启动子进程（如 ffmpeg），输出追加到 logPath；返回句柄(>=1)，失败 -1 */
    int spawn(in String[] argv, String cwd, String logPath) = 12;

    /** 进程是否存活 */
    boolean processAlive(int handle) = 13;

    /** 进程退出码；仍在运行返回 -999，句柄无效返回 -404 */
    int processExitCode(int handle) = 14;

    /** 结束进程 */
    boolean killProcess(int handle) = 15;

    /** 增量读文本：返回 "<newOffset>\u0001<content>"；失败返回 null */
    String readTextFileFrom(String path, long offset, int maxBytes) = 16;

    /** 拷贝文件并 chmod 0755（安装可执行工具） */
    boolean installExecutable(String srcPath, String dstDir, String name) = 17;

    /** 用系统 tar 解压 .tar / .tar.gz / .tgz 到目标目录 */
    boolean untar(String archivePath, String dstDir) = 18;

    /** 写文本文件（弹幕 ASS/SRT 等输出）；append=true 时追加 */
    boolean writeTextFile(String path, String content, boolean append) = 19;

    /** 分块写二进制（offset 定位，文件不存在则创建） */
    boolean writeChunk(String path, long offset, in byte[] data) = 20;

    /** 就地修改文件权限（0755 = 493） */
    boolean chmod(String path, int mode) = 21;
}
