import random

# generate synthetic trace with maybe 40000000 lines
lines_in_trace = 40000000
lines_to_test = lines_in_trace/2
synthetic_trace_link = "./synthetic_trace"
f = open(synthetic_trace_link)

# possible initial slab allocations
# slab_allocation = {64: 16, 128: 8, 256: 8, 512: 4, 1024: 4, 2048: 3, 4096: 3, 8192: 2, 16384: 2, 32768: 2, 65536: 1, 131072: 1, 262144: 1, 524288: 1, 1048576:1 }
# slab_allocation = {64: 10, 128: 0, 256: 0, 512: 0, 1024: 0, 2048: 0, 4096: 0, 8192: 0, 16384: 0, 32768: 0, 65536: 0, 131072: 0, 262144: 0, 524288: 0, 1048576: 0 }
# slab_allocation = {64: 10, 128: 1, 256: 1, 512: 1, 1024: 1, 2048: 1, 4096: 1, 8192: 1, 16384: 1, 32768: 1, 65536: 1, 131072: 1, 262144: 1, 524288: 1, 1048576: 1 }

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
def write_item_to_trace(f, id, size, next_time_accessed):
    f.write(b'\xC3\xA9\xA9\xA9')
    f.write(id.to_bytes(8, 'little'))
    f.write(size.to_bytes(4, 'little'))
    f.write(next_time_accessed.to_bytes(8, 'little'))

def write_trace_half_with_target(target_slab_class):
    SLAB_CLASSES_WITHOUT_TARGET = [x for x in SLAB_CLASSES if x != target_slab_class]
    for _ in range(lines_to_test):
        item_sc = -1
        if random.random() < .8:
            item_sc = pick_random_slab_class_except(SLAB_CLASSES_WITHOUT_TARGET)
        else:
            item_sc = target_slab_class
        
        id = pick_random_item_from_slab_class(item_sc, item_pools)
        size = item_sc - 2
        next_time_accessed = - 1        

        write_item_to_trace(f, id, size, next_time_accessed)

write_trace_half_with_target(1024)
write_trace_half_with_target(4096)

