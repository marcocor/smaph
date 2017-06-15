The SMAPH system for query entity linking.
=============

This package contains the code of the SMAPH system developed by Marco Cornolti, Paolo Ferragina (University of Pisa), Massimiliano Ciaramita (Google), Stefan Rued and Hinrich Shuetze (LMU Munich).

The SMAPH system links web search queries to the entities they mention, providing an unambiguous representation of the concepts referenced by the query. Entities are expressed as Wikipedia pages, that can easily be linked to other knowledge bases such as Wikidata or DBPedia. This problem is known as "entity recognition and disambiguation in queries".

For example, the query **armstrong moon landing** refers to entities [Neil Armstrong](http://en.wikipedia.org/wiki/Neil_Armstrong) and [Moon Landing](http://en.wikipedia.org/wiki/Moon_landing), while the query **armstrong trumpet** refers to [Louis Armstrong](http://en.wikipedia.org/wiki/Louis_Armstrong) and [Trumpet](http://en.wikipedia.org/wiki/Trumpet). SMAPH selects, among the possible meanings of words, the actual concept referenced by the query.

This system won the Entity Recognition and Disambiguation Challenge (short-text track) and reaches state-of-the-art performance on the GERDAQ Test dataset, it obtains an average F1 score of 62.3%.

SMAPH is trained on queries but may give interesting results on other types of short text (such as questions) too.

# Obtaining Google CSE / Bing Search credentials
SMAPH is built on top of the information provided by search engines: it issues a number of calls (typically three for Google and two for Bing, but one call can be spared if Source 2 is disabled, see later). Currently, only two engines are supported: Bing Search and Google CSE. For this reason, you will need a key to access either Bing Search or Google CSE.

*By using SMAPH you accept that it may issue  any number of calls to the search engines API on your account, which may results in spending your credit or money.*

- To use SMAPH with Google, please follow the guide [here](https://sobigdata.d4science.org/group/smaph/documentation) (See sections "Setting up Google CSE" and "Enabling the Google API").
- To use SMAPH with Bing, register to the [Bing Web Search API](https://azure.microsoft.com/en-us/pricing/details/cognitive-services/search-api/).

# Using SMAPH deployment on SoBigData
SMAPH is accessible as a web service hosted by the [SoBigData European Research Infrastructure](www.sobigdata.eu). For general use, we strongly recommend to use this deployment, which is the only one for which we guarantee technical support. Registration is free.

Access [SMAPH on SoBigData](https://sobigdata.d4science.org/group/smaph) now!

# Local deployment
In case you prefer to deploy SMAPH locally, you will need to first install a few libraries and gather some datasets. We assume that you have Maven, JDK and `git` installed on your system. These are the instructions:

1. Download the code
	* `git clone http://github.com/marcocor/smaph`
	* `cd smaph`

2. Run the script to download and install libraries
	* `pushd libs/`
	* `./install_libs.sh`
	* `popd`

3. Create a directory for data storage
	* `mkdir -p storage/mapdb`

4. Download the [SMAPH datasets](https://groviera1.di.unipi.it:5001/sharing/xtEQJlHVR) and unzip the files to `storage/mapdb`.

5. Verify datasets integrity with `md5sum storage/mapdb/*`. Should give:
```
dc487aed3bf41928e710d9be181b7aad  storage/mapdb/e2a.db
eaef557016496c5c5f848547dc3caf7a  storage/mapdb/freebase.db
9bb288f0ad3a5aa1a4cab16e836ced05  storage/mapdb/wikipedia_pages.db
```

5. Build SMAPH with
	* `mvn clean compile`

## Option 1. Standalone web server
SMAPH includes a Grizzly standalone web server that deploys the SMAPH Servlet and makes it accessible through a RESTful API. This servlet currently only supports Google CSE as search engine (if you are interested in Bing too, open an [issue](https://github.com/marcocor/smaph/issues)). You can run the standalone server with:

`mvn exec:java -P server`

To change the default listening port (8080), hostname (`localhost`), or storage path (`storage/mapdb`), you can issue parameters `-Dsmaph.port=PORT`, `-Dsmaph.host=HOSTNAME`, `-Dsmaph.storage_path=PATH`.

You can now access SMAPH at:

```
http://localhost:9090/smaph/annotate?google-cse-id=CSE_ID&google-api-key=API_KEY&q=armstrong moon landing
```

where `CSE_ID` and `API_KEY` are your Google CSE credentials (see above).

This will annotate query `armstrong moon landing` with the default annotator and return a json file similar to:
```
{
	"response-code": "OK",
	"annotator": "Smaph annotator - greedy, GOOGLE_CSE",
	"annotations": [
	{
		"begin": 0,
		"end": 9,
		"wid": 21247,
		"title": "Neil Armstrong",
		"url": "http:\/\/en.wikipedia.org\/wiki\/Neil%20Armstrong",
		"score": 0.09837865084409714
	}, {
		"begin": 10,
		"end": 22,
		"wid": 1558077,
		"title": "Moon landing",
		"url":"http:\/\/en.wikipedia.org\/wiki\/Moon%20landing",
		"score":0.4796932637691498
	}]
}
```


You can change the default annotator (currently, SMAPH-3) by issuing the `annotator=ANN` parameter, where `ANN` is either `smaph-1`, `smaph-s`, `smaph-2`, or `smaph-3`.

You can also spare calls to the search engine by issuing parameter `exclude-s2`. This will result in slightly lower (around -2%) average F1, but will save you one call per processed query.

## Option 2. Deploy SMAPH Servlet on a Java Servlet Container (such as Tomcat)

Build the SMAPH Servlet WAR with:

`mvn war:war`

The servlet will need a few context parameters that must be passed e.g. through a `context.xml` file (to be placed in the Tomcat `conf/` directory). It should contain the following parameters:
```
<?xml version='1.0' encoding='utf-8'?>
  <Context>
    <WatchedResource>WEB-INF/web.xml</WatchedResource>
    <Parameter name="it.unipi.di.acube.smaph.wiki-pages-db" value="/PATH/TO/STORAGE/wikipedia_pages.db" override="false"/>
    <Parameter name="it.unipi.di.acube.smaph.wiki-to-freebase-db" value="/PATH/TO/STORAGE/freebase.db" override="false"/>
    <Parameter name="it.unipi.di.acube.smaph.entity-to-anchors-db" value="/PATH/TO/STORAGE/e2a.db" override="false"/>
</Context>
```

## Option 3. Call SMAPH as a Java library
You can also access the SMAPH system directly by calling its Java methods. Install the library with

`mvn install -DskipTests`

and include it in your project's `pom.xml` with:

```
<dependency>
  <groupId>it.unipi.di.acube</groupId>
  <artifactId>smaph</artifactId>
  <version>3.0</version>
</dependency>
```
Take a look at the `annotateDefault` method in `SmaphServlet.java` to see how it's done. You will basically have to build an annotator with `SmaphBuilder` and call the annotator's `solveSa2W()` method.

# Citation and further reading
You can read about SMAPH in [this paper](http://dl.acm.org/citation.cfm?id=2883061&CFID=942489270&CFTOKEN=37300508) published at WWW'16.
You can cite SMAPH through the following bibitem:
```
@inproceedings{Cornolti:2016:PSJ:2872427.2883061,
 author = {Cornolti, Marco and Ferragina, Paolo and Ciaramita, Massimiliano and R\"{u}d, Stefan and Sch\"{u}tze, Hinrich},
 title = {A Piggyback System for Joint Entity Mention Detection and Linking in Web Queries},
 booktitle = {Proceedings of the 25th International Conference on World Wide Web},
 series = {WWW '16},
 year = {2016},
 isbn = {978-1-4503-4143-1},
 location = {Montr\&\#233;al, Qu\&\#233;bec, Canada},
 pages = {567--578},
 numpages = {12},
 url = {https://doi.org/10.1145/2872427.2883061},
 doi = {10.1145/2872427.2883061},
 acmid = {2883061},
 publisher = {International World Wide Web Conferences Steering Committee},
 address = {Republic and Canton of Geneva, Switzerland},
 keywords = {entity linking, erd, piggyback, query annotation},
} 
```
# Contacts
For any bug you encounter, you can open a bug report on [github](http://github.com/marcor/smaph).

For any enquiry, send an email at x at di.unipi.it (replace x with 'cornolti')

Enjoy,
The SMAPH team.
