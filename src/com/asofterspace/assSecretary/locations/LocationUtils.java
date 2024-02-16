/**
 * Unlicensed code created by A Softer Space, 2023
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.locations;

import com.asofterspace.toolbox.utils.StrUtils;
import com.asofterspace.toolbox.utils.Triple;

import java.util.ArrayList;
import java.util.List;


public class LocationUtils {

	public static String serializeToday(Triple<List<WhenWhere>, List<WhenWhere>, List<WhenWhere>> whenWheres) {
		return serializeDay(whenWheres, true);
	}

	public static String serializeDay(Triple<List<WhenWhere>, List<WhenWhere>, List<WhenWhere>> whenWheres) {
		return serializeDay(whenWheres, false);
	}

	public static String serializeDay(Triple<List<WhenWhere>, List<WhenWhere>, List<WhenWhere>> whenWheres, boolean usePrefix) {

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
		for (WhenWhere whenWhere : whenWheres.getLeft()) {
			List<String> locations = StrUtils.split(whenWhere.getWhere(), " / ");
			for (String loc : locations) {
				if (!toDisplayLeft.contains(loc)) {
					toDisplayLeft.add(loc);
				}
			}
		}

		addToResultWhitespacely(toDisplayLeft, result, usePrefix);

		if (whenWheres.getRight().size() > 0) {

			List<String> toDisplayRight = new ArrayList<>();
			for (WhenWhere whenWhere : whenWheres.getRight()) {
				List<String> locations = StrUtils.split(whenWhere.getWhere(), " / ");
				for (String loc : locations) {
					if (!toDisplayRight.contains(loc)) {
						toDisplayRight.add(loc);
					}
				}
			}

			List<String> toDisplayMiddle = new ArrayList<>();
			for (WhenWhere whenWhere : whenWheres.getMiddle()) {
				List<String> locations = StrUtils.split(whenWhere.getWhere(), " / ");
				for (String loc : locations) {
					if (!toDisplayMiddle.contains(loc)) {
						toDisplayMiddle.add(loc);
					}
				}
			}

			if (toDisplayMiddle.size() > 0) {

				if (usePrefix) {
					result.append(", moving to ");
				} else {
					result.append(" -> ");
				}

				addToResultWhitespacely(toDisplayMiddle, result, usePrefix);

				if (usePrefix) {
					if (!(toDisplayLeft.containsAll(toDisplayRight) &&
						toDisplayRight.containsAll(toDisplayLeft))) {
						result.append(", then to ");
					} else {
						result.append(", then back to ");
					}
				} else {
					result.append(" -> ");
				}

				addToResultWhitespacely(toDisplayRight, result, usePrefix);

			} else {

				if (!(toDisplayLeft.containsAll(toDisplayRight) &&
					toDisplayRight.containsAll(toDisplayLeft))) {

					if (usePrefix) {
						result.append(", moving to ");
					} else {
						result.append(" -> ");
					}

					addToResultWhitespacely(toDisplayRight, result, usePrefix);
				}
			}
		}

		return prefix + result.toString();
	}

	private static void addToResultWhitespacely(List<String> toDisplay, StringBuilder result, boolean usePrefix) {
		String sep = "";
		for (String disp : toDisplay) {
			result.append(sep);
			sep = " / ";

			if (usePrefix) {
				if (StrUtils.startsWithOrIs(disp, "Nest")) {
					result.append("the ");
				}
			}

			result.append(
				/*
				StrUtils.replaceAll(
					// ensure whitespaces are not cut
					StrUtils.replaceAll(disp, " ", "&nbsp;"),
				// but allow whitespaces before brackets to be cut
				"&nbsp;(", " (")
				*/
				disp
			);
		}
	}

}
