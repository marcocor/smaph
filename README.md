SMAPH system for query entity linking.
=============

This package contains the code of the SMAPH system developed by Marco Cornolti, Massimiliano Ciaramita, Paolo Ferragina, Stefan Rued and Hinrich Shuetze.

The SMAPH system links queries to the entities it mentions, disambiguating mentions if needed. Entities are Wikipedia pages. This problem is known as "entity recognition and disambiguation in queries". For example, the query **armstrong moon landing** should point to [Neil Armstrong](http://en.wikipedia.org/wiki/Neil_Armstrong) and [Moon Landing](http://en.wikipedia.org/wiki/Moon_landing), while the query **armstrong trumpet** should point to [Louis Armstrong](http://en.wikipedia.org/wiki/Louis_Armstrong) and [Trumpet](http://en.wikipedia.org/wiki/Trumpet).

This system won the [Entity Recognition and Disambiguation Challenge](http://web-ngram.research.microsoft.com/erd2014) (short-text track).

SMAPH is built on top of [Bing](http://bing.com). For this reason, you will need a key to access Bing's API. 

The system is deployed as a web service but can also be queried directly from your code.

# Deploy instructions
## Standard (and suggested) way: deploy a web service.
1. Download the code
	* `git clone http://github.com/marcocor/smaph-erd`
	* `cd smaph-erd`
2. Set the Bing API key
	* obtain a key of the Bing Search API [here](https://datamarket.azure.com/dataset/bing/search)
	* `cp smaph-config.xml.template smaph-config.xml`
	* edit **smaph-config.xml** replacing **BING_KEY** with your [Primary Account Key](https://datamarket.azure.com/account)
3. Run smaph:
	* `mvn -Djetty.port=9090 jetty:run` where 9090 is the TCP port your server will be listening to
3. Use smaph! You can either:
	* access the Json API at **http://localhost:9090/smaph/rest/default?Text=armstrong%20moon**
	* access the debug interface, that will guide you through the steps of the algorithm at **http://localhost:9090/smaph/debug.html**

## Call SMAPH's Java methods
You can also access the SMAPH system directly by calling its Java methods. Take a look at the annotateGetFull method in RestService.java to see how it's done.

## ERD-Challenge way
SMAPH also provides a standard interface as defined by the [ERD Challenge 20014](http://web-ngram.research.microsoft.com/erd2014). Thid interface is accessible at:

**http://localhost:9090/smaph/rest/shortTrack**


# Contacts
For any bug you encounter, you can open a bug report on [github](http://github.com/marcor/smaph-erd).

For any enquiry, send an email at x at di.unipi.it (replace x with 'cornolti')

Enjoy,
The SMAPH team.
