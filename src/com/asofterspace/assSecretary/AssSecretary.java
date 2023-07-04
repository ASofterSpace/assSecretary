/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary;

import com.asofterspace.assSecretary.facts.FactDatabase;
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
	public final static String VERSION_NUMBER = "0.0.3.9(" + Utils.TOOLBOX_VERSION_NUMBER + ")";
	public final static String VERSION_DATE = "21. October 2020 - 4. May 2023";

	private static Database database;

	private static MissionControlDatabase missionControlDatabase;
	private static VmInfo vmInfo;
	private static WebInfo webInfo;
	private static String memeInfo = "";
	private static String memeInfoShort = "<span class='awesome'>Memes all different</span>";


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
		QuickDatabase quickDB = new QuickDatabase(dataDir);

		missionControlDatabase = new MissionControlDatabase(dataDir, "mission_control");

		// this one can be static, as it does not ever really change anymore :)
		LtcDatabase.init(dataDir);

		TaskDatabase taskDatabase = new TaskDatabase(dataDir);
		FactDatabase factDatabase = new FactDatabase(factDir);
		TaskCtrl taskCtrl = new TaskCtrl(database, taskDatabase, webRoot, uploadDir);


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

			Server server = new Server(webRoot, serverDir, database, taskCtrl, factDatabase, quickDB);

			server.setWhitelist(whitelist);

			boolean async = true;

			server.serve(async);


			System.out.println("Performing startup tasks...");

			runStartupTasks();


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

		webInfo = new WebInfo();
		vmInfo = new VmInfo();

		addWebInfo(webInfo, "asofterspace", "assEn", "https://www.asofterspace.com/", missionControlDatabase);
		addWebInfo(webInfo, "asofterspace", "assDe", "https://www.asofterspace.de/", missionControlDatabase);

		addWebInfo(webInfo, "fem*streik", "femOrg", "https://feministischerstreik.org/", missionControlDatabase);

		addVmInfo(vmInfo, "skyhook", "db", missionControlDatabase);
		addVmInfo(vmInfo, "skyhook", "f1", missionControlDatabase);
		addVmInfo(vmInfo, "skyhook", "f2", missionControlDatabase);
		addWebInfo(webInfo, "skyhook", "skyWeb", "https://skyhook.is/", missionControlDatabase);
		addWebInfo(webInfo, "skyhook", "skyApp", "https://app.skyhook.is/", missionControlDatabase);
		addWebInfo(webInfo, "skyhook", "skyDb", "http://skyhookdb.skyhook.is/phpmyadmin/", missionControlDatabase);

		// this call was unreliable, sometimes working, often giving nonsense - so we prefer to get rid of it,
		// such that all calls that are present always work, and when something is not working, it is actually
		// seen as important rather than as "just one more thing that always fails"
		// addVmInfo(vmInfo, "supervision-earth", "svs-backend", missionControlDatabase);
		addWebInfo(webInfo, "supervision-earth", "sveWeb", "https://supervision.earth/", missionControlDatabase);
		addWebInfo(webInfo, "supervision-earth", "sveApp", "https://supervisionspace.app/", missionControlDatabase);
		addWebInfo(webInfo, "supervision-earth", "sveLB", "http://svs-backend-loadbalancer-1910963306.eu-central-1.elb.amazonaws.com/", missionControlDatabase);

		checkMemeFolder();
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

		boolean foundSome = false;
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
					foundSome = true;
					result.append("<div class='line'>");
					result.append("<span class='error'>" + files.get(i).getCanonicalFilename() +
						"</span> and <span class='error'>" + files.get(j).getCanonicalFilename() + "</span>");
					result.append("</div>");
				}
			}
		}
		result.append("</div>");

		if (foundSome) {
			memeInfo = result.toString();
			memeInfoShort = "<span class='error'>Meme collision!</span>";
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

	public static String getMemeInfo() {
		return memeInfo;
	}

	public static String getMemeInfoShort() {
		return memeInfoShort;
	}

}
