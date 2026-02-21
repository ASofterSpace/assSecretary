/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.missionControl;

import com.asofterspace.assSecretary.Database;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;


public class McInfo {

	private final static String DEFAULT = "<span class='warning'>Has not yet responded</span>";

	private List<String> entryKeys = new ArrayList<>();
	private Map<String, String> entries = new ConcurrentHashMap<>();
	private static Map<String, String> names;
	private static Map<String, String> overviewCaptions;


	public McInfo() {
	}

	public synchronized void clear(String key) {
		entries.put(key, DEFAULT);
		if (!entryKeys.contains(key)) {
			entryKeys.add(key);
		}
	}

	public void set(String key, String value) {
		entries.put(key, value);
	}

	public List<String> getKeys() {
		return entryKeys;
	}

	public String get(String key) {
		String result = entries.get(key);
		if (result == null) {
			return DEFAULT;
		}
		return result;
	}

	public String getOv(String key) {
		String result = getOverviewCaption(key);
		if (result == null) {
			return get(key);
		}
		return result + ": " + get(key);
	}

	public static void initStaticsFromDatabase(Database database) {
		names = database.getMcInfoNames();
		overviewCaptions = database.getMcInfoOverviewCaptions();
	}

	public static String getName(String key) {
		if (names != null) {
			String result = names.get(key);
			if (result == null) {
				return "(name unknown, key: '" + key + "')";
			}
			return result;
		}
		return null;
	}

	public String getOverviewCaption(String key) {
		if (overviewCaptions != null) {
			return overviewCaptions.get(key);
		}
		return null;
	}

	public boolean isImportant(String key) {

		String val = get(key);

		if (val.contains("class='warning'") || val.contains("class='error'")) {
			return true;
		}

		return false;
	}

}
