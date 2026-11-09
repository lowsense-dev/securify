#!/usr/bin/env bash
# Packs the Compose distributable into an AppImage: appimage.sh <distributable dir> <work dir> <output file>
set -euo pipefail

app="$1"; work="$2"; out="$3"
here="$(cd "$(dirname "$0")" && pwd)"
tools="$work/tools"
mkdir -p "$tools"

fetch() { # url sha256 file
    if [ ! -f "$3" ] || ! echo "$2  $3" | sha256sum -c --status; then
        curl -fsSL -o "$3" "$1"
        echo "$2  $3" | sha256sum -c --status || { echo "checksum mismatch: $1" >&2; rm -f "$3"; exit 1; }
    fi
    chmod +x "$3"
}
fetch https://github.com/AppImage/appimagetool/releases/download/1.9.1/appimagetool-x86_64.AppImage \
    ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0 "$tools/appimagetool"
fetch https://github.com/AppImage/type2-runtime/releases/download/20251108/runtime-x86_64 \
    2fca8b443c92510f1483a883f60061ad09b46b978b2631c807cd873a47ec260d "$tools/runtime-x86_64"

dir="$work/Securify.AppDir"
rm -rf "$dir"
mkdir -p "$dir/usr"
cp -a "$app/." "$dir/usr/"
cp "$here/src/main/resources/securify.svg" "$dir/securify.svg"
ln -s securify.svg "$dir/.DirIcon"
cp "$here/securify.desktop" "$dir/securify.desktop"
cat > "$dir/AppRun" <<'EOF'
#!/bin/sh
exec "$APPDIR/usr/bin/Securify" "$@"
EOF
chmod +x "$dir/AppRun"

mkdir -p "$(dirname "$out")"
rm -f "$out"
APPIMAGE_EXTRACT_AND_RUN=1 ARCH=x86_64 "$tools/appimagetool" --no-appstream --runtime-file "$tools/runtime-x86_64" "$dir" "$out"
