/**
 * Unlicensed code created by A Softer Space, 2024
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.eventList;

import com.asofterspace.assSecretary.Database;
import com.asofterspace.assSecretary.tasks.Task;
import com.asofterspace.toolbox.io.Directory;
import com.asofterspace.toolbox.io.JSON;
import com.asofterspace.toolbox.io.JsonFile;
import com.asofterspace.toolbox.io.JsonParseException;
import com.asofterspace.toolbox.io.TextFile;
import com.asofterspace.toolbox.utils.DateUtils;
import com.asofterspace.toolbox.utils.MathUtils;
import com.asofterspace.toolbox.utils.Record;
import com.asofterspace.toolbox.utils.StrUtils;
import com.asofterspace.toolbox.web.WebAccessor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class EventListDatabase {

	private static Database database;

	private static Record eventListBackupRoot;

	private static List<Task> eventsAsTaskInstances = new ArrayList<>();

	private final static String BACKUP_PREFIX = "database_server_backup";


	public static void init(Database databaseArg) {
		database = databaseArg;

		if ((database.getEventListDirectory() == null) || (!database.getEventListDirectory().exists())) {
			return;
		}

		JsonFile lastBackupFile = new JsonFile(
			database.getEventListDirectory(),
			BACKUP_PREFIX + database.getEventListLatest() + ".json"
		);

		try {
			eventListBackupRoot = lastBackupFile.getAllContents();
		} catch (JsonParseException e) {
			eventListBackupRoot = new Record();
			System.out.println("Error while locally loading event list backup:");
			System.out.println(e);
		}

		initEventList();
	}

	private static void initEventList() {

		eventsAsTaskInstances = new ArrayList<>();

		List<Record> eventListRec = eventListBackupRoot.getArray("events");
		for (Record eventRec : eventListRec) {
			Event ev = new Event(eventRec);
			eventsAsTaskInstances.addAll(ev.getTaskInstances());
		}
	}

	public static void runEventListBackup() {
		if (database.getEventListURL() == null) {
			return;
		}
		// web accessor call is synchronous, as we assume that we are being called in a thread
		// (e.g. by the startup task thread) anyway
		String eventListBackupStr = WebAccessor.get(database.getEventListURL());

		try {
			JSON newRoot = new JSON(eventListBackupStr);

			if (!newRoot.equals(eventListBackupRoot)) {
				eventListBackupRoot = newRoot;
				String newLatestSlotStr = StrUtils.leftPad0(MathUtils.randomInteger(100), 2);
				TextFile newBackupFile = new TextFile(
					database.getEventListDirectory(),
					BACKUP_PREFIX + newLatestSlotStr + ".json"
				);
				newBackupFile.saveContent(eventListBackupStr);
				database.setEventListLatest(newLatestSlotStr);
				database.save();

				initEventList();
			}

		} catch (JsonParseException e) {
			System.out.println("Error while obtaining event list backup:");
			System.out.println(e);
		}
	}

	public static List<Task> getTaskInstances(Date from, Date to) {

		Date tomorrow = DateUtils.addDays(DateUtils.now(), 1);

		// we only ever get future events with this, all others we fully ignore
		if ((from == null) || (from.before(tomorrow))) {
			from = tomorrow;
		}

		List<Task> result = new ArrayList<>();

		if ((to != null) && from.after(to)) {
			return result;
		}

		if (eventsAsTaskInstances != null) {
			for (Task task : eventsAsTaskInstances) {
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
		}

		return result;
	}


}
