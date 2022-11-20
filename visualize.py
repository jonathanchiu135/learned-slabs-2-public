# import matplotlib.pylab as plt

def get_hitrates(results_file):
    f = open("./actualResults/" + results_file, "r")
    i = 0
    cost_benefit_map, static_map = {}, {}
    for x in f:
        if i != 0:
            cost_benefit_map[i] = float(x.split(" ")[3].rstrip())
            static_map[i] = float(x.split(" ")[5].rstrip())
        i += 1
    f.close()
    return cost_benefit_map, static_map

# visualize how well static does on each trace
no_threshold_hitrates, static_hitrates = get_hitrates("overallRes1.txt")
threshold_100_hitrates, _ = get_hitrates("overallRes2.txt")
threshold_1000_hitrates, _ = get_hitrates("overallRes3.txt")

static_cnt, no_threshold_cnt, threshold_100_cnt, threshold_1000_cnt = 0, 0, 0, 0
for i in range(1, 51):
    max_hitrate = max(static_hitrates[i], no_threshold_hitrates[i], threshold_100_hitrates[i], threshold_1000_hitrates[i])
    if max_hitrate == static_hitrates[i]:
        static_cnt += 1
    elif max_hitrate == no_threshold_hitrates[i]:
        no_threshold_cnt += 1
    elif max_hitrate == threshold_100_hitrates[i]:
        threshold_100_cnt += 1
    else:
        threshold_1000_cnt += 1 
 
print(static_cnt, no_threshold_cnt, threshold_100_cnt, threshold_1000_cnt)

# plot the graphs
lists = sorted(d.items()) # sorted by key, return a list of tuples
x, y = zip(*lists) # unpack a list of pairs into two tuples

plt.plot(x, y)
plt.show()
