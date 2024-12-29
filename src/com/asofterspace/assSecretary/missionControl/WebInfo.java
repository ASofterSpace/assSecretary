/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.missionControl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;


/**
 * Some information about the webpages which we are monitoring
 */
public class WebInfo extends McInfo {

	private final static String DEFAULT = "<span class='warning'>Has not yet responded</span>";

	private Map<String, String> entries;


	public WebInfo() {
		entries = new ConcurrentHashMap<>();
	}

	@Override
	public void set(String key, String value) {
		entries.put(key, value);
	}

	@Override
	public String get(String key) {
		String result = entries.get(key);
		if (result == null) {
			return DEFAULT;
		}
		return result;
	}

}
