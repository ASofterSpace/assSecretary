/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary;

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
import com.asofterspace.toolbox.io.TextFile;
import com.asofterspace.toolbox.projects.GenericProject;
import com.asofterspace.toolbox.projects.GenericProjectCtrl;
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

	public final static String PROGRAM_TITLE = "assSecretary (Hugo)";
	public final static String VERSION_NUMBER = "0.0.0.5(" + Utils.TOOLBOX_VERSION_NUMBER + ")";
	public final static String VERSION_DATE = "21. October 2020 - 17. November 2020";

	private static Database database;

	private static VmInfo vmInfo;
	private static WebInfo webInfo;
	private static String projHtmlStr;


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

		System.out.println("Loading database...");

		database = new Database(dataDir);
		MissionControlDatabase missionControlDatabase = new MissionControlDatabase(dataDir, "mission_control");

		TaskDatabase taskDatabase = new TaskDatabase(dataDir);
		TaskCtrl taskCtrl = new TaskCtrl(taskDatabase);


		try {

			JsonFile jsonConfigFile = new JsonFile(serverDir, "webengine.json");
			JSON jsonConfig = jsonConfigFile.getAllContents();
			jsonConfig.inc("version");
			jsonConfigFile.save(jsonConfig);

			List<String> whitelist = jsonConfig.getArrayAsStringList("files");

			System.out.println("Templating the web application...");

			WebTemplateEngine engine = new WebTemplateEngine(serverDir, jsonConfig);

			engine.compileTo(webRoot);


			GenericProjectCtrl projectCtrl = new GenericProjectCtrl(
				System.getProperty("java.class.path") + "/../../assWorkbench/server/projects");
			List<GenericProject> projects = projectCtrl.getGenericProjects();
			StringBuilder projHtml = new StringBuilder();

			for (GenericProject proj : projects) {
				projHtml.append("\n");
				projHtml.append("  <a href=\"localhost:3010/projects/" + proj.getShortName() + "/?open=logbook\" target=\"_blank\" class=\"project\" style=\"border-color: " + proj.getColor().toHexString() + "\">");
				projHtml.append("    <span class=\"vertAligner\"></span><img src=\"projectlogos/" + proj.getShortName() + "/logo.png\" />");
				projHtml.append("  </a>");
			}

			projHtmlStr = projHtml.toString();

			TextFile indexBaseFile = new TextFile(webRoot, "index.htm");
			String indexContent = indexBaseFile.getContent();
			indexContent = StrUtils.replaceAll(indexContent, "[[PROJECTS]]", projHtmlStr);
			String otherOriginsStr = "";
			otherOriginsStr += "<option value='asofterspace'>a softer space</option>";
			otherOriginsStr += "<option value='skyhook'>skyhook</option>";
			otherOriginsStr += "<option value='egscc'>EGS-CC</option>";
			otherOriginsStr += "<option value='recoded'>Recoded</option>";
			otherOriginsStr += "<option value='supervisionearth'>Supervision Earth</option>";
			otherOriginsStr += "<option value='gsmccc'>GSMC-CC</option>";
			otherOriginsStr += "<option value='behemoth'>Behemoth House Hunting</option>";
			otherOriginsStr += "<option value='sports'>Sports</option>";
			indexContent = StrUtils.replaceAll(indexContent, "[[OTHER_ORIGINS]]", otherOriginsStr);
			indexBaseFile.saveContent(indexContent);


			System.out.println("Starting the server on port " + database.getPort() + "...");

			Server server = new Server(webRoot, serverDir, database, taskCtrl);

			server.setWhitelist(whitelist);

			boolean async = true;

			server.serve(async);


			System.out.println("Performing startup tasks...");

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

			addVmInfo(vmInfo, "supervision-earth", "svs-backend", missionControlDatabase);
			addWebInfo(webInfo, "supervision-earth", "sveWeb", "https://supervision.earth/", missionControlDatabase);
			addWebInfo(webInfo, "supervision-earth", "sveApp", "https://supervisionspace.app/", missionControlDatabase);
			addWebInfo(webInfo, "supervision-earth", "sveLB", "http://svs-backend-loadbalancer-1910963306.eu-central-1.elb.amazonaws.com/", missionControlDatabase);

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

	public static String getProjHtmlStr() {
		return projHtmlStr;
	}

}
