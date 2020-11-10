/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.skyhook;

import com.asofterspace.assSecretary.Database;
import com.asofterspace.toolbox.io.Directory;
import com.asofterspace.toolbox.io.JSON;
import com.asofterspace.toolbox.io.JsonFile;
import com.asofterspace.toolbox.io.JsonParseException;
import com.asofterspace.toolbox.utils.Record;

import java.util.Date;
import java.util.List;


/**
 * Some information about the VMs which we are monitoring for skyhook
 */
public class VmInfoDatabase {

	private Directory dataDir;

	private String dbFileName;

	private JsonFile dbFile;

	private JSON root;

	private List<Record> entries;


	public VmInfoDatabase(Directory dataDir, String dbFileNameArg) {

		this.dataDir = dataDir;

		this.dbFileName = dbFileNameArg + ".json";

		dataDir.create();

		this.dbFile = new JsonFile(dataDir, this.dbFileName);
		this.dbFile.createParentDirectory();
		try {
			this.root = dbFile.getAllContents();
		} catch (JsonParseException e) {
			System.err.println("Oh no!");
			e.printStackTrace(System.err);
			System.exit(1);
		}

		this.entries = root.getArray("entries");
	}

	public Record getRoot() {
		return root;
	}

	public void addDatapoint(Date timestamp, String whichServer, Integer highestPerc) {

		Record cur = Record.emptyObject();
		cur.set("timestamp", timestamp);
		cur.set("server", whichServer);
		cur.set("highestPerc", highestPerc);
		entries.add(cur);

		save();
	}

	public void save() {

		root.makeObject();

		root.set("entries", entries);

		dbFile.setAllContents(root);
		dbFile.save();
	}
}
