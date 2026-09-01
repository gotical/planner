#!/usr/bin/env bash
# Генерация PNG-иконок приложения из SVG-исходников.
# Требует: rsvg-convert (или inkscape).
set -e
cd "$(dirname "$0")/.."
RES="$(pwd)/res"

if [ ! -f "$(pwd)/tools/planner_logo.svg" ]; then
  echo "tools/planner_logo.svg не найден"
  exit 1
fi

gen() {
  local size=$1; local dir=$2; local file=$3; local src=$4
  if command -v rsvg-convert >/dev/null 2>&1; then
    rsvg-convert -w "$size" -h "$size" "$src" -o "$RES/$dir/$file"
  elif command -v convert >/dev/null 2>&1; then
    convert -background none -density 1200 "$src" -resize ${size}x${size} "$RES/$dir/$file"
  else
    echo "Не найден rsvg-convert или convert (ImageMagick)" >&2
    exit 1
  fi
}

SRC=$(pwd)/tools/planner_logo.svg
ROUND=$(pwd)/tools/planner_logo_round.svg

gen 48  mipmap-mdpi    ic_launcher.png       "$SRC"
gen 48  mipmap-mdpi    ic_launcher_round.png "$ROUND"
gen 72  mipmap-hdpi    ic_launcher.png       "$SRC"
gen 72  mipmap-hdpi    ic_launcher_round.png "$ROUND"
gen 96  mipmap-xhdpi   ic_launcher.png       "$SRC"
gen 96  mipmap-xhdpi   ic_launcher_round.png "$ROUND"
gen 144 mipmap-xxhdpi  ic_launcher.png       "$SRC"
gen 144 mipmap-xxhdpi  ic_launcher_round.png "$ROUND"
gen 192 mipmap-xxxhdpi ic_launcher.png       "$SRC"
gen 192 mipmap-xxxhdpi ic_launcher_round.png "$ROUND"

echo "OK: иконки созданы во всех плотностях."
