import matplotlib.pylab as plt

epoch_lens = [-1, 1080000, 540000, 270000, 135000, 67500, 33750, 16875, 8437, 4218, 2109, 1054, 528, 264]

f = open("./reqcount_results/trace23-epoch264-allocation.txt", "r")
x = []
y = []
i = 1
for line in f:
    if len(line.split(" ")) > 2:
        y.append(1)
    else:
        y.append(0)
    x.append(i)
    i += 1
f.close()

plt.scatter(x, y, s=1, )
plt.show()

# for i in epoch_lens:
#     plt.boxplot(distances_to_max_per_epoch[i])
# plt.show()    

