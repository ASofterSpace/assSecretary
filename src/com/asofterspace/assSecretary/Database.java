/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary;

import com.asofterspace.toolbox.io.Directory;
import com.asofterspace.toolbox.io.JSON;
import com.asofterspace.toolbox.io.JsonFile;
import com.asofterspace.toolbox.io.JsonParseException;
import com.asofterspace.toolbox.utils.Record;


public class Database {

	private static final String DB_FILE_NAME = "database.json";

	private Directory dataDir;

	private JsonFile dbFile;

	private JSON root;

	private Integer port;

	private String username;

	private String inboxContent;


	public Database(Directory dataDir) {

		this.dataDir = dataDir;

		dataDir.create();

		this.dbFile = new JsonFile(dataDir, DB_FILE_NAME);
		this.dbFile.createParentDirectory();
		try {
			this.root = dbFile.getAllContents();
		} catch (JsonParseException e) {
			System.err.println("Oh no!");
			e.printStackTrace(System.err);
			System.exit(1);
		}

		this.port = root.getInteger("port");

		this.username = root.getString("username");

		this.inboxContent = root.getString("inboxContent");
	}

	public Record getRoot() {
		return root;
	}

	public Integer getPort() {
		if (port == null) {
			return 3012;
		}
		return port;
	}

	public String getUsername() {
		return username;
	}

	public String getInboxContent() {
		return inboxContent;
	}

	public void setInboxContent(String inboxContent) {
		this.inboxContent = inboxContent;
	}

	public void save() {

		root.makeObject();

		root.set("port", port);

		root.set("username", username);

		root.set("inboxContent", inboxContent);

		dbFile.setAllContents(root);
		dbFile.save();
	}

}
