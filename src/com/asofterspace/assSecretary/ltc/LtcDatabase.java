/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.ltc;

import com.asofterspace.assSecretary.Database;
import com.asofterspace.assSecretary.tasks.Task;
import com.asofterspace.toolbox.io.Directory;
import com.asofterspace.toolbox.io.TextFile;
import com.asofterspace.toolbox.utils.DateUtils;
import com.asofterspace.toolbox.utils.StrUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * This is a readonly (!) version of the legacy database from the datacomx LTC (long time calendar).
 * We use this database to be able to tell the user about the history of legacy tasks.
 */
public class LtcDatabase {

	private static List<Task> taskInstances;


	public static void init(Directory dataDir) {

		taskInstances = new ArrayList<>();

		// this is Vergangenheit.stpu, but converted to UTF8 with BOM, and cleaned of parsing errors
		TextFile dbFile = new TextFile(dataDir, "ltc_converted.stpu");

		if (dbFile.exists()) {
			String db = dbFile.getContent();
			StringBuilder curLineBuilder = new StringBuilder();
			String prevLine = "";
			String curLine = "";

			for (int pos = 0; pos < db.length(); pos++) {
				char c = db.charAt(pos);
				if (c == '\n') {
					prevLine = curLine;
					curLine = curLineBuilder.toString().trim();
					curLineBuilder = new StringBuilder();
					if (curLine.startsWith("%c ")) {
						String tags = curLine.substring(3).trim();
						if ("nil".equals(tags) || "inev".equals(tags)) {
							continue;
						}
						if (prevLine.startsWith("%c ")) {
							continue;
						}
						String dateStr = prevLine.substring(0, 12);
						Date date = DateUtils.parseDate(dateStr);
						String contentStr = prevLine.substring(12).trim();

						Task task = new Task();
						task.setTitle(contentStr);
						task.setReleasedDate(date);
						task.setDone(true);
						task.setDoneDate(date);
						task.setExternalSource("LTC");

						tags = ";" + StrUtils.replaceAll(tags, "; ", ";").toLowerCase() + ";";

						// LABEL :: TO ADD ORIGIN, LOOK HERE (import of LTC entries - not necessary for new origins)
						task.setOrigin("private");
						if (tags.contains(";house hunting;")) {
							task.setOrigin("behemoth");
						}
						if (tags.contains(";nowhere;")) {
							task.setOrigin("nowhere");
						}
						if (tags.contains(";asofterspace;") || tags.contains(";a softer space;")) {
							String contentStrLow = contentStr.toLowerCase();
							if (contentStrLow.contains("reco")) {
								task.setOrigin("recoded");
							} else if (contentStrLow.contains("sve")) {
								task.setOrigin("supervisionearth");
							} else {
								task.setOrigin("asofterspace");
							}
						}
						if (tags.contains(";esa;")) {
							task.setOrigin("egscc");
						}
						if (tags.contains(";sport;") || tags.contains(";sports;")) {
							task.setOrigin("sports");
						}
						if (tags.contains(";feuerwehr;") || tags.contains(";ff25;") || tags.contains(";ff43;")) {
							task.setOrigin("firefighting");
						}
						taskInstances.add(task);
					}
				} else {
					curLineBuilder.append(c);
				}
			}
		}
	}

	public static List<Task> getTaskInstances(Date from, Date to) {
		if ((from == null) && (to == null)) {
			return taskInstances;
		}

		List<Task> result = new ArrayList<>();
		for (Task task : taskInstances) {
			Date date = task.getReleaseDate();
			// do not report tasks without date at all
			if (date == null) {
				continue;
			}
			if (from != null) {
				if (from.after(date)) {
					continue;
				}
			}
			if (to != null) {
				if (to.before(date)) {
					continue;
				}
			}
			result.add(task);
		}

		return result;
	}

}
