#!/data/data/com.termux/files/usr/bin/bash
# ============================================================
#  BiliForge 依赖探测脚本
#  在【翻墙环境】的 Termux 中运行：
#      bash biliforge-probe.sh            # 完整探测
#      bash biliforge-probe.sh --pack     # 额外打包 Termux 版 ffmpeg
#
#  产出：
#   1. 所有 Google Maven / Maven Central 依赖的最新版本号
#   2. ffmpeg(Android/arm64) 候选：自动下载 → 试运行 → ELF/16KB 对齐检测
#   3. 验证通过的 ffmpeg 自动复制到 /sdcard/BiliForge/tools/
#   4. 报告文件：/sdcard/BiliForge/biliforge-probe-<时间>.txt
#      （把这个报告文件发给 BiliForge 开发者即可）
# ============================================================
set -u
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
OUT=/sdcard/BiliForge
mkdir -p "$OUT" 2>/dev/null
if [ ! -w "$OUT" ]; then OUT="$HOME/biliforge"; mkdir -p "$OUT"; fi
TOOLS="$OUT/tools"
mkdir -p "$TOOLS" 2>/dev/null
TS=$(date +%Y%m%d-%H%M%S)
REPORT="$OUT/biliforge-probe-$TS.txt"
WORK="$HOME/.cache/biliforge-probe"
mkdir -p "$WORK"

log(){ printf '%s\n' "$*" | tee -a "$REPORT"; }
section(){ log ""; log "=============================================="; log "  $*"; log "=============================================="; }

log "BiliForge Probe Report"
log "时间: $(date)"
log "设备: $(getprop ro.product.model 2>/dev/null) / Android $(getprop ro.build.version.release 2>/dev/null) / $(getprop ro.product.cpu.abi 2>/dev/null)"
log "报告: $REPORT"

command -v curl >/dev/null 2>&1 || { log "!! 缺少 curl：请先执行 pkg install curl"; exit 1; }

# ---------------- 1) 版本号收集 ----------------
section "1) 依赖版本号（Google Maven + Maven Central）"

gmeta(){
  local name="$1" url="$2" xml latest release tailv
  xml=$(curl -sL --max-time 25 "$url" 2>/dev/null)
  if [ -z "$xml" ]; then log "[$name] 拉取失败"; log "    $url"; return; fi
  latest=$(printf '%s' "$xml" | grep -o '<latest>[^<]*' | head -1 | sed 's/<latest>//')
  release=$(printf '%s' "$xml" | grep -o '<release>[^<]*' | head -1 | sed 's/<release>//')
  tailv=$(printf '%s' "$xml" | grep -o '<version>[^<]*' | sed 's/<version>//' | tail -6 | tr '\n' ' ')
  log "[$name]"
  log "    release = ${release:-?}"
  log "    latest  = ${latest:-?}"
  log "    最近版本 = $tailv"
}

G=https://dl.google.com/dl/android/maven2
M=https://repo1.maven.org/maven2

gmeta "AGP(com.android.tools.build:gradle)"  "$G/com/android/tools/build/gradle/maven-metadata.xml"
gmeta "compose-bom"                          "$G/androidx/compose/compose-bom/maven-metadata.xml"
gmeta "media3-transformer"                   "$G/androidx/media3/media3-transformer/maven-metadata.xml"
gmeta "material3"                            "$G/androidx/compose/material3/material3/maven-metadata.xml"
gmeta "activity-compose"                     "$G/androidx/activity/activity-compose/maven-metadata.xml"
gmeta "lifecycle-viewmodel-compose"          "$G/androidx/lifecycle/lifecycle-viewmodel-compose/maven-metadata.xml"
gmeta "core-ktx"                             "$G/androidx/core/core-ktx/maven-metadata.xml"
gmeta "datastore-preferences"                "$G/androidx/datastore/datastore-preferences/maven-metadata.xml"
gmeta "work-runtime-ktx"                     "$G/androidx/work/work-runtime-ktx/maven-metadata.xml"
gmeta "navigation-compose"                   "$G/androidx/navigation/navigation-compose/maven-metadata.xml"
gmeta "shizuku-api"                          "$M/dev/rikka/shizuku/api/maven-metadata.xml"
gmeta "shizuku-provider"                     "$M/dev/rikka/shizuku/provider/maven-metadata.xml"
gmeta "kotlin-gradle-plugin"                 "$M/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml"
gmeta "kotlinx-coroutines-android"           "$M/org/jetbrains/kotlinx/kotlinx-coroutines-android/maven-metadata.xml"

# ---------------- 2) ffmpeg 探测 ----------------
section "2) ffmpeg for Android / arm64"

CHECK_ELF(){
  local bin="$1" tool=""
  for t in llvm-readelf readelf; do command -v "$t" >/dev/null 2>&1 && { tool="$t"; break; }; done
  if [ -z "$tool" ]; then
    log "    (未安装 readelf：可 pkg install binutils 后重跑，检查 16KB 对齐)"
    return
  fi
  log "    —— ELF 信息 ——"
  "$tool" -h "$bin" 2>/dev/null | grep -Ei 'class|machine|type:' | sed 's/^/      /' | tee -a "$REPORT"
  local aligns
  aligns=$("$tool" -lW "$bin" 2>/dev/null | awk '$1=="LOAD"{print $NF}' | sort -u | tr '\n' ' ')
  log "    LOAD 对齐 = $aligns   <-- Android16/16KB页面要求含 0x4000"
  "$tool" -d "$bin" 2>/dev/null | grep -Ei 'needed|rpath|runpath|interpreter' | sed 's/^/      /' | head -25 | tee -a "$REPORT"
}

VERIFY(){
  local bin="$1" out
  [ -f "$bin" ] || return 1
  chmod +x "$bin" 2>/dev/null
  out=$("$bin" -version 2>/dev/null | head -1)
  if [ -n "$out" ]; then
    log "    ✅ 可直接执行: $out"
    return 0
  fi
  log "    ❌ 不能直接执行（缺依赖或非 Android 构建）"
  return 1
}

fetch_url(){ local url="$1" out="$2"; log "    下载: $(basename "$url")"; curl -sL --max-time 150 -o "$out" "$url" && [ -s "$out" ]; }

try_repo(){
  local repo="$1" json urls u name cand
  log ""
  log "  ── GitHub: $repo ──"
  json=$(curl -sL --max-time 25 "https://api.github.com/repos/$repo/releases/latest" 2>/dev/null)
  urls=$(printf '%s' "$json" | grep -o '"browser_download_url": *"[^"]*"' | sed 's/.*"\(http[^"]*\)"/\1/' | grep -iE 'arm64|aarch64' | head -6)
  if [ -z "$urls" ]; then log "    未发现 arm64 资产"; return 1; fi
  for u in $urls; do
    name=$(basename "$u")
    log "    资产: $name"
    case "$name" in
      *.tar.gz|*.tgz)
        fetch_url "$u" "$WORK/$name" || continue
        rm -rf "$WORK/x-$name"; mkdir -p "$WORK/x-$name"
        tar xzf "$WORK/$name" -C "$WORK/x-$name" 2>/dev/null
        cand=$(find "$WORK/x-$name" -type f -name ffmpeg 2>/dev/null | head -1)
        if [ -n "$cand" ] && VERIFY "$cand"; then
          CHECK_ELF "$cand"
          cp "$cand" "$TOOLS/ffmpeg-$(echo "$repo" | tr '/' '_')" && log "    → 已复制: $TOOLS/ffmpeg-$(echo "$repo" | tr '/' '_')"
          return 0
        fi
        ;;
      *.zip)
        fetch_url "$u" "$WORK/$name" || continue
        rm -rf "$WORK/x-$name"; mkdir -p "$WORK/x-$name"
        unzip -o -q "$WORK/$name" -d "$WORK/x-$name" 2>/dev/null
        cand=$(find "$WORK/x-$name" -type f -name ffmpeg 2>/dev/null | head -1)
        if [ -n "$cand" ] && VERIFY "$cand"; then
          CHECK_ELF "$cand"
          cp "$cand" "$TOOLS/ffmpeg-$(echo "$repo" | tr '/' '_')" && log "    → 已复制: $TOOLS/ffmpeg-$(echo "$repo" | tr '/' '_')"
          return 0
        fi
        ;;
      *)
        fetch_url "$u" "$WORK/$name" || continue
        if VERIFY "$WORK/$name"; then
          CHECK_ELF "$WORK/$name"
          cp "$WORK/$name" "$TOOLS/ffmpeg-$(echo "$repo" | tr '/' '_')" && log "    → 已复制: $TOOLS/ffmpeg-$(echo "$repo" | tr '/' '_')"
          return 0
        fi
        ;;
    esac
  done
  return 1
}

try_repo "Khang-NT/ffmpeg-binary-android"
try_repo "Javernaut/ffmpeg-android-maker"

log ""
log "  更多候选参考（GitHub 搜索）:"
curl -sL --max-time 20 "https://api.github.com/search/repositories?q=ffmpeg+android+static+binary&sort=updated&per_page=8" 2>/dev/null \
  | grep -E '"full_name"|"description"' | head -16 | sed 's/^[ \t]*/    /' | tee -a "$REPORT"

if command -v ffmpeg >/dev/null 2>&1; then
  log ""
  log "  Termux 本地 ffmpeg: $(command -v ffmpeg)"
  log "    $(ffmpeg -version 2>/dev/null | head -1)"
fi

# ---------------- 3) (可选) 打包 Termux ffmpeg ----------------
collect_deps(){
  local bin="$1" seen="$2" lib
  ldd "$bin" 2>/dev/null | awk '/=>/{print $3}' | grep -v '^$' | while read -r lib; do
    case "$lib" in
      "$PREFIX"/*)
        if ! grep -qxF "$lib" "$seen" 2>/dev/null; then
          echo "$lib" >> "$seen"
          collect_deps "$lib" "$seen"
        fi ;;
    esac
  done
}

if [ "${1:-}" = "--pack" ]; then
  section "3) 打包 Termux ffmpeg"
  if ! command -v ffmpeg >/dev/null 2>&1; then
    log "未安装 ffmpeg：请 pkg install ffmpeg 后重跑"
  else
    BINFF=$(command -v ffmpeg)
    STAGE="$WORK/pack"
    rm -rf "$STAGE"; mkdir -p "$STAGE/bin" "$STAGE/lib"
    cp "$BINFF" "$STAGE/bin/ffmpeg"
    SEEN="$WORK/seen.txt"; : > "$SEEN"
    collect_deps "$BINFF" "$SEEN"
    collect_deps "$BINFF" "$SEEN"   # 第二轮，把递归中新发现的依赖也收全
    n=0
    while read -r lib; do
      [ -f "$lib" ] || continue
      cp "$lib" "$STAGE/lib/" 2>/dev/null && n=$((n+1))
    done < "$SEEN"
    tar czf "$TOOLS/ffmpeg-termux.tar.gz" -C "$STAGE" . 2>/dev/null
    log "已打包 $n 个依赖库 → $TOOLS/ffmpeg-termux.tar.gz"
    log "（App 安装后会：ffmpeg 与所有 .so 放同一目录，并以该目录为 LD_LIBRARY_PATH 运行）"
  fi
fi

section "完成"
log "报告文件: $REPORT"
log "请把此文件发给 BiliForge 开发者。"
log "如果 2) 中已有 ✅ 可用的 ffmpeg，已自动复制到: $TOOLS"
