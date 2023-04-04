import matplotlib.pylab as plt

epoch_lens = [-1, 1080000, 540000, 270000, 135000, 67500, 33750, 16875, 8437, 4218, 2109, 1054, 528, 264]

f = open("./reqcount_results/overall-results-future-2.txt", "r")
i = 0
hitrate_map = {}
for x in f:
    # start reading on line 6
    if i >= 6:
        hitrate_map[i-5] = dict(zip(epoch_lens, list(map(float, x.rstrip().split(" ")))))
    i += 1
f.close()

"""
hitrate map is now in the form:
    1 -> {-1 -> hitrate, 1080000 -> hitrate, ...}
    2 -> {-1 -> hitrate, 1080000 -> hitrate, ...}
    ...
"""

# # box plot the distances to the max
distances_to_max_per_epoch = {}
for i in epoch_lens:
    distances_to_max_per_epoch[i] = []

# loop through traces
for i in range(1, max(hitrate_map.keys())):
    maxHitrate = max(hitrate_map[i].values())
    for j in hitrate_map[i].keys():
        distances_to_max_per_epoch[j].append(abs(maxHitrate - hitrate_map[i][j]))

print(distances_to_max_per_epoch)

plt.boxplot(distances_to_max_per_epoch.values(), showfliers=False)
plt.show()

# for i in epoch_lens:
#     plt.boxplot(distances_to_max_per_epoch[i])
# plt.show()    

