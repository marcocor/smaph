import numpy as np
import matplotlib.pyplot as plt
from matplotlib import mlab
import sys

categories = dict()
with open("/dev/stdin") as f:
	for line in f:
		data = line.split('\t')
		expected = float(data[0])
		prediction = float(data[1])
		if expected not in categories:
			categories[expected] = []
		categories[expected].append(prediction)

for c in categories:
	plt.hist(categories[c], 20, histtype='step', label=str(c))
plt.legend(loc='upper right')
plt.title("Predictions vs. expected")
plt.show()
