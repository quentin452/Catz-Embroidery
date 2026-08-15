//! Gate: holds `matrix.toml` to the workspace manifests.
//!
//! Every workspace crate must have a matrix row, every row must name a real crate,
//! and every dependency edge between workspace crates — dev- and build-dependencies
//! included, because a test-only edge is still an edge — must be declared in the
//! row's `allow` list. `allow = "all"` permits every workspace crate.
//!
//! `expect`/`unwrap` here: this whole file is a test — a failure IS the report
//! (clippy.toml's allow-expect-in-tests does not cover integration tests).

#![allow(clippy::expect_used, clippy::unwrap_used)]

use std::collections::BTreeSet;
use std::path::{Path, PathBuf};

use toml::Value;

fn workspace_root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR"))
        .parent()
        .expect("gates crate sits directly under the workspace root")
        .to_path_buf()
}

/// The package names of every workspace member, derived from the root manifest.
fn workspace_members(root: &Path) -> Vec<(String, PathBuf)> {
    let raw =
        std::fs::read_to_string(root.join("Cargo.toml")).expect("workspace Cargo.toml must exist");
    let table: toml::Table = toml::from_str(&raw).expect("workspace Cargo.toml must parse");
    let members = table
        .get("workspace")
        .and_then(Value::as_table)
        .expect("workspace table must exist")
        .get("members")
        .and_then(Value::as_array)
        .expect("workspace members must be an array");

    let mut out = Vec::new();
    for member in members {
        let rel = member.as_str().expect("member paths must be strings");
        let manifest = root.join(rel).join("Cargo.toml");
        let pkg = std::fs::read_to_string(&manifest)
            .unwrap_or_else(|e| panic!("member manifest {} unreadable: {e}", manifest.display()));
        let pkg: toml::Table = toml::from_str(&pkg).expect("member manifest must parse");
        let name = pkg
            .get("package")
            .and_then(Value::as_table)
            .and_then(|p| p.get("name"))
            .and_then(Value::as_str)
            .expect("member manifest must declare package.name")
            .to_string();
        out.push((name, manifest));
    }
    out
}

/// Workspace-internal edges a manifest declares, across every dependency section.
fn declared_edges(manifest: &Path, members: &BTreeSet<String>) -> BTreeSet<String> {
    let raw = std::fs::read_to_string(manifest)
        .unwrap_or_else(|e| panic!("manifest {} unreadable: {e}", manifest.display()));
    let table: toml::Table = toml::from_str(&raw).expect("manifest must parse");

    let mut edges = BTreeSet::new();
    for section in ["dependencies", "dev-dependencies", "build-dependencies"] {
        if let Some(deps) = table.get(section).and_then(Value::as_table) {
            for name in deps.keys() {
                if members.contains(name) {
                    edges.insert(name.clone());
                }
            }
        }
    }
    edges
}

fn matrix_rows(root: &Path) -> toml::Table {
    let raw = std::fs::read_to_string(root.join("matrix.toml"))
        .expect("matrix.toml must exist at the workspace root");
    toml::from_str(&raw).expect("matrix.toml must parse")
}

#[test]
fn every_crate_is_in_the_matrix() {
    let root = workspace_root();
    let rows = matrix_rows(&root);
    for (name, manifest) in workspace_members(&root) {
        assert!(
            rows.contains_key(&name),
            "crate `{name}` ({}) has no row in matrix.toml",
            manifest.display()
        );
    }
}

#[test]
fn every_row_names_a_real_crate() {
    let root = workspace_root();
    let rows = matrix_rows(&root);
    let members: BTreeSet<String> = workspace_members(&root)
        .into_iter()
        .map(|(name, _)| name)
        .collect();
    for row in rows.keys() {
        assert!(
            members.contains(row),
            "matrix.toml row `{row}` names no crate in the workspace"
        );
    }
}

#[test]
fn every_edge_is_declared() {
    let root = workspace_root();
    let rows = matrix_rows(&root);
    let members: BTreeSet<String> = workspace_members(&root)
        .into_iter()
        .map(|(name, _)| name)
        .collect();

    for (name, manifest) in workspace_members(&root) {
        let row = rows
            .get(&name)
            .unwrap_or_else(|| panic!("crate `{name}` has no matrix row"));
        let allowed: BTreeSet<String> = match row.get("allow") {
            Some(Value::Array(list)) => list
                .iter()
                .map(|v| {
                    v.as_str()
                        .expect("allow entries must be strings")
                        .to_string()
                })
                .collect(),
            Some(Value::String(s)) if s == "all" => members.clone(),
            other => {
                panic!("matrix row `{name}`: `allow` must be a list or \"all\", got {other:?}")
            }
        };
        // An `allow` entry that names no crate is a typo the gate should catch.
        for a in &allowed {
            assert!(
                members.contains(a),
                "matrix row `{name}` allows `{a}`, which is not a workspace crate"
            );
        }

        for edge in declared_edges(&manifest, &members) {
            assert!(
                allowed.contains(&edge),
                "crate `{name}` declares a dependency on `{edge}` in {} but matrix.toml \
                 does not allow it",
                manifest.display()
            );
        }
    }
}
