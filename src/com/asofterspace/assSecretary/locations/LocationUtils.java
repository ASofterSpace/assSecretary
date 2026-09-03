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

		if (whenWheres == null) {
			if (usePrefix) {
				prefix = "on ";
			}
			return prefix + "Planet Earth";
		}

		StringBuilder result = new StringBuilder();

		List<String> toDisplayLeft = new ArrayList<>();
		for (WhenWhere whenWhere : whenWheres) {
			toDisplayLeft.add(whenWhere.getWhere());
		}

		addToResultWhitespacely(toDisplayLeft, result, usePrefix);

		return prefix + result.toString();
	}

	private static void addToResultWhitespacely(List<String> toDisplay, StringBuilder result, boolean usePrefix) {
		String sep = "";
		for (String disp : toDisplay) {
			result.append(sep);

			if (usePrefix) {
				if ("".equals(sep)) {
					sep = ", travelling to ";
				} else {
					sep = ", then to ";
				}

				if (StrUtils.startsWithOrIs(disp, "Nest")) {
					result.append("the ");
				}
			} else {
				sep = " -> ";
			}

			result.append(
				disp
			);
		}
	}

}
