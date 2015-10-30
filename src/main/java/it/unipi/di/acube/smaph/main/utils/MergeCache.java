package it.unipi.di.acube.smaph.main.utils;

import it.unipi.di.acube.BingInterface;

import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.HashMap;

public class MergeCache {
	public static void main(String[] args) throws Exception{
		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(
				"bing.cache.compressed.2"));
		HashMap<String, byte[]> url2jsonCache2 = (HashMap<String, byte[]>) ois.readObject();
		ois.close();
		
		BingInterface.setCache("bing.cache.compressed");
		
		BingInterface.mergeCache(url2jsonCache2);
		
		BingInterface.flush();
	}

}
