//! The GitHub update check, ported from the Java's `Updater.java` (the
//! launcher checks once, on a background thread, and offers the releases
//! page when a newer version exists).
//!
//! Deviations, each measured from the Java:
//! - the Java compares the versions by plain string inequality, which flags
//!   `0.1.0` vs the conventional release tag `v0.1.0` as "outdated"; this
//!   port strips the leading `v`/`V` and compares numeric segments. A tag
//!   that is not numeric segments (a pre-release like `v0.2.0-rc1`) falls
//!   back to the string comparison — it cannot claim to be "newer" or
//!   "older" than a plain tag.
//! - the Java throws on any error (HTTP, missing `tag_name`) and logs it;
//!   the port returns the error as a string so the hub can show it instead
//!   of crashing the launcher.
//! - the check runs once per launcher process (the Java's `isUpdateChecked`
//!   static flag) — there is no refresh button (pin 3, no consumer).

use std::time::Duration;

/// The latest-release endpoint (the Java's `GITHUB_API_URL`).
const API_URL: &str = "https://api.github.com/repos/quentin452/Catz-Embroidery/releases/latest";

/// The releases page the browser opens on "Yes" (the Java's
/// `openBrowserToReleasesPage`).
const RELEASES_URL: &str = "https://github.com/quentin452/Catz-Embroidery/releases";

/// Is `latest` a strictly newer version than `current`? Release tags
/// conventionally carry a leading `v` (`v0.1.0`) while the crate version is
/// plain (`0.1.0`); the comparison strips it and walks the dot-separated
/// numeric segments, shorter lists padding with 0. A tag that is not
/// numeric segments falls back to the Java's string inequality.
pub fn is_outdated(current: &str, latest: &str) -> bool {
    match (numeric_segments(current), numeric_segments(latest)) {
        (Some(a), Some(b)) => {
            for i in 0..a.len().max(b.len()) {
                let (x, y) = (
                    a.get(i).copied().unwrap_or(0),
                    b.get(i).copied().unwrap_or(0),
                );
                if x < y {
                    return true;
                }
                if x > y {
                    return false;
                }
            }
            false
        }
        _ => strip_v(current) != strip_v(latest),
    }
}

fn strip_v(v: &str) -> &str {
    match v.strip_prefix(['v', 'V']) {
        Some(rest) if !rest.is_empty() => rest,
        _ => v,
    }
}

/// `0.1.0` -> Some([0, 1, 0]); `0.1.0-rc1` or a non-numeric tag -> None.
fn numeric_segments(v: &str) -> Option<Vec<u64>> {
    let v = strip_v(v);
    if v.is_empty() {
        return None;
    }
    let mut out = Vec::new();
    for part in v.split('.') {
        out.push(part.parse::<u64>().ok()?);
    }
    Some(out)
}

/// The latest release tag on GitHub (the Java's
/// `getLatestVersionFromGitHub`), or an error string. Fails when there is
/// no release yet (GitHub answers 404), the network is down, or the
/// response has no `tag_name` (the Java's regex).
pub fn fetch_latest_version() -> Result<String, String> {
    let resp = ureq::get(API_URL)
        .set("Accept", "application/vnd.github.v3+json")
        .timeout(Duration::from_secs(10))
        .call()
        .map_err(|e| format!("update check failed: {e}"))?;
    let body = resp
        .into_string()
        .map_err(|e| format!("update check failed: {e}"))?;
    body.split("\"tag_name\":\"")
        .nth(1)
        .and_then(|s| s.split('"').next())
        .map(str::to_string)
        .ok_or_else(|| "update check failed: no tag_name in the GitHub response".into())
}

/// Open the releases page in the default browser (the Java's
/// `Desktop.browse`). Best-effort: the launcher already showed the dialog,
/// so a failure here is not worth a popup.
pub fn open_releases_page() {
    let _ = match std::env::consts::OS {
        "windows" => std::process::Command::new("cmd")
            .args(["/C", "start", "", RELEASES_URL])
            .spawn(),
        "macos" => std::process::Command::new("open").arg(RELEASES_URL).spawn(),
        _ => std::process::Command::new("xdg-open")
            .arg(RELEASES_URL)
            .spawn(),
    };
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn equal_versions_are_not_outdated() {
        assert!(!is_outdated("0.1.0", "0.1.0"));
        assert!(!is_outdated("0.1.0", "v0.1.0"));
        assert!(!is_outdated("V0.1.0", "v0.1.0"));
    }

    #[test]
    fn newer_latest_is_outdated() {
        assert!(is_outdated("0.1.0", "0.2.0"));
        assert!(is_outdated("0.1.0", "1.0.0"));
        assert!(is_outdated("0.1.0", "v0.1.1"));
        assert!(is_outdated("0.9.9", "0.10.0"));
    }

    #[test]
    fn newer_current_is_not_outdated() {
        assert!(!is_outdated("0.2.0", "0.1.0"));
        assert!(!is_outdated("1.0.0", "v0.9.0"));
    }

    #[test]
    fn shorter_lists_pad_with_zero() {
        assert!(is_outdated("0.1", "0.1.1"));
        assert!(!is_outdated("0.1.1", "0.1"));
    }

    #[test]
    fn non_numeric_tags_fall_back_to_string_inequality() {
        assert!(is_outdated("0.1.0", "v0.2.0-rc1"));
        assert!(is_outdated("0.1.0", "v0.1.0-rc1"));
        assert!(is_outdated("v0.2.0-rc1", "0.1.0"));
        assert!(!is_outdated("0.1.0", "v0.1.0"));
    }
}
