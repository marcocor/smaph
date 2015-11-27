import glob
from collections import Counter
import re

EXCLUDED = ("Category:", "Wikipedia:", "Portal:", "Help:", "Template:", "File:", ":Template:", "Draft:", "Wikipedia talk:", "Talk:")

def normalized(title):
	title = title[0].upper() + title[1:]
	return re.sub("_+", " ", title)

def read_tsv_files(glob_pattern):
	for fn in glob.glob(glob_pattern):
		print "Reading file: {}".format(fn)
		with open(fn) as f:
			for l in f:
				yield l.decode('utf8').rstrip("\n").split("\t")

def load_titles(glob_pattern):
	title_to_wid = dict()
	redir_title_to_title = []
	for tok in read_tsv_files(glob_pattern):
		assert len(tok) == 3
		if not tok[2]: #Actual page
			title = normalized(tok[0])
			wid = int(tok[1])
			title_to_wid[title] = wid
		else:
			from_title = tok[0]
			wid = int(tok[1])
			to_title = normalized(tok[2])
			redir_title_to_title.append((from_title, wid, to_title))

	#de-reference redirect titles
	redir_title_to_wid, redir_title_to_deref_wid = indepth_dereference(title_to_wid, redir_title_to_title)
	return title_to_wid, redir_title_to_wid, redir_title_to_deref_wid

def indepth_dereference(title_to_wid, redir_title_to_title):
	redir_title_to_wid=dict()
	redir_title_to_deref_wid=dict()
	leftovers = redir_title_to_title
	found = True
	iteration = 0

	while leftovers and found:
		print "Nested redirect iteration {} leftovers:{}".format(iteration, len(leftovers))
		found = False
		new_leftovers = []
		for redir_from, wid_from, redir_to in leftovers:
			deref_wid = None
			if redir_to in title_to_wid:
				deref_wid = title_to_wid[redir_to]
			elif redir_to in redir_title_to_wid:
				deref_wid = redir_title_to_deref_wid[redir_to]
				print u"Nested redirect at iteration {}: {}->{}->{}".format(iteration, redir_from, redir_to, deref_wid).encode("utf8")
			if deref_wid:
				redir_title_to_deref_wid[redir_from] = deref_wid
				redir_title_to_wid[redir_from] = wid_from
				found = True
			else:
				new_leftovers.append((redir_from, wid_from, redir_to))
		leftovers = new_leftovers
		iteration += 1

	for redir_title, _, title in leftovers:
		if not title.startswith(EXCLUDED):
			print u"Broken redirect: {}->{}".format(redir_title, title).encode("utf8")
	return redir_title_to_wid, redir_title_to_deref_wid

def load_anchors(glob_pattern, title_to_wid, redir_title_to_wid):
	broken_links_count = 0
	wid_to_anchors_counter = dict((wid, Counter()) for wid in title_to_wid.values())
	for anchor, title in read_tsv_files(glob_pattern):
		if not anchor:
			continue
		title = normalized(title)
		wid = None
		if title in title_to_wid:
			wid = title_to_wid[title]
		elif title in redir_title_to_wid:
			wid = redir_title_to_wid[title]
		else:
			if not title.startswith(EXCLUDED):
				broken_links_count += 1
			continue
		wid_to_anchors_counter[wid][anchor] += 1
	print u"Broken anchors: {} (excluding Category:, Help:, etc...)".format(broken_links_count)
	return wid_to_anchors_counter

if __name__ == "__main__":
	print "Loading titles and redirects..."
	title_to_wid, redir_title_to_wid, redir_title_to_deref_wid = load_titles("data/title_wid_redirect-*.tsv")
	print "Loading anchors..."
	#wid_to_anchors_counter = load_anchors("data/anchors-*.tsv", title_to_wid, redir_title_to_wid)

	print "Writing titles to wids..."
	with open("data/titles_wid.tsv", "w") as titles_f:
		for t, w in title_to_wid.iteritems():
			titles_f.write(u"{}\t{}\n".format(t,w).encode("utf8"))
	print "Writing redirect titles to de-referenced wids..."
	with open("data/redirect_wid.tsv", "w") as red_f:
		for t, w in redir_title_to_deref_wid.iteritems():
			red_f.write(u"{}\t{}\t{}\n".format(t,redir_title_to_wid[t],w).encode("utf8"))
	print "Writing anchors frequency..."
#	with open("data/anchors.tsv", "w") as anchors_f:
#		for w in wid_to_anchors_counter:
#			for a, freq in wid_to_anchors_counter[w].iteritems():
#				anchors_f.write(u"{}\t{}\t{}\n".format(a, w, freq).encode("utf8"))
