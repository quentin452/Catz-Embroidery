#![forbid(unsafe_code)]
//! Cross-crate gate tests. Each test holds a declared rule to the files on disk;
//! a drift fails `cargo test --workspace`, not a review (docs/TESTING.md).
