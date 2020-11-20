/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.tasks;

import com.asofterspace.toolbox.io.Directory;
import com.asofterspace.toolbox.io.JsonFile;
import com.asofterspace.toolbox.io.JsonParseException;
import com.asofterspace.toolbox.utils.Record;
import com.asofterspace.toolbox.utils.StrUtils;

import java.util.Random;


/**
 * This is a database for the tasks managed by the assSecretary directly
 */
public class TaskDatabase {

	private Directory dataDir;

	private JsonFile dbFile;

	private Record loadedRoot;

	private boolean available = false;

	private Random rand;


	public TaskDatabase(Directory dataDir) {

		this.dataDir = dataDir;

		rand = new Random();

		dbFile = new JsonFile(dataDir, "tasks.json");

		try {

			this.loadedRoot = dbFile.getAllContents();

			this.available = true;

		} catch (JsonParseException e) {
			this.available = false;
			System.out.println("Tasks database cannot be loaded!");
		}
	}

	public Record getLoadedRoot() {
		return loadedRoot;
	}

	public boolean isAvailable() {
		return available;
	}

	public void save() {

		loadedRoot.makeObject();

		dbFile.setAllContents(loadedRoot);
		dbFile.save();

		String backupName = "tasks-backup-" + StrUtils.leftPad0(rand.nextInt(10000), 4) + ".json";
		JsonFile backupFile = new JsonFile(dataDir, backupName);
		backupFile.setAllContents(loadedRoot);
		backupFile.save();
	}

}
