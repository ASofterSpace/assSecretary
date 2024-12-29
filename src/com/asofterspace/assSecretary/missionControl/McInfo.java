/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.missionControl;



public abstract class McInfo {

	public abstract void set(String key, String value);

	public abstract String get(String key);

	public boolean isImportant(String key) {

		String val = get(key);

		if (val.contains("class='warning'") || val.contains("class='error'")) {
			return true;
		}

		return false;
	}

}
