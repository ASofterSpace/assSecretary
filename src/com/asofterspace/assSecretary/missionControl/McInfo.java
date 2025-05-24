/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.missionControl;

import com.asofterspace.assSecretary.Database;

import java.util.Map;


public abstract class McInfo {

	private static Map<String, String> names;
	private static Map<String, String> overviewCaptions;


	public abstract void set(String key, String value);

	public abstract String get(String key);

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
