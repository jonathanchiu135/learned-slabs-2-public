import matplotlib.pylab as plt

f = open("./parameters-test-thresholds/overallRes.txt", "r")
f.readline()
thresholds = f.readline().split(" ")
thresholds.append("static")

# each map maps trace number -> hitrate; these values will be plotted
hitrate_maps = []
for threshold in thresholds:
    hitrate_maps.append({})

f.readline()
i = 1
while True:
    line = f.readline()
    if not line:
        break
    
    hitrates = line.split(" ")
    for threshold_index, hitrate in enumerate(hitrates):
        hitrate_maps[threshold_index][i] = hitrate
    i += 1

# plot the graphs
def plot_coords(curr, curr_label):
    lists = sorted(curr.items()) 
    x, y = zip(*lists) 
    plt.scatter(x, y, label=curr_label)

for index, hitrate_map in enumerate(hitrate_maps):
    plot_coords(hitrate_map, str(thresholds[index]))

plt.legend(loc="upper left")
plt.show()
