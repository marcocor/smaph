package it.unipi.di.acube.smaph.main;

import it.unipi.di.acube.smaph.wikiAnchors.EntityToAnchors;

import java.io.FileInputStream;
import java.io.ObjectInputStream;

public class DumpE2A {
	public static final String DATASET_FILENAME = "e2a.dataset";
	public static final String JSON_FILENAME = "e2a.json";

	public static void main(String[] args) throws Exception {
		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(
				DATASET_FILENAME));
		EntityToAnchors e2a = (EntityToAnchors) ois.readObject();
		ois.close();

		e2a.dumpJson(JSON_FILENAME);
	}
}
