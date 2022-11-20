import matplotlib.pylab as plt

def get_moves(result_file):
    f = open("./exampleResults/" + result_file, "r")
    i, epoch_cnt = 0, 0
    epoch_cnts = {} 
    while True:
        if i % 10 == 0 and i != 0:
            epoch_cnts[i] = epoch_cnt
            epoch_cnt = 0
        
        next_line = f.readline()
        if next_line and len(next_line.split(" ")) > 0:
            if len(next_line.split(" ")) > 2:
                epoch_cnt += 1 
            i += 1

            # read the rest of the epoch 
            for _ in range(3):
                f.readline()

        else:
            break
    f.close()
    return epoch_cnts

moves_no_threshold = get_moves("w01.cost-benefit-nothreshold.txt")
moves_threshold_100 = get_moves("w01.cost-benefit-threshold100.txt")

def plot_coords(curr):
    lists = sorted(curr.items()) 
    x, y = zip(*lists) 
    plt.scatter(x, y)

# plot_coords(moves_no_threshold)
plot_coords(moves_threshold_100)
plt.show()
