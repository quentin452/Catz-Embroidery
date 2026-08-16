//! TSP ordering (greedy NN + 2-opt), ported from `PEmbroiderTSP`.
//!
//! Determinism: the Java starts trials 0, 1, 2 at fixed points (0, y-min,
//! x-min) and trials >= 3 at `Math.random()` — non-deterministic per run. The
//! Rust port replaces trials >= 3 with a fixed-seed PRNG (a design change the
//! Java should have had; recorded in docs/LESSONS.md). Fixture tests use
//! trials=3, which is deterministic in both.
//!
//! The Java MUTATES its input polylines (reversing them in place); the Rust
//! port takes `&[Vec<Point>]` and returns new reversed copies.

use crate::geom::{Point, segment_intersect};

#[derive(Debug, Clone)]
struct Edge {
    i0: usize,
    i1: usize,
    r0: bool,
    r1: bool,
    d: f32,
    p0: Point,
    p1: Point,
}

/// `PEmbroiderTSP.NN`: nearest-neighbour greedy tour starting at `start`.
fn nn(polylines: &[Vec<Point>], mut start: usize) -> Vec<Edge> {
    let mut edges = Vec::new();
    let mut r0 = true;
    let mut p = polylines[start][polylines[start].len() - 1];
    let mut mask = vec![true; polylines.len()];
    mask[start] = false;

    while edges.len() < polylines.len() - 1 {
        let mut min_i = 0usize;
        let mut min_d = f32::INFINITY;
        let mut min_r = false;
        let mut min_p = Point::new(0.0, 0.0);
        let mut min_q = Point::new(0.0, 0.0);
        for i in 0..polylines.len() {
            if !mask[i] {
                continue;
            }
            let q0 = polylines[i][0];
            let q1 = polylines[i][polylines[i].len() - 1];
            let d0 = q0.dist(p);
            if d0 < min_d {
                min_i = i;
                min_d = d0;
                min_r = false;
                min_p = q0;
                min_q = q1;
            }
            let d1 = q1.dist(p);
            if d1 < min_d {
                min_i = i;
                min_d = d1;
                min_r = true;
                min_p = q1;
                min_q = q0;
            }
        }
        edges.push(Edge {
            i0: start,
            i1: min_i,
            r0,
            r1: min_r,
            d: min_d,
            p0: p,
            p1: min_p,
        });
        mask[min_i] = false;
        p = min_q;
        start = min_i;
        r0 = !min_r;
    }
    edges
}

/// `PEmbroiderTSP.opt2`: 2-opt crossing removal, at most `max_iter` passes.
///
/// The loop stops at the first pass that does not SHORTEN the tour (D008).
/// The Java runs all `max_iter` passes even when the length is invariant —
/// measured: hatch-parallel designs oscillate their crossings with a
/// constant tour length, so every pass past the first is pure O(n²) waste
/// (999 passes × 5 trials on a 1600-polyline design: ~265 s of no progress).
/// Converging designs are unaffected — they stop at `change == false` or a
/// flat pass exactly where the Java does; the fixture tests (small designs)
/// stay green.
fn opt2(edges: &mut [Edge], max_iter: usize) {
    let mut it = 0usize;
    let mut prev_len = f32::INFINITY;
    while it < max_iter {
        it += 1;
        let mut change = false;
        let len = edges.len();
        for i in 0..len {
            for j in i + 1..len {
                let e0 = edges[i].clone();
                let e1 = edges[j].clone();
                if segment_intersect(e0.p0, e0.p1, e1.p0, e1.p1).is_some() {
                    let f0 = Edge {
                        i0: e0.i0,
                        r0: e0.r0,
                        p0: e0.p0,
                        i1: e1.i0,
                        r1: e1.r0,
                        p1: e1.p0,
                        d: e0.p0.dist(e1.p0),
                    };
                    let f1 = Edge {
                        i0: e0.i1,
                        r0: e0.r1,
                        p0: e0.p1,
                        i1: e1.i1,
                        r1: e1.r1,
                        p1: e1.p1,
                        d: e0.p1.dist(e1.p1),
                    };
                    edges[i] = f0;
                    edges[j] = f1;
                    // reverseEdges on the sublist: reverse order AND each edge.
                    let mid = &mut edges[i + 1..j];
                    mid.reverse();
                    for e in mid.iter_mut() {
                        std::mem::swap(&mut e.i0, &mut e.i1);
                        std::mem::swap(&mut e.r0, &mut e.r1);
                        std::mem::swap(&mut e.p0, &mut e.p1);
                    }
                    change = true;
                }
            }
        }
        if !change {
            break;
        }
        let l = sum_length(edges);
        if l >= prev_len {
            break;
        }
        prev_len = l;
    }
}

fn sum_length(edges: &[Edge]) -> f32 {
    edges.iter().map(|e| e.d).sum()
}

/// Deterministic stand-in for `Math.random()` (see the module docs).
fn seeded_rand(state: &mut u64) -> u64 {
    // xorshift64*
    let mut x = *state;
    x ^= x >> 12;
    x ^= x << 25;
    x ^= x >> 27;
    *state = x;
    x.wrapping_mul(0x2545F4914F6CDD1D)
}

/// `PEmbroiderTSP.solve`: `trials` NN+2-opt runs, best tour wins, then the edge
/// chain is rebuilt into an ordered polyline list.
pub fn solve(polylines: &[Vec<Point>], trials: usize, max_iter: usize) -> Vec<Vec<Point>> {
    if polylines.len() < 2 {
        return polylines.to_vec();
    }
    let polylines2: Vec<Vec<Point>> = polylines
        .iter()
        .filter(|p| !p.is_empty())
        .cloned()
        .collect();
    let mut rng: u64 = 0x9E3779B97F4A7C15;

    let mut min_l = f32::INFINITY;
    let mut min_e: Vec<Edge> = Vec::new();
    for i in 0..trials {
        let (mut yam, mut xam) = (0usize, 0usize);
        let mut ymin = f32::INFINITY;
        let mut xmin = f32::INFINITY;
        for (ii, poly) in polylines2.iter().enumerate() {
            for j in [0, poly.len() - 1] {
                let p = poly[j];
                if p.y < ymin {
                    yam = ii;
                    ymin = p.y;
                }
                if p.x < xmin {
                    xam = ii;
                    xmin = p.x;
                }
            }
        }
        let b = match i {
            0 => 0,
            1 => yam,
            2 => xam,
            _ => (seeded_rand(&mut rng) as usize) % polylines2.len(),
        };

        let mut edges = nn(&polylines2, b);
        let _l0 = sum_length(&edges);
        opt2(&mut edges, max_iter);
        let l1 = sum_length(&edges);
        if l1 < min_l {
            min_l = l1;
            min_e = edges;
        }
    }

    let mut polylines3: Vec<Vec<Point>> = Vec::new();
    let mut next: isize = -1;
    let mut zero: isize = -1;
    let mut nr = false;
    while !min_e.is_empty() {
        let mut ok = false;
        let mut end = false;
        let mut i = min_e.len();
        while i > 0 {
            i -= 1;
            let e = min_e[i].clone();
            if next == -1 || e.i0 as isize == next {
                let mut p = polylines2[e.i0].clone();
                if !e.r0 {
                    p.reverse();
                }
                ok = true;
                end = true;
                polylines3.push(p);
                next = e.i1 as isize;
                nr = e.r1;
                if zero == -1 {
                    zero = e.i0 as isize;
                }
                min_e.remove(i);
                break;
            }
            if e.i1 as isize == zero {
                let mut p = polylines2[e.i0].clone();
                if !e.r0 {
                    p.reverse();
                }
                ok = true;
                polylines3.insert(0, p);
                zero = e.i0 as isize;
                min_e.remove(i);
            }
        }
        if !ok {
            break;
        }
        if !end {
            let mut q = polylines2[next as usize].clone();
            if nr {
                q.reverse();
            }
            polylines3.push(q);
        }
    }
    polylines3
}
