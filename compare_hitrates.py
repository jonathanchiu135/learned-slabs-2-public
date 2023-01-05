import matplotlib.pylab as plt

def get_hitrates(results_file):
    f = open("./resultsSummary/" + results_file, "r")
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
# results: no threshold
# results2: threshold 100
# results3: threshold 1000
# results4: threshold 500
# results5: threshold 3000

no_threshold_hitrates, static_hitrates = get_hitrates("overallRes1.txt")
threshold_100_hitrates, _ = get_hitrates("overallRes2.txt")
threshold_1000_hitrates, _ = get_hitrates("overallRes3.txt")
threshold_500_hitrates, _ = get_hitrates("overallRes4.txt")
threshold_3000_hitrates, _ = get_hitrates("overallRes5.txt")

hitrate_maps = [static_hitrates, no_threshold_hitrates, threshold_100_hitrates, threshold_1000_hitrates, threshold_500_hitrates, threshold_3000_hitrates]

static_cnt, no_threshold_cnt, threshold_100_cnt, threshold_1000_cnt, threshold_500_cnt, threshold_3000_cnt = 0, 0, 0, 0, 0, 0

for i in range(1, 51):
    hitrates = list(map(lambda curr_map : curr_map[i], hitrate_maps))
    max_hitrate = max(hitrates)
    # count_max = sum(map(lambda x : 1 if x == max_hitrate else 0, hitrates))
    contrib = 1

    if max_hitrate == static_hitrates[i]:
        static_cnt += contrib
    if max_hitrate == no_threshold_hitrates[i]:
        no_threshold_cnt += contrib
    if max_hitrate == threshold_100_hitrates[i]:
        threshold_100_cnt += contrib
    if max_hitrate == threshold_1000_hitrates[i]:
        threshold_1000_cnt += contrib
    if max_hitrate == threshold_500_hitrates[i]:
        threshold_500_cnt += contrib
    if max_hitrate == threshold_3000_hitrates[i]:
        threshold_3000_cnt += contrib
 
print(static_cnt, no_threshold_cnt, threshold_100_cnt, threshold_1000_cnt, threshold_500_cnt, threshold_3000_cnt)

# plot the graphs
def plot_coords(curr, curr_label):
    lists = sorted(curr.items()) 
    x, y = zip(*lists) 
    plt.scatter(x, y, label=curr_label)

plot_coords(static_hitrates, "static")
plot_coords(no_threshold_hitrates, "no_threshold")
plot_coords(threshold_100_hitrates, "threshold_100")
plot_coords(threshold_1000_hitrates, "threshold_1000")
plot_coords(threshold_500_hitrates, "threshold_500")
plot_coords(threshold_3000_hitrates, "threshold_3000")
plt.legend(loc="upper left")
plt.show()
