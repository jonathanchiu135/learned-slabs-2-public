import random

# generate synthetic trace with maybe 40000000 lines
lines_in_trace = 40000000
lines_to_test = int(lines_in_trace/2)
synthetic_trace_link = "/mntData2/synthetic/synthetic_trace_256_1024"

"""
    **** some possible initial slab allocations **** 

        this.SLAB_COUNTS_MAP = new HashMap<>();
        this.SLAB_COUNTS_MAP.put(64, 16);
        this.SLAB_COUNTS_MAP.put(128, 8);
        this.SLAB_COUNTS_MAP.put(256, 8);
        this.SLAB_COUNTS_MAP.put(512, 4);
        this.SLAB_COUNTS_MAP.put(1024, 4);
        this.SLAB_COUNTS_MAP.put(2048, 3);
        this.SLAB_COUNTS_MAP.put(4096, 3);
        this.SLAB_COUNTS_MAP.put(8192, 2);
        this.SLAB_COUNTS_MAP.put(16384, 2);
        this.SLAB_COUNTS_MAP.put(32768, 2);
        this.SLAB_COUNTS_MAP.put(65536, 1);
        this.SLAB_COUNTS_MAP.put(131072, 1);
        this.SLAB_COUNTS_MAP.put(262144, 1);
        this.SLAB_COUNTS_MAP.put(524288, 1);
        this.SLAB_COUNTS_MAP.put(1048576, 1);

        this.SLAB_COUNTS_MAP = new HashMap<>();
        this.SLAB_COUNTS_MAP.put(64, 1);
        this.SLAB_COUNTS_MAP.put(128, 1);
        this.SLAB_COUNTS_MAP.put(256, 1);
        this.SLAB_COUNTS_MAP.put(512, 1);
        this.SLAB_COUNTS_MAP.put(1024, 1);
        this.SLAB_COUNTS_MAP.put(2048, 1);
        this.SLAB_COUNTS_MAP.put(4096, 1);
        this.SLAB_COUNTS_MAP.put(8192, 5);
        this.SLAB_COUNTS_MAP.put(16384, 1);
        this.SLAB_COUNTS_MAP.put(32768, 1);
        this.SLAB_COUNTS_MAP.put(65536, 1);
        this.SLAB_COUNTS_MAP.put(131072, 1);
        this.SLAB_COUNTS_MAP.put(262144, 1);
        this.SLAB_COUNTS_MAP.put(524288, 1);
        this.SLAB_COUNTS_MAP.put(1048576, 1);

        
"""

# generate pools of items
def pick_random_slab_class_except(SLAB_CLASSES_WITHOUT_TARGET):
    return random.choice(SLAB_CLASSES_WITHOUT_TARGET)

def pick_random_item_from_slab_class(sc, item_pools):
    return random.choice(item_pools[sc])

SLAB_CLASSES = [ 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, 131072, 262144, 524288, 1048576 ]
item_pools = {}
items_per_slab_class = 30000
item_id = 100
for sc in SLAB_CLASSES:
    item_pools[sc] = []
    for _ in range(items_per_slab_class):
        item_pools[sc].append(item_id)
        item_id += 1


"""
    trace format:

    time: 4 bytes (discarded)
    id: 8 bytes
    size: 4 bytes
    next_time_accessed: 8 bytes (-1 if never accessed again)

    all values are little-endian
"""
def write_item_to_trace(f, curr_id, size, next_time_accessed):
    # curr = b'\xFF\xFF\xFF\xFF' + curr_id.to_bytes(8, 'little', signed=True) + size.to_bytes(4, 'little', signed=True) + next_time_accessed.to_bytes(8, 'little', signed=True)
    f.write(b'\xFF\xFF\xFF\xFF')
    f.write(curr_id.to_bytes(8, 'little', signed=True))
    f.write(size.to_bytes(4, 'little', signed=True))
    f.write(next_time_accessed.to_bytes(8, 'little', signed=True))

def write_trace_half_with_target(f, target_slab_class):
    SLAB_CLASSES_WITHOUT_TARGET = [x for x in SLAB_CLASSES if x != target_slab_class]
    for _ in range(lines_to_test):
        item_sc = -1
        if random.random() < .2:
            item_sc = pick_random_slab_class_except(SLAB_CLASSES_WITHOUT_TARGET)
        else:
            item_sc = target_slab_class
        
        id = pick_random_item_from_slab_class(item_sc, item_pools)
        size = item_sc - 2
        next_time_accessed = - 1        

        write_item_to_trace(f, id, size, next_time_accessed)

def rewrite_trace_line(f, next_access_times, line_start):
    # parse id from line and check if it's stored in next_access_times
    f.seek(line_start + 4)
    curr_id = f.read(8)
    next_access_time = -1
    if curr_id in next_access_times:
        next_access_time = int(next_access_times[curr_id])
        f.seek(line_start + 16)
        f.write(next_access_time.to_bytes(8, 'little', signed=True))
    next_access_times[curr_id] = line_start / 24 + 1

f = open(synthetic_trace_link, 'wb')
write_trace_half_with_target(f, 256)
write_trace_half_with_target(f, 1024)
f.close()

# scan trace from back, and insert information about next_time_accessed
next_access_times = {}
with open(synthetic_trace_link, 'rb+') as f:
    for i in range(lines_in_trace*24-24, -1, -24):
        rewrite_trace_line(f, next_access_times, i)


