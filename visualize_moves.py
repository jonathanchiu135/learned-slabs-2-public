def visualize_moves(result_file):
    f = open("./exampleResults/" + result_file, "r")
    i = 0
    cost_benefit_map, static_map = {}, {}
    for x in f:
        if i != 0:
            cost_benefit_map[i] = float(x.split(" ")[3].rstrip())
            static_map[i] = float(x.split(" ")[5].rstrip())
        i += 1
    f.close()