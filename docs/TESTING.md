# Catz-Embroidery — testing

What belongs here: things a test cannot see, and how the gates are run and read.

The workspace gates:

- `tests/arch.rs` — holds `matrix.toml` to the crate manifests: an undeclared edge
  (dev-dependencies included) fails the build, not a review.
- `tests/memory.rs` — holds `memory/MEMORY.md` to the directory: an entry written and
  not indexed exists on disk and for nobody.

Filled in by the first phase that writes code.
