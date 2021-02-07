/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary;

import com.asofterspace.assSecretary.tasks.TaskCtrl;
import com.asofterspace.toolbox.io.Directory;
import com.asofterspace.toolbox.io.JSON;
import com.asofterspace.toolbox.io.JsonFile;
import com.asofterspace.toolbox.io.JsonParseException;
import com.asofterspace.toolbox.utils.DateUtils;
import com.asofterspace.toolbox.utils.Record;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Database {

	private static final String DB_FILE_NAME = "database.json";

	private Directory dataDir;

	private JsonFile dbFile;

	private JSON root;

	private Integer port;

	private String username;

	private String inboxContent;

	private Boolean connectToMari;
	private Boolean connectToTowa;
	private Boolean connectToWorkbench;
	private Boolean connectToLtc;

	private TaskCtrl taskCtrl;

	private Map<String, List<String>> shortlistAdvances;

	private Map<String, Object> currentTaskInstanceAmounts;
	private Map<String, Object> doneTaskInstanceAmounts;

	private static String PORT = "port";
	private static String USERNAME = "username";
	private static String INBOX_CONTENT = "inboxContent";
	private static String CONNECT_TO_MARI = "connectToMari";
	private static String CONNECT_TO_TOWA = "connectToTowa";
	private static String CONNECT_TO_WORKBENCH = "connectToWorkbench";
	private static String CONNECT_TO_LTC = "connectToLtc";
	private static String SHORTLIST_ADVANCES = "shortlistAdvances";
	private static String CURRENT_TASK_INSTANCE_AMOUNTS = "currentTaskInstanceAmounts";
	private static String DONE_TASK_INSTANCE_AMOUNTS = "doneTaskInstanceAmounts";


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

		this.port = root.getInteger(PORT);

		this.username = root.getString(USERNAME);

		this.inboxContent = root.getString(INBOX_CONTENT);

		this.connectToMari = root.getBoolean(CONNECT_TO_MARI);

		this.connectToTowa = root.getBoolean(CONNECT_TO_TOWA);

		this.connectToWorkbench = root.getBoolean(CONNECT_TO_WORKBENCH);

		this.connectToLtc = root.getBoolean(CONNECT_TO_LTC);


		this.shortlistAdvances = new HashMap<String, List<String>>();

		Map<String, Record> shortlistAdvanceMap = root.getValueMap(SHORTLIST_ADVANCES);

		for (Map.Entry<String, Record> entry : shortlistAdvanceMap.entrySet()) {
			shortlistAdvances.put(entry.getKey(), entry.getValue().getStringValues());
		}


		this.currentTaskInstanceAmounts = root.getObjectMap(CURRENT_TASK_INSTANCE_AMOUNTS);

		this.doneTaskInstanceAmounts = root.getObjectMap(DONE_TASK_INSTANCE_AMOUNTS);
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

		root.set(PORT, port);

		root.set(USERNAME, username);

		root.set(INBOX_CONTENT, inboxContent);

		root.set(CONNECT_TO_MARI, connectToMari());

		root.set(CONNECT_TO_TOWA, connectToTowa());

		root.set(CONNECT_TO_WORKBENCH, connectToWorkbench());

		root.set(CONNECT_TO_LTC, connectToLtc());

		root.set(SHORTLIST_ADVANCES, shortlistAdvances);

		boolean ordered = false;
		currentTaskInstanceAmounts.put(DateUtils.serializeDate(DateUtils.now()),
			taskCtrl.getCurrentTaskInstances(ordered).size());
		root.set(CURRENT_TASK_INSTANCE_AMOUNTS, currentTaskInstanceAmounts);

		doneTaskInstanceAmounts.put(DateUtils.serializeDate(DateUtils.now()),
			taskCtrl.getDoneTaskInstancesAsTasks().size());
		root.set(DONE_TASK_INSTANCE_AMOUNTS, doneTaskInstanceAmounts);

		dbFile.setAllContents(root);
		dbFile.save();
	}

	public boolean connectToMari() {
		if (connectToMari == null) {
			return true;
		}
		return connectToMari;
	}

	public boolean connectToTowa() {
		if (connectToTowa == null) {
			return true;
		}
		return connectToTowa;
	}

	public boolean connectToWorkbench() {
		if (connectToWorkbench == null) {
			return true;
		}
		return connectToWorkbench;
	}

	public boolean connectToLtc() {
		if (connectToLtc == null) {
			return true;
		}
		return connectToLtc;
	}

	public Map<String, List<String>> getShortlistAdvances() {
		return shortlistAdvances;
	}

	public void setTaskCtrl(TaskCtrl taskCtrl) {
		this.taskCtrl = taskCtrl;
	}

	public Map<String, Object> getCurrentTaskInstanceAmounts() {
		return currentTaskInstanceAmounts;
	}

	public Map<String, Object> getDoneTaskInstanceAmounts() {
		return doneTaskInstanceAmounts;
	}

}
