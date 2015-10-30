import numpy as np
import matplotlib.pyplot as plt
from matplotlib import mlab
import sys

FTR_ID = int(sys.argv[1])
ftr_values = ([],[])
with open("/dev/stdin") as f:
	for line in f:
		data = line.split('#')[0].strip().split()
		category = int(float(data[0]))
		data = dict(map(lambda dp: (int(dp.split(":")[0]), float(dp.split(":")[1])), data[1:]))
		if category == 1:
			ftr_values[1].append(data[FTR_ID])
		else:
			ftr_values[0].append(data[FTR_ID])

print "average for Positive:{0} Negatives:{1}".format(np.mean(ftr_values[1]), np.mean(ftr_values[0]))

plt.hist(ftr_values[0], 20, histtype='step', label="Negative ex.")#, normed=1
plt.hist(ftr_values[1], 20, histtype='step', label="Positive ex.")#, normed=1
plt.legend(loc='upper right')
plt.title("Feature {0}".format(sys.argv[1]))
plt.show()
