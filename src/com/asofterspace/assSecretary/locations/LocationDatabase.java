/**
 * Unlicensed code created by A Softer Space, 2023
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.locations;

import com.asofterspace.toolbox.io.Directory;
import com.asofterspace.toolbox.io.JSON;
import com.asofterspace.toolbox.io.JsonFile;
import com.asofterspace.toolbox.io.JsonParseException;
import com.asofterspace.toolbox.utils.DateUtils;
import com.asofterspace.toolbox.utils.Record;
import com.asofterspace.toolbox.utils.Triple;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class LocationDatabase {

	private static final String DB_FILE_NAME = "locations.json";

	private Directory dataDir;

	private JsonFile dbFile;

	private JSON root;

	private List<String> mainLocations;
	private List<WhenWhere> whenWheres;

	private static String MAIN_LOCATIONS = "mainLocations";

	private static String WHEN_WHERE = "whenWhere";


	public LocationDatabase(Directory dataDir) {

		this.dataDir = dataDir;

		dataDir.create();

		this.mainLocations = new ArrayList<>();
		this.whenWheres = new ArrayList<>();
	}

	public void reload() {

		this.dbFile = new JsonFile(dataDir, DB_FILE_NAME);
		this.dbFile.createParentDirectory();
		try {
			this.root = dbFile.getAllContents();
		} catch (JsonParseException e) {
			System.err.println("LocationDB could not be loaded! This will be ignored...");
			e.printStackTrace(System.err);
			return;
		}

		this.mainLocations = root.getArrayAsStringList(MAIN_LOCATIONS);

		this.whenWheres = new ArrayList<>();
		for (Record rec : root.getArray(WHEN_WHERE)) {
			this.whenWheres.add(new WhenWhere(rec));
		}
	}

	public List<WhenWhere> getWhenWheres() {
		return whenWheres;
	}

	public Triple<List<WhenWhere>, List<WhenWhere>, List<WhenWhere>> getFromTo(Date day) {

		Triple<List<WhenWhere>, List<WhenWhere>, List<WhenWhere>> result =
			new Triple<>(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

		if ((whenWheres == null) || (whenWheres.size() < 1)) {
			return result;
		}

		Date lastBefore = whenWheres.get(0).getDate();
		List<WhenWhere> lastDayBeforeTodayResults = new ArrayList<>();
		List<WhenWhere> todayResults = new ArrayList<>();
		String lastTodayTime = null;
		for (WhenWhere whenWhere : whenWheres) {
			if (day.before(whenWhere.getDate())) {
				break;
			}
			// add everything that is the same day
			if (DateUtils.isSameDay(day, whenWhere.getDate())) {
				todayResults.add(whenWhere);
				lastTodayTime = whenWhere.getTime();
			} else {
				lastBefore = whenWhere.getDate();
			}
		}

		// add everything that was from the day before
		String lastDayBeforeTodayTime = null;
		List<WhenWhere> lastDayBeforeTodayResultsAnyTime = new ArrayList<>();
		for (WhenWhere whenWhere : whenWheres) {
			if (DateUtils.isSameDay(lastBefore, whenWhere.getDate())) {
				lastDayBeforeTodayResultsAnyTime.add(whenWhere);
				lastDayBeforeTodayTime = whenWhere.getTime();
			}
		}
		for (WhenWhere whenWhere : lastDayBeforeTodayResultsAnyTime) {
			if (lastDayBeforeTodayTime.equals(whenWhere.getTime())) {
				lastDayBeforeTodayResults.add(whenWhere);
			}
		}

		// all entries of today except the last one(s)
		List<WhenWhere> todayOnlyResults = new ArrayList<>();
		// last entries of today
		List<WhenWhere> todayLastResults = new ArrayList<>();
		if (lastTodayTime == null) {
			todayLastResults = todayResults;
		} else {
			for (WhenWhere whenWhere : todayResults) {
				if (lastTodayTime.equals(whenWhere.getTime())) {
					todayLastResults.add(whenWhere);
				} else {
					todayOnlyResults.add(whenWhere);
				}
			}
		}

		result.setLeft(lastDayBeforeTodayResults);
		result.setMiddle(todayOnlyResults);
		result.setRight(todayLastResults);

		return result;
	}

	public void save() {

		if (root == null) {
			root = new JSON();
		}

		root.makeObject();

		root.set(MAIN_LOCATIONS, mainLocations);

		List<Record> whenWhereRecs = new ArrayList<>();
		for (WhenWhere whenWhere : whenWheres) {
			whenWhereRecs.add(whenWhere.toRecord());
		}
		root.set(WHEN_WHERE, whenWhereRecs);

		dbFile.setAllContents(root);
		dbFile.save();
	}

}
