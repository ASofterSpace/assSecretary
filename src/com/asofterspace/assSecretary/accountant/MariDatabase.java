/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.accountant;

import com.asofterspace.assSecretary.Database;
import com.asofterspace.toolbox.io.JsonFile;
import com.asofterspace.toolbox.io.JsonParseException;
import com.asofterspace.toolbox.utils.Record;


/**
 * This is a readonly (!) version of the database from the assAccountant, Mari.
 * We use this database to be able to tell the user about tasks coming from Mari.
 */
public class MariDatabase {

	private Record loadedRoot;

	private boolean available = false;


	public MariDatabase(String dbFilePath) {

		JsonFile dbFile = new JsonFile(dbFilePath);

		try {

			this.loadedRoot = dbFile.getAllContents();

			this.available = true;

		} catch (JsonParseException e) {
			this.available = false;
		}
	}

	public Record getLoadedRoot() {
		return loadedRoot;
	}

	public boolean isAvailable() {
		return available;
	}

}
