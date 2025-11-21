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
	private static final String TASK_STATS_FILE_NAME = "task-stats.json";

	private Directory dataDir;

	private JsonFile dbFile;
	private String dbFilePath;
	private JSON root;

	private JsonFile taskStatsFile;
	private JSON taskStatsRoot;

	private Integer port;

	private String username;

	private String memePath;

	private String inboxContent;

	private Boolean connectToMari;
	private Boolean connectToTowa;
	private Boolean connectToWorkbench;
	private Boolean connectToLtc;
	private Boolean useMissionControl;
	private Boolean uploadCalendar;

	private TaskCtrl taskCtrl;

	private Map<String, List<String>> shortlistAdvances;

	private Map<String, Object> currentTaskInstanceAmounts;
	private Map<String, Object> doneTaskInstanceAmounts;

	private Map<String, String> mcInfoNames;
	private Map<String, String> mcInfoOverviewCaptions;
	private Map<String, String> mcWebLinks;

	private String eventListURL = null;
	private String eventListDir = null;
	private Directory eventListDirectory = null;
	private String eventListLatest = null;

	private static final String PORT = "port";
	private static final String USERNAME = "username";
	private static final String INBOX_CONTENT = "inboxContent";
	private static final String CONNECT_TO_MARI = "connectToMari";
	private static final String CONNECT_TO_TOWA = "connectToTowa";
	private static final String CONNECT_TO_WORKBENCH = "connectToWorkbench";
	private static final String CONNECT_TO_LTC = "connectToLtc";
	public static final String USE_MISSION_CONTROL = "useMissionControl";
	private static final String UPLOAD_CALENDAR = "uploadCalendar";
	private static final String SHORTLIST_ADVANCES = "shortlistAdvances";
	private static final String CURRENT_TASK_INSTANCE_AMOUNTS = "currentTaskInstanceAmounts";
	private static final String DONE_TASK_INSTANCE_AMOUNTS = "doneTaskInstanceAmounts";
	private static final String MEME_PATH = "memepath";
	private static final String EVENT_LIST_URL = "eventListURL";
	private static final String EVENT_LIST_DIR = "eventListDir";
	private static final String EVENT_LIST_LATEST = "eventListLatest";
	private static final String MC_INFO_NAMES = "mcInfoNames";
	private static final String MC_INFO_OVERVIEW_CAPTIONS = "mcInfoOverviewCaptions";
	private static final String MC_WEB_LINKS = "mcWebLinks";


	public Database(Directory dataDir) {

		this.dataDir = dataDir;

		dataDir.create();

		this.dbFile = new JsonFile(dataDir, DB_FILE_NAME);
		this.dbFile.createParentDirectory();
		this.dbFilePath = this.dbFile.getCanonicalFilename();

		this.taskStatsFile = new JsonFile(dataDir, TASK_STATS_FILE_NAME);

		try {
			this.root = dbFile.getAllContents();
			this.taskStatsRoot = dbFile.getAllContents();
		} catch (JsonParseException e) {
			System.err.println("Oh no!");
			e.printStackTrace(System.err);
			System.exit(1);
		}

		this.port = root.getInteger(PORT);

		this.username = root.getString(USERNAME);

		this.memePath = root.getString(MEME_PATH);

		this.inboxContent = root.getString(INBOX_CONTENT);

		this.connectToMari = root.getBoolean(CONNECT_TO_MARI);

		this.connectToTowa = root.getBoolean(CONNECT_TO_TOWA);

		this.connectToWorkbench = root.getBoolean(CONNECT_TO_WORKBENCH);

		this.connectToLtc = root.getBoolean(CONNECT_TO_LTC);

		this.useMissionControl = root.getBoolean(USE_MISSION_CONTROL);

		this.uploadCalendar = root.getBoolean(UPLOAD_CALENDAR);


		this.shortlistAdvances = new HashMap<String, List<String>>();

		Map<String, Record> shortlistAdvanceMap = root.getValueMap(SHORTLIST_ADVANCES);

		for (Map.Entry<String, Record> entry : shortlistAdvanceMap.entrySet()) {
			shortlistAdvances.put(entry.getKey(), entry.getValue().getStringValues());
		}

		this.eventListURL = root.getString(EVENT_LIST_URL);

		this.eventListDir = root.getString(EVENT_LIST_DIR);

		this.eventListDirectory = new Directory(this.eventListDir);

		this.eventListLatest = root.getString(EVENT_LIST_LATEST);

		this.mcInfoNames = root.getStringMap(MC_INFO_NAMES);

		this.mcInfoOverviewCaptions = root.getStringMap(MC_INFO_OVERVIEW_CAPTIONS);

		this.mcWebLinks = root.getStringMap(MC_WEB_LINKS);

		this.currentTaskInstanceAmounts = taskStatsRoot.getObjectMap(CURRENT_TASK_INSTANCE_AMOUNTS);

		this.doneTaskInstanceAmounts = taskStatsRoot.getObjectMap(DONE_TASK_INSTANCE_AMOUNTS);
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

		root.set(MEME_PATH, memePath);

		root.set(INBOX_CONTENT, inboxContent);

		root.set(CONNECT_TO_MARI, connectToMari());

		root.set(CONNECT_TO_TOWA, connectToTowa());

		root.set(CONNECT_TO_WORKBENCH, connectToWorkbench());

		root.set(CONNECT_TO_LTC, connectToLtc());

		root.set(USE_MISSION_CONTROL, useMissionControl());

		root.set(UPLOAD_CALENDAR, uploadCalendar());

		root.set(SHORTLIST_ADVANCES, shortlistAdvances);

		root.set(EVENT_LIST_URL, eventListURL);

		root.set(EVENT_LIST_DIR, eventListDir);

		root.set(EVENT_LIST_LATEST, eventListLatest);

		root.set(MC_INFO_NAMES, mcInfoNames);

		root.set(MC_INFO_OVERVIEW_CAPTIONS, mcInfoOverviewCaptions);

		root.set(MC_WEB_LINKS, mcWebLinks);

		dbFile.setAllContents(root);
		dbFile.save();

		boolean ordered = true;
		currentTaskInstanceAmounts.put(DateUtils.serializeDate(DateUtils.now()),
			taskCtrl.getCurrentTaskInstances(ordered).size());
		taskStatsRoot.set(CURRENT_TASK_INSTANCE_AMOUNTS, currentTaskInstanceAmounts);

		doneTaskInstanceAmounts.put(DateUtils.serializeDate(DateUtils.now()),
			taskCtrl.getDoneTaskInstancesAsTasks().size());
		taskStatsRoot.set(DONE_TASK_INSTANCE_AMOUNTS, doneTaskInstanceAmounts);

		taskStatsFile.setAllContents(taskStatsRoot);
		taskStatsFile.save();
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

	public boolean useMissionControl() {
		if (useMissionControl == null) {
			return true;
		}
		return useMissionControl;
	}

	public boolean uploadCalendar() {
		if (uploadCalendar == null) {
			return true;
		}
		return uploadCalendar;
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

	public String getMemePath() {
		return memePath;
	}

	public Directory getEventListDirectory() {
		return eventListDirectory;
	}

	public String getEventListURL() {
		return eventListURL;
	}

	public String getEventListLatest() {
		return eventListLatest;
	}

	public void setEventListLatest(String eventListLatest) {
		this.eventListLatest = eventListLatest;
	}

	public Map<String, String> getMcInfoNames() {
		return mcInfoNames;
	}

	public Map<String, String> getMcInfoOverviewCaptions() {
		return mcInfoOverviewCaptions;
	}

	public Map<String, String> getMcWebLinks() {
		return mcWebLinks;
	}

	/**
	 * return a string to inform user about which key in which file to edit to change a certain
	 * configured behavior
	 */
	public String getKeyInfoForPrinting(String key) {
		return "key '" + key + "' in database file " + dbFilePath;
	}

}
