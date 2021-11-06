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
import com.asofterspace.assSecretary.tasks.TaskCtrl;
import com.asofterspace.assSecretary.tasks.TaskDatabase;
import com.asofterspace.assSecretary.web.Server;
import com.asofterspace.toolbox.io.Directory;
import com.asofterspace.toolbox.io.IoUtils;
import com.asofterspace.toolbox.io.JSON;
import com.asofterspace.toolbox.io.JsonFile;
import com.asofterspace.toolbox.io.JsonParseException;
import com.asofterspace.toolbox.io.SimpleFile;
import com.asofterspace.toolbox.utils.StrUtils;
import com.asofterspace.toolbox.Utils;
import com.asofterspace.toolbox.web.WebAccessedCallback;
import com.asofterspace.toolbox.web.WebAccessor;
import com.asofterspace.toolbox.web.WebTemplateEngine;

import java.util.Date;
import java.util.List;


public class AssSecretary {

	public final static String DATA_DIR = "config";
	public final static String SERVER_DIR = "server";
	public final static String SCRIPTS_DIR = "scripts";
	public final static String WEB_ROOT_DIR = "deployed";
	public final static String FACT_DIR = "../assTrainer/config";

	public final static String PROGRAM_TITLE = "assSecretary (Hugo)";
	public final static String VERSION_NUMBER = "0.0.3.3(" + Utils.TOOLBOX_VERSION_NUMBER + ")";
	public final static String VERSION_DATE = "21. October 2020 - 5. November 2021";

	private static Database database;

	private static MissionControlDatabase missionControlDatabase;
	private static VmInfo vmInfo;
	private static WebInfo webInfo;


	public static void main(String[] args) {

		// let the Utils know in what program it is being used
		Utils.setProgramTitle(PROGRAM_TITLE);
		Utils.setVersionNumber(VERSION_NUMBER);
		Utils.setVersionDate(VERSION_DATE);

		if (args.length > 0) {
			if (args[0].equals("--version")) {
				System.out.println(Utils.getFullProgramIdentifierWithDate());
				return;
			}

			if (args[0].equals("--version_for_zip")) {
				System.out.println("version " + Utils.getVersionNumber());
				return;
			}
		}

		System.out.println("Hi, Hugo here...");

		System.out.println("Looking at directories...");

		Directory dataDir = new Directory(DATA_DIR);
		Directory serverDir = new Directory(SERVER_DIR);
		Directory webRoot = new Directory(WEB_ROOT_DIR);
		Directory factDir = new Directory(FACT_DIR);

		System.out.println("Loading database...");

		database = new Database(dataDir);

		missionControlDatabase = new MissionControlDatabase(dataDir, "mission_control");

		// this one can be static, as it does not ever really change anymore :)
		LtcDatabase.init(dataDir);

		TaskDatabase taskDatabase = new TaskDatabase(dataDir);
		FactDatabase factDatabase = new FactDatabase(factDir);
		TaskCtrl taskCtrl = new TaskCtrl(database, taskDatabase);


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

			Server server = new Server(webRoot, serverDir, database, taskCtrl, factDatabase);

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

}
