const std = @import("std");
const math = std.math;
const testing = std.testing;

const RndGen = std.rand.DefaultPrng;
const Allocator = std.mem.Allocator;
var prng = RndGen.init(0);

const earth_radius = 6371;

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

const HaversineResult = struct {
    entries: u64,
    result: f64,
    time_input: u64,
    time_math: u64,
    time_total: u64,
    throughput: []u8,
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

    std.debug.print("Wrote {d} items", .{data.len});

    try write_data_to_json(output_path, data);
}

fn haversine_of_degrees(coords: Coordinates, r: f32) f32 {
    const dy = math.degreesToRadians(f32, coords.y1 - coords.y0);
    const dx = math.degreesToRadians(f32, coords.x1 - coords.x0);
    const y0 = math.degreesToRadians(f32, coords.y0);
    const y1 = math.degreesToRadians(f32, coords.y1);

    const root_term = (math.pow(f32, math.sin(dy / 2.0), 2.0) + (math.cos(y0) * math.cos(y1) * math.pow(f32, math.sin(dx / 2.0), 2.0)));
    const result = (2 * r * math.asin(math.sqrt(root_term)));

    return result;
}

fn read_haversine_json(allocator: Allocator, path: []const u8) !std.json.Parsed([]Coordinates) {
    const file = try std.fs.cwd().openFile(path, .{});
    const file_size = (try file.stat()).size;
    var buffer = try allocator.alloc(u8, file_size);
    try file.reader().readNoEof(buffer);
    var parsed_result = try std.json.parseFromSlice([]Coordinates, allocator, buffer, .{});

    return parsed_result;
}

fn nanos_to_seconds(nanos: u64) u64 {
    return nanos / 1000000000;
}

fn calc_throughput(count: u64, time_total: u64) f64 {
    const fcount = @as(f64, @floatFromInt(count));
    const ftime_total = @as(f64, @floatFromInt(nanos_to_seconds(time_total)));
    return fcount / ftime_total;
}

fn run_haversine() !HaversineResult {
    var json_input_arena = std.heap.ArenaAllocator.init(std.heap.page_allocator);
    defer json_input_arena.deinit();

    std.log.debug("reading haversine-data.json", .{});
    var timer = try std.time.Timer.start();
    const parsed = try read_haversine_json(json_input_arena.allocator(), "haversine-data.json");
    const haversine_data = parsed.value;
    const input_time = timer.lap();
    std.log.debug("done reading.", .{});

    std.log.debug("Calculating average", .{});
    var sum: f32 = 0.0;
    var cnt: u64 = 0;

    for (haversine_data) |coords| {
        sum += haversine_of_degrees(coords, earth_radius);
        cnt += 1;
    }

    const average_distance: f64 = (sum / @as(f32, @floatFromInt(cnt)));
    const math_time = timer.read();

    const time_total: u64 = input_time + math_time;
    const throughput = try std.fmt.allocPrint(std.heap.page_allocator, "{d} haversines/second", .{calc_throughput(cnt, time_total)});

    return .{
        .entries = cnt,
        .result = average_distance,
        .time_input = input_time,
        .time_math = math_time,
        .time_total = time_total,
        .throughput = throughput,
    };
}

pub fn run_haversine_json_generator() !void {
    var example_data_arena = std.heap.ArenaAllocator.init(std.heap.page_allocator);
    defer example_data_arena.deinit();

    const count = 10000000;
    const out_path = "h_versine-data.json";
    try write_example_json(example_data_arena.allocator(), count, out_path);
}

pub fn run_haversine_example() !void {
    const hav_result = try run_haversine();
    const w = std.io.getStdOut().writer();
    try w.print("ENTRIES: {d}\nRESULT: {d}\nINPUT: {any}\nMATH: {any}\nTOTAL: {any}\nTHROUGH: {s}\n", .{
        hav_result.entries,
        hav_result.result,
        std.fmt.fmtDuration(hav_result.time_input),
        std.fmt.fmtDuration(hav_result.time_math),
        std.fmt.fmtDuration(hav_result.time_total),
        hav_result.throughput,
    });
}

test "Creating example json should not leak memory" {
    const test_file = "tmp-testing.json";
    defer {
        std.fs.cwd().deleteFile(test_file) catch |err| {
            switch (err) {
                error.FileNotFound => {
                    std.log.err("Temp output file doesn't exist", .{});
                },
                else => {
                    std.log.err("Couldn't delete tmp file: {}", .{err});
                },
            }
        };
    }

    try write_example_json(testing.allocator, 10, test_file);
}
