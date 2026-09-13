#!/data/data/com.termux/files/usr/bin/bash
# BiliForge · Termux 一键打包 ffmpeg（防呆版）
# 用法: bash biliforge-pack-termux.sh
# 产物: /sdcard/ffmpeg-pack.tar.gz  (bin/ffmpeg + lib/*.so)
set -e
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
S="$HOME/ffp"
rm -rf "$S"; mkdir -p "$S/bin" "$S/lib"
FF="$(command -v ffmpeg)"
echo "ffmpeg: $FF"
"$FF" -version | head -1
cp "$FF" "$S/bin/ffmpeg"

F="$HOME/.ffseen"; : > "$F"
collect() {
  ldd "$1" 2>/dev/null | awk '/=>/{print $3}' | grep -v '^$' | while read -r l; do
    case "$l" in
      "$PREFIX"/*)
        if ! grep -qxF "$l" "$F" 2>/dev/null; then
          echo "$l" >> "$F"
          collect "$l"
        fi ;;
    esac
  done
}
collect "$S/bin/ffmpeg"
collect "$S/bin/ffmpeg"
while read -r l; do
  if [ -f "$l" ]; then cp -n "$l" "$S/lib/" 2>/dev/null || true; fi
done < "$F"
echo "依赖库: $(ls "$S/lib" | wc -l) 个"

T="$HOME/fft"; rm -rf "$T"; cp -r "$S" "$T"; chmod +x "$T/bin/ffmpeg"
if LD_LIBRARY_PATH="$T/lib" "$T/bin/ffmpeg" -version >/dev/null 2>&1; then
  echo "✅ 自检通过"
else
  echo "⚠️ 自检失败:"; LD_LIBRARY_PATH="$T/lib" "$T/bin/ffmpeg" -version 2>&1 | head -5 || true
fi

tar czf /sdcard/ffmpeg-pack.tar.gz -C "$S" .
ls -lh /sdcard/ffmpeg-pack.tar.gz
echo "完成"
