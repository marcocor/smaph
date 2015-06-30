import gzip
import rdflib
import re
import codecs
# <http://rdf.freebase.com/ns/m.02mjmr>   <http://rdf.freebase.com/key/wikipedia.en_title>        "Barack_Obama"  .
# <http://rdf.freebase.com/ns/m.02mjmr>   <http://rdf.freebase.com/key/wikipedia.it_id>   "4714697"       .

MID_REGEX = re.compile("<http://rdf.freebase.com/ns/(.*)>")
WID_REGEX = re.compile('"(\d*)"')
WID_KEY = "<http://rdf.freebase.com/key/wikipedia.en_id>"
TITLE_REGEX = re.compile('"(.*)"')
TITLE_KEY = "<http://rdf.freebase.com/key/wikipedia.en_title>"
ENCODING_REGEX = re.compile("\$[0-9A-Fa-f]{4}")

def parseTitle(triple):
	matchMid = MID_REGEX.match(triple[0])
	matchTitle = TITLE_REGEX.match(triple[2])
	if not matchMid or not matchTitle or matchMid.group(1)[:2]!="m.":
		print "weird things happening! " + ", ".join(triple)
		return None, None
	title = matchTitle.group(1)
	while True:
		matchEnc = ENCODING_REGEX.search(title)
		if not matchEnc: break
		old = matchEnc.group(0)
		new = unichr(int(old[1:],16))
		title = title.replace(old, new)
	return matchMid.group(1).replace(".", "/"), title.replace("_", " ")

def parseWid(triple):
	matchMid = MID_REGEX.match(triple[0])
	matchWid =  WID_REGEX.match(triple[2])
	if not matchMid or not matchWid or matchMid.group(1)[:2]!="m.":
		print "weird things happening! " + ", ".join(triple)
		return None, None
	return matchMid.group(1).replace(".", "/"), int(matchWid.group(1))
	
def read_lines(write_triplet):
	count = 0
	inserted = 0
	mid_to_title = {}
	mid_to_wid = {}
	for line in gzip.open("freebase-rdf-2014-07-20-00-00.gz", 'r'):
		count = count + 1
		triple = line.split("\t")[:3]
		if triple[1] == TITLE_KEY:
			mid,title = parseTitle(triple)
			if mid == title == None:
				continue
			if mid in mid_to_wid:
				write_triplet(mid, mid_to_wid[mid], title)
				inserted = inserted + 1
				mid_to_wid.pop(mid, None)
			else:
				mid_to_title[mid] = title
		elif triple[1] == WID_KEY:
			mid, wid =  parseWid(triple)
			if mid == wid == None:
				continue
			if mid in mid_to_title:
				write_triplet(mid, wid, mid_to_title[mid])
				inserted = inserted + 1
				mid_to_title.pop(mid, None)
			else:
				mid_to_wid[mid] = wid
		if count % 10000000 == 0:
			print "{0:.3}% mid_to_wid:{1} mid_to_title:{2}, inserted:{3}".format(float(count)*100 / 1900000000.0, len(mid_to_wid), len(mid_to_title), inserted)
		if "m.010gs6hs" in triple[0]:
			print line
	return mid_to_wid, mid_to_title

out = codecs.open("triplets", "w", "utf-8")
def write_to_file(mid, wid, title):
	out.write(u"{0}\t{1}\t{2}\n".format(mid,wid,title))

mid_to_wid, mid_to_title = read_lines(write_to_file)
print "leftover mid_to_wid:", mid_to_wid
print "leftover mid_to_title:", mid_to_title
out.close()
