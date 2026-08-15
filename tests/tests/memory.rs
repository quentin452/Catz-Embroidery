//! Gate: holds `memory/MEMORY.md` to the directory it indexes.
//!
//! An entry written and not indexed exists on disk and for nobody; a referenced
//! entry that is gone is a stale claim. Both directions fail the build.
//!
//! `expect`/`unwrap` here: this whole file is a test — a failure IS the report
//! (clippy.toml's allow-expect-in-tests does not cover integration tests).

#![allow(clippy::expect_used, clippy::unwrap_used)]

use std::path::{Path, PathBuf};

fn workspace_root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR"))
        .parent()
        .expect("gates crate sits directly under the workspace root")
        .to_path_buf()
}

fn entry_files(dir: &Path) -> Vec<String> {
    let mut out = Vec::new();
    for entry in std::fs::read_dir(dir).expect("memory/ must exist") {
        let entry = entry.expect("read_dir entry");
        let name = entry.file_name().to_string_lossy().into_owned();
        if name.ends_with(".md") && name != "MEMORY.md" {
            out.push(name);
        }
    }
    out.sort();
    out
}

/// Filenames referenced in the `## Index` section: the first token of each `- ` line.
fn referenced(dir: &Path) -> Vec<String> {
    let raw = std::fs::read_to_string(dir.join("MEMORY.md")).expect("memory/MEMORY.md must exist");
    let (_, rest) = raw
        .split_once("## Index")
        .expect("MEMORY.md must carry an `## Index` section");
    rest.lines()
        .map(str::trim)
        .filter(|line| line.starts_with("- "))
        .filter_map(|line| line.strip_prefix("- "))
        .map(|line| {
            line.split_whitespace()
                .next()
                .unwrap_or_default()
                .to_string()
        })
        .filter(|name| !name.is_empty())
        .collect()
}

#[test]
fn index_lists_every_entry() {
    let dir = workspace_root().join("memory");
    let files = entry_files(&dir);
    let index = referenced(&dir);
    for file in &files {
        assert!(
            index.contains(file),
            "memory entry {file} is not referenced in MEMORY.md's index"
        );
    }
}

#[test]
fn every_reference_exists() {
    let dir = workspace_root().join("memory");
    let files = entry_files(&dir);
    let index = referenced(&dir);
    for name in &index {
        assert!(
            files.contains(name),
            "MEMORY.md references {name}, which is not a memory entry on disk"
        );
    }
}
