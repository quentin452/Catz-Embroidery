# Java fixture generator

Regenerates the `fixtures/` files the Rust suite byte-compares against
(`crates/emb-data/tests/fixtures.rs`). The Java suite in `src/` is the model; its
output is the ground truth for the port.

## Requirements

- JDK 17+ (the repo pins Java 17 in `build.gradle`; JDK 21 works).
- `tools/java-fixtures/lib/jaxb-api-2.1.jar` — JDK 11+ removed `javax.xml.bind`,
  which `processing/core/PShape.java` still imports. Downloaded from Maven Central
  (the jar is committed so regeneration is offline).

## Usage

```powershell
javac -encoding UTF-8 -cp "src/main/java;library/*;tools/java-fixtures/lib/jaxb-api-2.1.jar" `
      -d tools/java-fixtures/out tools/java-fixtures/GenFixtures.java
java "-Duser.language=en" "-Duser.country=US" `
     -cp "tools/java-fixtures/out;src/main/java;library/*;tools/java-fixtures/lib/jaxb-api-2.1.jar" `
     GenFixtures
```

The `-Duser.language=en -Duser.country=US` is MANDATORY: the Java SVG writer
formats floats without a locale (a French-locale run produces comma decimals —
measured, see `docs/LESSONS.md`).

## Files

- `GenFixtures.java` — the generator (the "Simple Test" design: red square + red
  jump + blue triangle, 100×100 mm, all values hand-checkable).
- `GenModelFixtures.java` — the `emb-model` algorithm fixtures (hatch, trace,
  isolines, TSP).
- `GenFontFixtures.java` — the Hershey SIMPLEX glyph fixtures (the TXT stitch
  source, D011). Run like GenModelFixtures:
  ```powershell
  javac -encoding UTF-8 -cp "src/main/java;library/*;tools/java-fixtures/lib/jaxb-api-2.1.jar" `
        -d tools/java-fixtures/out tools/java-fixtures/GenFontFixtures.java
  java -cp "tools/java-fixtures/out;src/main/java;library/*;tools/java-fixtures/lib/jaxb-api-2.1.jar" `
       GenFontFixtures
  ```
- `extract_font_data.ps1` — regenerates `crates/emb-model/src/font/data.rs` (the
  SIMPLEX cmap + glyph strings) from `PEmbroiderFont.java`. The one-time audit
  trail for the port; run from anywhere (paths resolve from the script's
  location).
- `PesProbe.java` — the measurement of the broken Java PES reader
  (`PEmbroiderReader.PES.read` misreads the PEC offset and decodes text/icon bytes
  as stitches). Kept as evidence; the Rust reader is written correct instead.
