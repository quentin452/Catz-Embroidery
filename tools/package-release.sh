#!/usr/bin/env bash
# Builds the Linux/Arch release binaries and assembles the distributable
# folder — the native-Linux counterpart of tools/package-release.ps1.
#
# Usage (on the Arch box, from the repo root):  bash tools/package-release.sh
#
# Output: <repo>/dist-linux/ — the four executables next to each other (the
# layout the launcher expects), plus a README.txt. The folder is git-ignored.
#
# Prereqs (Arch, pacman):
#   sudo pacman -S rustup    # then: rustup toolchain install 1.92.0
#   rustup target add x86_64-unknown-linux-gnu
#   sudo pacman -S base-devel libxkbcommon wayland wayland-protocols \
#        libxcb xcb-util xcb-util-wm xcb-util-keysyms \
#        libglvnd mesa
# The eframe/glutin stack needs the wayland + x11 features, which the
# workspace enables in apps/*/Cargo.toml (see the release skill).
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
dist="$root/dist-linux"
apps=(emb-editor emb-converter emb-viewer emb-launcher)

cd "$root"
cargo build --release -p emb-editor -p emb-converter -p emb-viewer -p emb-launcher

mkdir -p "$dist"
for app in "${apps[@]}"; do
    cp -f "target/release/$app" "$dist/$app"
done

# The workspace version (Cargo.toml), e.g. 0.1.0.
version="$(sed -n 's/^version = "\([^"]*\)"/\1/p' Cargo.toml | head -n1)"
version="${version:-0.1.0}"

cat > "$dist/README.txt" <<EOF
Catz-Embroidery $version - the embroidery suite (editor, converter, viewer, launcher).

Run ./emb-launcher to pick an app. Each app can also be run directly.

The launcher checks GitHub for a newer release at startup and offers the
releases page when one exists: https://github.com/quentin452/Catz-Embroidery/releases
EOF

echo "Packaged ${#apps[@]} binaries + README.txt into $dist"
