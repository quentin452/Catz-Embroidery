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
  | Linux/Arch | `tools/package-release.sh` | `dist-linux/` (4 binaries + README.txt) |
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

3. **Build the shipped binaries fresh from HEAD.** Run the packaging script for each platform the
   release covers. Windows (from a Windows box): `powershell -ExecutionPolicy Bypass -File
   tools/package-release.ps1`. Linux/Arch (from the Arch box): `bash tools/package-release.sh`.
   The gates must be green first (`cargo test --workspace`, `cargo clippy --workspace
   --all-targets`, `cargo fmt --check`). Check the output binaries' mtime is from THIS build —
   attaching a stale binary is a silent error that is public the moment the release is created.

4. **Zip each output dir** (`dist/` → `Catz-Embroidery-<v>-win64.zip`,
   `dist-linux/` → `Catz-Embroidery-<v>-linux-x86_64.zip`).

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

- **Linux/Arch is built natively on the Arch box, not cross-compiled.** The workspace is
  Rust/egui (eframe + glow); the eframe `wayland` and `x11` features are enabled in all four apps
  (`apps/*/Cargo.toml`). The Arch box needs the system libs `tools/package-release.sh` lists
  (libxkbcommon, wayland-protocols, xcb utils, libglvnd, mesa). There is no Linux cross-toolchain;
  do not attempt to cross-compile from Windows.
- Do NOT touch other remotes (there is no upstream; `origin` is the only one).
- A release is public immediately (not a draft): if a regression is suspected in the built
  binaries, flag it to the user before publishing.
