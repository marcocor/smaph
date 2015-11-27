import xml.sax
import sys
import mwparserfromhell
import re
import logging
import os
import os.path
import multiprocessing
from multiprocessing import Queue, Pool, Process

class WikipediaHandler(xml.sax.ContentHandler):
	char_count = 0
	pages_count = 0

	def __init__(self, stream_queue):
		self._charBuffer = []
		self._stream_queue = stream_queue

	def _flushCharBuffer(self):
		s = ''.join(self._charBuffer).strip()
		s = re.sub(r"\s+", " ", s)
		return s

	def characters(self, data):
		self._charBuffer.append(data)

	def startElement(self, name, attrs):
		if name == "page":
			self.title, self.wid, self.redirect, self.ns, self.text = None, None, None, None, None
		if name == "redirect":
			titles = [p[1] for p in attrs.items() if p[0] == "title"]
			assert len(titles) == 1
			self.redirect = titles[0]
		self._charBuffer = []

	def endElement(self, name):
		if name == "title":
			self.title = self._flushCharBuffer()
		elif name == "id":
			if self.wid is None: #If this is the first id we see.
				self.wid = int(self._flushCharBuffer())
		elif name == "ns":
			self.ns = int(self._flushCharBuffer())
		elif name == "text":
			self.text = self._flushCharBuffer()
		elif name == "page":
			self._process()

	def _process(self):
		if self.ns != 0:
			return
		if self.text[:9].lower() == "#redirect" and self.redirect is None:
			return
		if not self.title or not self.text:
			return
		self._stream_queue.put((self.title, self.wid, self.redirect, self.text))
		assert self.title
		assert self.wid is not None
		assert self.ns is not None
		assert self.text
		if self.text[:9].lower() != "#redirect":
			assert self.redirect is None
		self.char_count += len(self.text)
		self.pages_count += 1
		if not self.pages_count % 100000:
			logging.info(u"Reader - read char:{} pages:{} queue_size:{}".format(self.char_count, self.pages_count, self._stream_queue.qsize()).encode("utf-8"))

def clean_anchor(a):
	a = a.strip()
	a = re.sub(r"''+", "", a)
	a = re.sub(r"<\s*br\s*/?>", " ", a)
	a = re.sub(r"<[^>]*>", "", a)
	a = re.sub(r"&nbsp;", " ", a)
	a = re.sub(r"&ndash;", "-", a)
	return a

def parse_anchors(links_iter):
	for l in links_iter:
		a_title = l.title.strip()
		a_text = clean_anchor(l.text) if l.text else None
		yield (a_title, a_text if a_text else a_title)

def parse_article(w_id, q, base_dir):
	count = 0
	with open(os.path.join(base_dir, "title_wid_redirect-{}.tsv".format(w_id)), "w") as titles_wid_redirect_f, open(os.path.join(base_dir, "anchors-{}.tsv".format(w_id)), "w") as anchors_f:
		while True:
			t = q.get()
			count += 1
			if not count % 10000 or t is None:
				logging.info(u"Parser-{} written pages:{} queue size:{}".format(w_id, count, q.qsize()))
			if t is None:
				q.put(None) #Tell other processes to exit
				return
			else:
				title, wid, redirect, text = t
				title_str = u"{}\t{}\t{}\n".format(title, wid, redirect if redirect else "").encode("utf-8")
				assert title_str.count("\t") == 2
				assert title_str.count("\n") == 1
				titles_wid_redirect_f.write(title_str)
				try:
					parsed_article = mwparserfromhell.parse(text)
				except mwparserfromhell.parser.ParserError:
					logging.warning(u"Could not parse text for article: {} (wid {}), skipping.".format(title, wid))
					continue
				for a_title, anchor in parse_anchors(parsed_article.filter_wikilinks()):
					if a_title and anchor and not any(c in anchor or c in a_title for c in "|[]{}#"):
						anchor_str = u"{}\t{}\n".format(anchor, a_title).encode("utf-8")
						assert anchor_str.count("\t") == 1
						assert anchor_str.count("\n") == 1
						anchors_f.write(anchor_str)

def reader_thr(q):
	parser = xml.sax.make_parser()
	parser.setContentHandler(WikipediaHandler(q))
	parser.parse(open(sys.argv[1], "r"))
	q.put(None)
	return

def parse_thr(q, base_dir):
	n_procs = multiprocessing.cpu_count()
	processes = [Process(target=parse_article, args=(w_id, q, base_dir)) for w_id in range(n_procs)]

	for p in processes:
		p.start()
	for p in processes:
		p.join()
	assert q.get() == None
	assert q.empty()
	q.close()

if __name__ == "__main__":
	logging.basicConfig(level=logging.INFO)
	base_dir = "data"
	if not os.path.isdir(base_dir):
		os.makedirs(base_dir)

	reader_parser_queue = Queue(maxsize=100)
	reader_p = Process(target=reader_thr, args=(reader_parser_queue,))
	parse_p = Process(target=parse_thr, args=(reader_parser_queue, base_dir))

	reader_p.start()
	parse_p.start()
	
	reader_p.join()
	parse_p.join()
