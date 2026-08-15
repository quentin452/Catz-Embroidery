# Catz-Embroidery — formats

What the machine formats do. Filled in by M1 (emb-data) from the Java readers/writers
plus real files — a format fact is only written once it is measured on a real file.

## DST

Tajima format. Stitch delta encoding, 3 bytes per stitch. Sections: header, stitches,
end-of-file. Documented when the reader lands.

## PES

Brother format. Multi-section container (PEC + PES). Documented when the reader lands.

## CSV

The suite's own interchange format (what the Java converter exports). Documented when
the writer lands.

## Round-trip rule

Every writer is tested against its reader on real files; the reader is the ground
truth for the writer's output. A format fact asserted by hand in a test is a
comment, not a gate.
