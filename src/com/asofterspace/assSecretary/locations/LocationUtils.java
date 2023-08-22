/**
 * Unlicensed code created by A Softer Space, 2023
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.locations;

import com.asofterspace.toolbox.utils.StrUtils;

import java.util.ArrayList;
import java.util.List;


public class LocationUtils {

	public static String serializeToday(List<WhenWhere> whenWheres) {
		return serializeDay(whenWheres, true);
	}

	public static String serializeDay(List<WhenWhere> whenWheres) {
		return serializeDay(whenWheres, false);
	}

	public static String serializeDay(List<WhenWhere> whenWheres, boolean usePrefix) {

		String prefix = "";
		if (usePrefix) {
			prefix = "in ";
		}

		if ((whenWheres == null) || (whenWheres.size() < 1)) {
			if (usePrefix) {
				prefix = "on ";
			}
			return prefix + "Planet&nbsp;Earth";
		}

		StringBuilder result = new StringBuilder();

		List<String> toDisplay = new ArrayList<>();
		for (WhenWhere whenWhere : whenWheres) {
			List<String> locations = StrUtils.split(whenWhere.getWhere(), " / ");
			for (String loc : locations) {
				if (!toDisplay.contains(loc)) {
					toDisplay.add(loc);
				}
			}
		}

		String sep = "";
		for (String disp : toDisplay) {
			result.append(sep);
			sep = " / ";
			result.append(StrUtils.replaceAll(disp, " ", "&nbsp;"));
		}

		if (usePrefix) {
			if (result.toString().startsWith("Nest")) {
				prefix += "your ";
			}
		}

		return prefix + result.toString();
	}

}
