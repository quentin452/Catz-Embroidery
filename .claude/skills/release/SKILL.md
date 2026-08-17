---
name: release
description: >-
  Cut a new tagged Catz-Embroidery release to GitHub (quentin452/Catz-Embroidery): finalize the
  changelog, build the shipped executables for the target platform (Windows and/or Linux/Arch),
  push main + a semver tag, and publish a gh release with curated notes and the zip asset. Use
  when the user says "release", "sortons la mise à jour", "cut a version", "publish to github",
  "new release", or names a version to ship.
---

# Release a new Catz-Embroidery version

Publishes a versioned GitHub release. This is **outward-facing and hard to undo** — confirm the
version number and title with the user before the `gh release create` step (the tag + release are
public once pushed). Prior releases: v0.1.0 (the first Rust release, 2026-08-17).

## Conventions (match the existing release)

- **Target repo / remote:** `origin` = `quentin452/Catz-Embroidery`. `gh` is authed as
  `quentin452`.
- **Branch:** release from `main` (the Rust rewrite). Merge the working branch (`rs-greenfield`)
  into `main` first if it is ahead, then tag on `main`.
- **Tag:** `vX.Y.Z` (annotated). Minor bump when the cycle adds user-facing features; patch bump
  for a fixes-only cycle. Rust is pre-1.0, so 0.1.0 → 0.2.0 on a feature cycle.
- **Release title:** `vX.Y.Z — <short theme>` (em dash). Theme = the 1–3 biggest user-facing items.
- **Asset:** attach a **zip** of the built executables. The build is per-BOX, so resolve it, don't
  assume it:
  | platform | build script | output dir |
  |---|---|---|
  | Windows | `tools/package-release.ps1` | `dist/` (4 `.exe` + README.txt) |
  | Linux/Arch | **cross-compile from a Windows box** (the standard, validated 2026-08-17): `cargo zigbuild --release --target x86_64-unknown-linux-gnu -p emb-editor -p emb-converter -p emb-viewer -p emb-launcher`, then assemble `dist-linux/` + `python tools/zip-linux.py dist-linux <zip>`. The native `tools/package-release.sh` on the Arch box remains an optional alternative. |
  Zip the output dir and attach one zip per platform the release covers. The launcher's update
  check only opens the releases page — it does not download assets, so a zip (not the raw exes) is
  the right shape.
- **Notes:** curated, user-facing markdown (see v0.1.0's notes for the house style — a headline
  summary, a what's-in-the-release section, an Install line). Derive from the changelog's version
  block, NOT raw git log.

## Steps

1. **Determine scope.** `git log --oneline <lastTag>..HEAD` and read `docs/CHANGELOG.md`'s
   `[Unreleased]`. Decide the version bump (feature → minor, fixes-only → patch). Confirm version +
   title with the user (use the question tool; recommend the semver-correct bump).

2. **Finalize the changelog.** The `[Unreleased]` block is the release body-in-progress. Add any
   user-facing change from `<lastTag>..HEAD` that is missing, following the changelog discipline
   (a `Fixed`/`Performance` entry only for a defect present in the last shipped tag). Then rename
   the block: `## [Unreleased]` → leave empty, insert `## [vX.Y.Z] - YYYY-MM-DD` below it. Commit:
   `docs(changelog): cut vX.Y.Z — <theme>`.

3. **Build the shipped binaries fresh from HEAD.** Windows (from a Windows box): `powershell
   -ExecutionPolicy Bypass -File tools/package-release.ps1`. Linux (also from the Windows box,
   cross-compiled — the standard): see the table in the conventions above. The gates must be green
   first (`cargo test --workspace`, `cargo clippy --workspace --all-targets`, `cargo fmt --check`).
   Check the output binaries' mtime is from THIS build — attaching a stale binary is a silent
   error that is public the moment the release is created.

4. **Zip each output dir.** Windows (`dist/` → `Catz-Embroidery-<v>-win64.zip`) via
   `Compress-Archive`. Linux (`dist-linux/` → `Catz-Embroidery-<v>-linux-x86_64.zip`) via
   `python tools/zip-linux.py dist-linux <zip>` — NOT Compress-Archive. Two pitfalls, both hit
   2026-08-17 on the draft: Compress-Archive drops the Unix executable bit (every binary fails
   with "permission denied (os error 13)" when the launcher spawns a sibling), AND a Python
   zipfile whose entries declare host 0 (DOS) makes Info-ZIP `unzip` ignore the Unix mode
   entirely. The helper writes `create_system = 3` (Unix) + mode 0o755 on the binaries, 0o644
   on README. Verify before publishing: `python tools/zip-linux.py --check <zip>` (non-zero exit
   on a missing exec bit OR a non-Unix host, both of which unzip would not apply).

5. **Push main:** `git fetch origin`; `git push origin main`. (The user normally pushes, but a
   release explicitly authorizes it.)

6. **Tag + push tag:** `git tag -a vX.Y.Z -m "vX.Y.Z — <theme>"`; `git push origin vX.Y.Z`.

7. **Write notes** to a scratchpad file (house style — headline the marquee features, then an
   Install line naming the platforms covered), then publish:
   ```
   gh release create vX.Y.Z --repo quentin452/Catz-Embroidery \
     --title "vX.Y.Z — <theme>" --notes-file <notes.md> \
     Catz-Embroidery-<v>-win64.zip [Catz-Embroidery-<v>-linux-x86_64.zip]
   ```

8. **Verify:** `gh release view vX.Y.Z --repo quentin452/Catz-Embroidery` — confirm
   `draft:false`, the tag, and the zip asset(s). Report the release URL to the user.

## Notes

- **Linux/Arch is cross-compiled from Windows** (2026-08-17, validated on the Arch box): all 4
  apps build with `cargo zigbuild --release --target x86_64-unknown-linux-gnu`. The workspace is
  Rust/egui (eframe + glow); the eframe `wayland` and `x11` features are enabled in all four apps
  (`apps/*/Cargo.toml`). The launcher's update check uses ureq's `tls` (rustls, pure-Rust) so it
  cross-compiles with no system openssl. The Arch box still needs the system libs
  `tools/package-release.sh` lists at RUNTIME (libxkbcommon, wayland-protocols, xcb utils,
  libglvnd, mesa); the binaries only link glibc ≤ 2.27. The native `tools/package-release.sh`
  build on the Arch box stays as an optional alternative. The zip needs `tools/zip-linux.py`
  (exec bit AND Unix host marker — see step 4).
- Do NOT touch other remotes (there is no upstream; `origin` is the only one).
- A release is public immediately (not a draft): if a regression is suspected in the built
  binaries, flag it to the user before publishing.
