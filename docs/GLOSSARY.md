# Catz-Embroidery — glossary

## Draw list
The shared preview vocabulary (`crates/emb-draw`): commands (`Canvas`,
`Polyline`) + the visual identity (canvas/backdrop colours, stitch width) that
every app emits and every renderer rasterises. The contract that keeps an
editor preview and a viewer preview from disagreeing on line width, colour or
spacing (docs/LESSONS.md). Commands carry precomputed bounds so viewport
culling is O(1) per command.

## Stitch model
`crates/emb-model`: the single SSOT every frontend consumes — polylines with
one colour each, on a width x height canvas (mm). The Java's
`PEmbroiderGraphics` de-facto invariant (`colors[i]` is the colour of
`polylines[i]`) made structural.

## Hoop
The canvas a design is stitched on: the model's width x height, rendered as
the draw list's `Canvas` command.

## Format container
The neutral in-memory representation of a design as the formats see it
(`emb_data::Design`): flat stitches, per-stitch colours and jump flags, bounds,
title. The model's `Model::from_design` groups it into polylines.

## Colour change
The point in a stitch stream where the thread colour changes — starts a new
polyline in `Model::from_design` (and a new path in the Java SVG writer).

## Jump stitch
A needle move with no stitching (the machine lifts the needle): splits a
polyline run. The PES reader does not yet decode jump flags (jumps are empty
on read-back); see the run-grouping in `emb_model::model::from_design`.

## Tie-in/off
Not yet ported; no consumer demands it (queued in ROADMAP if one appears).

See also the Java terms that died on purpose (recorded in
`docs/decisions/INDEX.md` when they do).
