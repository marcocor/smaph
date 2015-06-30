package it.acubelab.smaph.main;

import it.acubelab.batframework.utils.WikipediaApiInterface;

public class ToDelete {

	public static void main(String[] args) throws Exception {
			WikipediaApiInterface api = new WikipediaApiInterface("/tmp/a2", "/tmp/b2");
			System.out.println(api.getIdByTitle("全日本空輸株式会社"));
			api.flush();
	}

}
