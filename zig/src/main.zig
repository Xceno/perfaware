//! Performance Aware Programming | 01-Interlude | The Haversine Distance Problem
//! ---
//! Haversine distance problem examples and experiments.
//! See: https://www.computerenhance.com/p/the-haversine-distance-problem
//! ---
//!

const std = @import("std");
const testing = std.testing;

const RndGen = std.rand.DefaultPrng;
const Allocator = std.mem.Allocator;
var prng = RndGen.init(0);

fn rand_latitude() f32 {
    return -90.0 + (180.0 * prng.random().float(f32));
}

fn rand_longitude() f32 {
    return -180.0 + (360.0 * prng.random().float(f32));
}

const Coordinates = struct {
    x0: f32,
    y0: f32,
    x1: f32,
    y1: f32,
};

fn create_example_data(allocator: Allocator, num_entries: u64) ![]Coordinates {
    const example_data = try allocator.alloc(Coordinates, num_entries);
    errdefer allocator.free(example_data);

    for (0..num_entries) |i| {
        example_data[i] = Coordinates{
            .x0 = rand_latitude(),
            .y0 = rand_longitude(),
            .x1 = rand_latitude(),
            .y1 = rand_longitude(),
        };
    }
    return example_data;
}

fn write_data_to_json(output_path: []const u8, data: []Coordinates) !void {
    var file = try std.fs.cwd().createFile(output_path, .{});
    defer file.close();

    //     const options = std.json.StringifyOptions{
    //         .whitespace = .{
    //             .indent_level = 0,
    //             .ident = .{ .Space = 4 },
    //             .separator = true,
    //         },
    //     };

    try std.json.stringify(data, .{}, file.writer());

    // TODO: look up what the return value is here.
    _ = try file.write("\n");
}

fn write_example_json(allocator: Allocator, num_entries: u64, output_path: []const u8) !void {
    const data = try create_example_data(allocator, num_entries);
    defer allocator.free(data);

    //try stdout.print("Wrote {d} items", .{data.length()});

    try write_data_to_json(output_path, data);
}

fn nanos_to_seconds(nanos: i128) f128 {
    const result = nanos / 1000000000;
    return result;
}

pub fn main() !void {
    // Prints to stderr (it's a shortcut based on `std.io.getStdErr()`)
    // std.debug.print("All your {s} are belong to us.\n", .{"codebase"});

    // stdout is for the actual output of your application, for example if you
    // are implementing gzip, then only the compressed bytes should be sent to
    // stdout, not any debugging messages.
    // const stdout_file = std.io.getStdOut().writer();
    // var bw = std.io.bufferedWriter(stdout_file);
    // const stdout = bw.writer();

    var example_data_arena = std.heap.ArenaAllocator.init(std.heap.page_allocator);
    defer example_data_arena.deinit();

    const count = 5;
    const out_path = "haversine-data.json";
    try write_example_json(example_data_arena.allocator(), count, out_path);

    // try bw.flush(); // don't forget to flush!
}

test "simple test" {
    var list = std.ArrayList(i32).init(std.testing.allocator);
    defer list.deinit(); // try commenting this out and see if zig detects the memory leak!
    try list.append(42);
    try std.testing.expectEqual(@as(i32, 42), list.pop());
}

test "Creating example json should not leak memory" {
    const test_file = "tmp-testing.json";
    defer {
        std.fs.cwd().deleteFile(test_file) catch unreachable;
    }

    try write_example_json(testing.allocator, 10, test_file);
}
