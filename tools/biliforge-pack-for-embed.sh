#!/data/data/com.termux/files/usr/bin/bash
# ============================================================
#  BiliForge 内置引擎打包脚本（一次性）
#
#  用法（Termux，需翻墙环境）：
#      bash biliforge-pack-for-embed.sh
#
#  产物：
#      /sdcard/ffmpeg-pack.tar.gz   ← 把这个文件上传给开发者
#      （结构: bin/ffmpeg + lib/*.so）
# ============================================================
set -e
echo "== 1/4 安装依赖 =="
pkg install -y ffmpeg binutils 2>&1 | tail -1

STAGE=$HOME/.cache/biliforge-pack
rm -rf "$STAGE" && mkdir -p "$STAGE/bin" "$STAGE/lib"

FF=$(command -v ffmpeg)
echo "== 2/4 收集 ffmpeg 及其依赖 =="
echo "   ffmpeg: $FF"
echo "   version: $($FF -version 2>/dev/null | head -1)"
cp "$FF" "$STAGE/bin/ffmpeg"

SEEN=$HOME/.cache/biliforge-seen.txt
: > "$SEEN"
collect() {
  ldd "$1" 2>/dev/null | awk '/=>/{print $3}' | grep -v '^$' | while read -r lib; do
    case "$lib" in
      "$PREFIX"/*)
        if ! grep -qxF "$lib" "$SEEN" 2>/dev/null; then
          echo "$lib" >> "$SEEN"
          collect "$lib"
        fi ;;
    esac
  done
}
collect "$STAGE/bin/ffmpeg"
collect "$STAGE/bin/ffmpeg"   # 第二轮补齐递归新发现

while read -r lib; do
  if [ -f "$lib" ]; then cp -n "$lib" "$STAGE/lib/" 2>/dev/null || true; fi
done < "$SEEN"
echo "   依赖库数量: $(ls "$STAGE/lib" | wc -l)"

echo "== 3/4 自检（模拟解包执行） =="
TEST=$HOME/.cache/biliforge-test
rm -rf "$TEST" && mkdir -p "$TEST"
cp -r "$STAGE/." "$TEST/"
chmod +x "$TEST/bin/ffmpeg"
if LD_LIBRARY_PATH="$TEST/lib" "$TEST/bin/ffmpeg" -version >/dev/null 2>&1; then
  echo "   ✅ 自检通过：解包环境可直接执行"
else
  echo "   ⚠️ 自检失败，输出如下（请连同报告一起发给开发者）："
  LD_LIBRARY_PATH="$TEST/lib" "$TEST/bin/ffmpeg" -version 2>&1 | head -5 || true
fi

echo "== 4/4 打包 =="
tar czf /sdcard/ffmpeg-pack.tar.gz -C "$STAGE" .
ls -lh /sdcard/ffmpeg-pack.tar.gz
echo
echo "════════════════════════════════════════════"
echo "完成！请把 /sdcard/ffmpeg-pack.tar.gz 上传到对话"
echo "════════════════════════════════════════════"
