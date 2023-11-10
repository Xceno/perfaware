//! Performance Aware Programming | 01-Interlude | The Haversine Distance Problem
//! ---
//! Haversine distance problem examples and experiments.
//! See: https://www.computerenhance.com/p/the-haversine-distance-problem
//! ---
//!

const std = @import("std");
const hav = @import("interlude-haversine/haversine.zig");


pub fn main() !void {
    try hav.run_haversine_example();
}
