/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary;

import com.asofterspace.toolbox.io.Directory;
import com.asofterspace.toolbox.io.JSON;
import com.asofterspace.toolbox.io.JsonFile;
import com.asofterspace.toolbox.io.JsonParseException;
import com.asofterspace.toolbox.utils.DateUtils;

import java.util.Date;


/**
 * This is a quick database - everything in here can be null, it is only intended to be saved fast,
 * not lots of data to be stored or something to rely on!
 */
public class QuickDatabase {

	private static final String DB_FILE_NAME = "quickdb.json";

	private Directory dataDir;

	private JsonFile dbFile;

	private JSON root;

	private Date lastAccessDate;
	private Date previousStartLastAccessDate;

	private static String LAST_ACCESS_DATE = "lastAccessDate";


	public QuickDatabase(Directory dataDir) {

		this.dataDir = dataDir;

		dataDir.create();

		this.dbFile = new JsonFile(dataDir, DB_FILE_NAME);
		this.dbFile.createParentDirectory();
		try {
			this.root = dbFile.getAllContents();
		} catch (JsonParseException e) {
			System.err.println("QuickDB could not be loaded! This will be ignored...");
			e.printStackTrace(System.err);
			return;
		}

		this.lastAccessDate = root.getDate(LAST_ACCESS_DATE);
		this.previousStartLastAccessDate = this.lastAccessDate;
	}

	public Date getPreviousStartLastAccessDate() {
		return previousStartLastAccessDate;
	}

	public void access() {
		lastAccessDate = DateUtils.now();

		save();
	}

	public void save() {

		if (root == null) {
			root = new JSON();
		}

		root.makeObject();

		root.set(LAST_ACCESS_DATE, lastAccessDate);

		dbFile.setAllContents(root);
		dbFile.save();
	}

}
