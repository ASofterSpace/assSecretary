/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary;

import com.asofterspace.assSecretary.eventList.EventListDatabase;
import com.asofterspace.assSecretary.facts.FactDatabase;
import com.asofterspace.assSecretary.locations.LocationDatabase;
import com.asofterspace.assSecretary.ltc.LtcDatabase;
import com.asofterspace.assSecretary.missionControl.MissionControlDatabase;
import com.asofterspace.assSecretary.missionControl.VmInfo;
import com.asofterspace.assSecretary.missionControl.WebInfo;
import com.asofterspace.assSecretary.missionControl.WebInfoCallback;
import com.asofterspace.assSecretary.tasks.Task;
import com.asofterspace.assSecretary.tasks.TaskCtrl;
import com.asofterspace.assSecretary.tasks.TaskDatabase;
import com.asofterspace.assSecretary.web.Server;
import com.asofterspace.toolbox.calendar.TaskCtrlBase;
import com.asofterspace.toolbox.io.Directory;
import com.asofterspace.toolbox.io.File;
import com.asofterspace.toolbox.io.IoUtils;
import com.asofterspace.toolbox.io.JSON;
import com.asofterspace.toolbox.io.JsonFile;
import com.asofterspace.toolbox.io.JsonParseException;
import com.asofterspace.toolbox.io.SimpleFile;
import com.asofterspace.toolbox.utils.DateHolder;
import com.asofterspace.toolbox.utils.DateUtils;
import com.asofterspace.toolbox.utils.Record;
import com.asofterspace.toolbox.utils.StrUtils;
import com.asofterspace.toolbox.Utils;
import com.asofterspace.toolbox.web.WebAccessedCallback;
import com.asofterspace.toolbox.web.WebAccessor;
import com.asofterspace.toolbox.web.WebTemplateEngine;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class AssSecretary {

	public final static String DATA_DIR = "config";
	public final static String SERVER_DIR = "server";
	public final static String SCRIPTS_DIR = "scripts";
	public final static String WEB_ROOT_DIR = "deployed";
	public final static String UPLOAD_DIR = "upload";
	public final static String FACT_DIR = "../assTrainer/config";

	public final static String PROGRAM_TITLE = "assSecretary (Hugo)";
	public final static String VERSION_NUMBER = "0.1.0.3(" + Utils.TOOLBOX_VERSION_NUMBER + ")";
	public final static String VERSION_DATE = "21. October 2020 - 31. August 2024";

	private static Database database;
	private static LocationDatabase locationDB;

	private static MissionControlDatabase missionControlDatabase;
	private static VmInfo vmInfo = new VmInfo();
	private static WebInfo webInfo = new WebInfo();

	private static String localInfo = "";
	private static String localInfoShort = "";


	public static void main(String[] args) {

		// let the Utils know in what program it is being used
		Utils.setProgramTitle(PROGRAM_TITLE);
		Utils.setVersionNumber(VERSION_NUMBER);
		Utils.setVersionDate(VERSION_DATE);

		boolean fix_data_mode = false;

		if (args.length > 0) {
			if (args[0].equals("--version")) {
				System.out.println(Utils.getFullProgramIdentifierWithDate());
				return;
			}

			if (args[0].equals("--version_for_zip")) {
				System.out.println("version " + Utils.getVersionNumber());
				return;
			}

			if (args[0].equals("fix_data")) {
				fix_data_mode = true;
			}
		}

		System.out.println("Hi, Hugo here...");

		System.out.println("Looking at directories...");

		Directory dataDir = new Directory(DATA_DIR);
		Directory serverDir = new Directory(SERVER_DIR);
		Directory webRoot = new Directory(WEB_ROOT_DIR);
		Directory factDir = new Directory(FACT_DIR);
		Directory uploadDir = new Directory(UPLOAD_DIR);

		System.out.println("Loading database...");
		database = new Database(dataDir);

		System.out.println("Loading quick db...");
		QuickDatabase quickDB = new QuickDatabase(dataDir);

		System.out.println("Loading location db...");
		locationDB = new LocationDatabase(dataDir);

		System.out.println("Loading mission control db...");
		missionControlDatabase = new MissionControlDatabase(dataDir, "mission_control");

		// this one can be static, as it does not ever really change anymore :)
		System.out.println("Loading LTC db...");
		LtcDatabase.init(dataDir);

		System.out.println("Loading task db...");
		TaskDatabase taskDatabase = new TaskDatabase(dataDir);

		System.out.println("Loading facts...");
		FactDatabase factDatabase = new FactDatabase(factDir);

		System.out.println("Loading event list...");
		EventListDatabase.init(database);

		System.out.println("Starting up task ctrl...");
		TaskCtrl taskCtrl = new TaskCtrl(database, taskDatabase, locationDB, webRoot, uploadDir);


		if (fix_data_mode) {
			System.out.println("Performing a fix data run, then exiting...");

			// sooo let's:
			// * get all the task backup files, in creation order
			// * load them, one by one, but in random order (they are randomly named anyway...)
			// * iterate over all tasks in the old file:
			//   if tasks with the same id exist in the current task file, for both doneDate and setToDoneDate:
			//     if they are not null, set their values to the ones from the old file in the current task file
			//     (if the value was actually changed, store this fact, and don't change it again!)
			// * after each iteration, if anything was changed, save the task file...

			boolean recursively = false;
			List<File> taskFiles = dataDir.getAllFilesStartingAndEndingWith("tasks-", ".json", recursively);
			System.out.println("Found " + taskFiles.size() + " task files, going through them one by one...");

			List<String> changedDoneDateIds = new ArrayList<>();
			List<String> changedSetToDoneDateIds = new ArrayList<>();
			List<Task> allTasks = taskCtrl.getAllTaskInstancesAsTasks();

			for (File taskFile : taskFiles) {
				System.out.println("Consolidating with " + taskFile.getAbsoluteFilename() + "...");
				int changedTasks = 0;
				JsonFile curDbFile = new JsonFile(taskFile);
				Record curRoot = null;
				try {
					curRoot = curDbFile.getAllContents();
				} catch (JsonParseException e) {
					System.out.println("Could not load the task file...");
					continue;
				}

				List<Record> curTaskInstances = curRoot.getArray("taskInstances");
				for (Record cur : curTaskInstances) {
					String curId = cur.getString("id");
					if (curId == null) {
						continue;
					}
					boolean changedDoneDateIdsContains = changedDoneDateIds.contains(curId);
					boolean changedSetToDoneDateIdsContains = changedSetToDoneDateIds.contains(curId);
					if (changedDoneDateIdsContains && changedSetToDoneDateIdsContains) {
						continue;
					}

					for (Task task : allTasks) {
						if (curId.equals(task.getId())) {
							if (!changedDoneDateIdsContains) {
								DateHolder curDoneDate = cur.getDateHolder(TaskCtrlBase.DONE_DATE);
								if (curDoneDate.getDate() != null) {
									DateHolder oldDoneDate = DateUtils.createDateHolder(task.getDoneDate());
									if (!curDoneDate.equals(oldDoneDate)) {
										task.setDoneDate(curDoneDate.getDate());
										changedDoneDateIds.add(curId);
										changedTasks++;
									}
								}
							}

							if (!changedSetToDoneDateIdsContains) {
								DateHolder curSetToDoneDate = cur.getDateHolder(TaskCtrlBase.SET_TO_DONE_DATE_TIME);
								if (curSetToDoneDate.getDate() != null) {
									DateHolder oldSetToDoneDate = DateUtils.createDateHolder(task.getSetToDoneDateTime());
									if (!curSetToDoneDate.equals(oldSetToDoneDate)) {
										task.setSetToDoneDateTime(curSetToDoneDate.getDate());
										changedSetToDoneDateIds.add(curId);
										changedTasks++;
									}
								}
							}

							break;
						}
					}
				}

				if (changedTasks > 0) {
					System.out.println("Changed " + changedTasks + " task values, now saving...");
					taskCtrl.save();
				} else {
					System.out.println("With this file, nothing was changed...");
				}
			}

			System.out.println("Finally, going over all Tasks, and if they have a done date, setting a setToDoneDate if it is null...");
			for (Task cur : allTasks) {
				if (cur.getSetToDoneDateTime() == null) {
					cur.setSetToDoneDateTimeHolder(cur.getDoneDateHolder());
				}
			}
			taskCtrl.save();

			System.out.println("Ran the fix algorithm on all task backup files found. " +
				"Maybe longtime-archive them now - re-running on them shouuuld not break things, but just in case...");

			return;
		}


		try {

			JsonFile jsonConfigFile = new JsonFile(serverDir, "webengine.json");
			JSON jsonConfig = jsonConfigFile.getAllContents();
			jsonConfig.inc("version");
			jsonConfigFile.save(jsonConfig);

			List<String> whitelist = jsonConfig.getArrayAsStringList("files");

			System.out.println("Templating the web application...");

			WebTemplateEngine engine = new WebTemplateEngine(serverDir, jsonConfig);

			engine.compileTo(webRoot);


			System.out.println("Starting the server on port " + database.getPort() + "...");

			Server server = new Server(webRoot, serverDir, database, taskCtrl, factDatabase, quickDB, locationDB);

			server.setWhitelist(whitelist);

			boolean async = true;

			server.serve(async);


			int startupTaskRepeatTimeMinutes = 29;
			System.out.println("Creating startup task thread to re-run tests every " + startupTaskRepeatTimeMinutes + " minutes...");

			Thread startupTaskThread = new Thread() {

				public void run() {

					while (true) {

						runStartupTasks();

						try {
							// wait for a while
							Thread.sleep(startupTaskRepeatTimeMinutes * 60 * 1000);
						} catch (InterruptedException e) {
							// task interrupted - let's bail, this is not so important anyway!
							break;
						}
					}
				}
			};
			startupTaskThread.start();


			System.out.println("Saving database directly after startup to save amount of open tasks...");

			database.save();

		} catch (JsonParseException e) {

			System.out.println("Oh no! The input could not be parsed: " + e);
		}
	}

	public static Database getDatabase() {
		return database;
	}

	public static VmInfo getVmInfo() {
		return vmInfo;
	}

	public static WebInfo getWebInfo() {
		return webInfo;
	}

	public static void runStartupTasks() {

		System.out.println("Performing startup tasks...");

		localInfo = "";
		localInfoShort = "";

		checkWebAndVmStatus();

		checkMemeFolder();

		checkMusicFolder();

		EventListDatabase.runEventListBackup();

		if ("".equals(localInfoShort)) {
			localInfoShort = "<span class='awesome'>All is well</span>";
		}

		locationDB.reload();

		System.out.println("Startup tasks done!");
	}

	private static void checkWebAndVmStatus() {

		webInfo = new WebInfo();
		vmInfo = new VmInfo();

		addWebInfo(webInfo, "asofterspace", "assEn", "https://www.asofterspace.com/", missionControlDatabase);
		addWebInfo(webInfo, "asofterspace", "assDe", "https://www.asofterspace.de/", missionControlDatabase);

		addWebInfo(webInfo, "fem*streik", "femOrg", "https://feministischerstreik.org/", missionControlDatabase);

		addWebInfo(webInfo, "AGSG", "agsgOrg", "https://afghangirlssuccessgate.org/", missionControlDatabase);

		addWebInfo(webInfo, "HERA", "heraTasks", "https://asofterspace.com/heraTasks/", missionControlDatabase);

		addWebInfo(webInfo, "QZT", "qztIPC", "https://asofterspace.com/qzt/instaPostCreator/", missionControlDatabase);

		addWebInfo(webInfo, "SB", "sbWW", "https://asofterspace.com/services/seebruecke/wegweiser/index.htm", missionControlDatabase);

		addWebInfo(webInfo, "WoodWatchers", "wwFrontend", "https://woodwatchers.org/", missionControlDatabase);
		addWebInfo(webInfo, "WoodWatchers", "wwBackend", "https://asofterspace.com/woodWatchers/", missionControlDatabase);

		addVmInfo(vmInfo, "skyhook", "db", missionControlDatabase);
		addVmInfo(vmInfo, "skyhook", "f1", missionControlDatabase);
		addVmInfo(vmInfo, "skyhook", "f2", missionControlDatabase);
		addWebInfo(webInfo, "skyhook", "skyWeb", "https://skyhook.is/", missionControlDatabase);
		addWebInfo(webInfo, "skyhook", "skyApp", "https://app.skyhook.is/", missionControlDatabase);
		addWebInfo(webInfo, "skyhook", "skyDb", "http://skyhookdb.skyhook.is/phpmyadmin/", missionControlDatabase);
	}

	private static void checkMemeFolder() {

		StringBuilder result = new StringBuilder();
		result.append("<div>");
		result.append("<div class='line'>");
		result.append("Several memes in the meme folder seem to have the same size, ");
		result.append("indicating they might be the same file!<br>");
		result.append("They are:");
		result.append("</div>");

		Directory parent = new Directory(database.getMemePath());

		boolean foundSomeProblem = false;
		boolean recursively = false;
		List<File> files = parent.getAllFiles(recursively);
		List<Long> fileSizes = new ArrayList<>();

		for (int i = 0; i < files.size(); i++) {
			fileSizes.add(files.get(i).getSize());
		}

		for (int i = 0; i < files.size(); i++) {
			long iLen = fileSizes.get(i);
			for (int j = i+1; j < files.size(); j++) {
				long jLen = fileSizes.get(j);
				if (iLen == jLen) {
					foundSomeProblem = true;
					result.append("<div class='line'>");
					result.append("<span class='error'>" + files.get(i).getCanonicalFilename() +
						"</span> and <span class='error'>" + files.get(j).getCanonicalFilename() + "</span>");
					result.append("</div>");
				}
			}
		}
		result.append("</div>");

		if (foundSomeProblem) {
			localInfo += result.toString();
			localInfoShort += "<span class='error'>Meme collision!</span> ";
		}
	}

	private static void checkMusicFolder() {

		StringBuilder result = new StringBuilder();
		result.append("<div>");
		result.append("<div class='line'>");
		result.append("Music files are present in the backup which shouldn't be!<br>");
		result.append("They are:");
		result.append("</div>");

		boolean foundSomeProblem = false;

		String SOURCE_ID = "hdd_13_3";
		String TARGET_ID = "hdd_21_1";

		Directory sourceDir = null;

		for (char driveLetter = 'A'; driveLetter < 'Z'; driveLetter++) {
			Directory potentialSourceDir = new Directory("" + driveLetter + ":\\");
			File sourceIdFile = new File(potentialSourceDir, SOURCE_ID + ".txt");
			if (sourceIdFile.exists()) {
				sourceDir = potentialSourceDir;
			}
		}

		Directory targetDir = null;

		for (char driveLetter = 'A'; driveLetter < 'Z'; driveLetter++) {
			Directory potentialTargetDir = new Directory("" + driveLetter + ":\\");
			File targetIdFile = new File(potentialTargetDir, TARGET_ID + ".txt");
			if (targetIdFile.exists()) {
				targetDir = potentialTargetDir;
			}
		}

		if ((sourceDir != null) && (targetDir != null)) {
			List<String> folderNames = new ArrayList<>();
			folderNames.add("Musik");
			folderNames.add("Musik (karaoke)");
			folderNames.add("Musik (Long Mixes)");
			folderNames.add("Musik (sea noise)");
			folderNames.add("Musik (together)");
			folderNames.add("Musik (unsortiert)");

			for (String folderName : folderNames) {
				Directory checkSourceDir = new Directory(sourceDir, "videos (some)/" + folderName);
				Directory checkTargetDir = new Directory(targetDir, "videos (actual)/" + folderName);
				boolean recursively = true;
				List<File> sourceFiles = checkSourceDir.getAllFiles(recursively);
				List<String> sourceFileLocalNames = new ArrayList<>();
				for (File sourceFile : sourceFiles) {
					sourceFileLocalNames.add(sourceFile.getLocalFilename());
				}
				List<File> targetFiles = checkTargetDir.getAllFiles(recursively);
				for (File targetFile : targetFiles) {
					boolean found = false;
					String targetLocalFilename = targetFile.getLocalFilename();
					for (String sourceFileLocalName : sourceFileLocalNames) {
						if (sourceFileLocalName.equals(targetLocalFilename)) {
							found = true;
							break;
						}
					}
					if (!found) {
						foundSomeProblem = true;
						result.append("<div class='line'>");
						result.append("<span class='error'>" + targetFile.getCanonicalFilename() + "</span>");
						result.append("</div>");
					}
				}
			}
		}

		result.append("</div>");

		if (foundSomeProblem) {
			localInfo += result.toString();
			localInfoShort += "<span class='error'>Music backup problems!</span> ";
		}
	}

	private static void addVmInfo(VmInfo vmInfo, String origin, String which,
		MissionControlDatabase missionControlDatabase) {

		Directory thisDir = new Directory(".");
		IoUtils.execute(thisDir.getAbsoluteDirname() + "\\" + SCRIPTS_DIR + "\\" + origin + "_df_" + which + ".bat");

		SimpleFile dfDbFile = new SimpleFile(thisDir, origin + "_out_" + which + ".txt");
		List<String> lines = dfDbFile.getContents();

		boolean nonsense = false;
		int highestPerc = 0;
		String highestFs = "";

		for (String line : lines) {
			if ("".equals(line.trim())) {
				continue;
			}
			if (line.startsWith("Filesystem ")) {
				continue;
			}
			if (line.startsWith("load pubkey ")) {
				continue;
			}
			if (line.startsWith("/dev/loop") && line.contains("100% /snap/")) {
				continue;
			}

			// transform "/dev/sda1 ... 50% /" into just "50%"
			String fs = line.substring(0, line.indexOf(" "));
			line = line.trim();
			line = line.substring(0, line.lastIndexOf(" "));
			line = line.trim();
			line = line.substring(line.lastIndexOf(" ") + 1);
			if (line.endsWith("%")) {
				line = line.substring(0, line.length() - 1);
				int curPerc = StrUtils.strToInt(line);
				if (curPerc > highestPerc) {
					highestPerc = curPerc;
					highestFs = fs;
				}
			} else {
				nonsense = true;
			}
		}

		String result = "";
		if (nonsense) {
			result += "<span class='error'>Responded with nonsense!</span>";
			missionControlDatabase.addDfDatapoint(new Date(), origin, which, null);
		} else {
			if (highestPerc < 30) {
				result += "<span class='awesome'>";
			}
			if (highestPerc >= 80) {
				if (highestPerc >= 90) {
					result += "<span class='error'>";
				} else {
					result += "<span class='warning'>";
				}
			}
			result += highestFs + " is to " + highestPerc + "% full";
			if ((highestPerc < 30) || (highestPerc >= 80)) {
				result += "</span>";
			}
			missionControlDatabase.addDfDatapoint(new Date(), origin, which, highestPerc);
		}

		vmInfo.set(which, result);
	}

	private static void addWebInfo(WebInfo webInfo, String origin, String which, String url,
		MissionControlDatabase missionControlDatabase) {

		WebAccessedCallback callback = new WebInfoCallback(webInfo, origin, which, missionControlDatabase);

		WebAccessor.getAsynch(url, callback);
	}

	public static String getLocalInfo() {
		return localInfo;
	}

	public static String getLocalInfoShort() {
		return localInfoShort;
	}

}
