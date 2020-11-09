/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary;

import com.asofterspace.assSecretary.skyhook.VmInfo;
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
import com.asofterspace.toolbox.web.WebTemplateEngine;

import java.util.List;


public class AssSecretary {

	public final static String DATA_DIR = "config";
	public final static String SERVER_DIR = "server";
	public final static String SCRIPTS_DIR = "scripts";
	public final static String WEB_ROOT_DIR = "deployed";

	public final static String PROGRAM_TITLE = "assSecretary (Hugo)";
	public final static String VERSION_NUMBER = "0.0.0.3(" + Utils.TOOLBOX_VERSION_NUMBER + ")";
	public final static String VERSION_DATE = "21. October 2020 - 9. November 2020";

	private static Database database;

	private static VmInfo skyhookVmInfo;


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


		try {

			JsonFile jsonConfigFile = new JsonFile(serverDir, "webengine.json");
			JSON jsonConfig = jsonConfigFile.getAllContents();
			jsonConfig.inc("version");
			jsonConfigFile.save(jsonConfig);

			List<String> whitelist = jsonConfig.getArrayAsStringList("files");

			System.out.println("Templating the web application...");

			WebTemplateEngine engine = new WebTemplateEngine(serverDir, jsonConfig);

			engine.compileTo(webRoot);


			System.out.println("Performing startup tasks...");

			skyhookVmInfo = new VmInfo();

			addVmInfo(skyhookVmInfo, "db");
			addVmInfo(skyhookVmInfo, "f1");
			addVmInfo(skyhookVmInfo, "f2");


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

			String projHtmlStr = projHtml.toString();

			TextFile indexBaseFile = new TextFile(webRoot, "index.htm");
			String indexContent = indexBaseFile.getContent();
			indexContent = StrUtils.replaceAll(indexContent, "[[PROJECTS]]", projHtmlStr);
			indexBaseFile.saveContent(indexContent);


			System.out.println("Starting the server on port " + database.getPort() + "...");

			Server server = new Server(webRoot, serverDir, database);

			server.setWhitelist(whitelist);

			boolean async = false;

			server.serve(async);


			System.out.println("Server done, all shut down and cleaned up! Have a nice day! :)");

		} catch (JsonParseException e) {

			System.out.println("Oh no! The input could not be parsed: " + e);
		}
	}

	public static Database getDatabase() {
		return database;
	}

	public static VmInfo getSkyhookVmInfo() {
		return skyhookVmInfo;
	}

	private static void addVmInfo(VmInfo skyhookVmInfo, String which) {

		Directory thisDir = new Directory(".");
		IoUtils.execute(thisDir.getAbsoluteDirname() + "\\" + SCRIPTS_DIR + "\\skyhook_df_" + which + ".bat");

		SimpleFile skyhookDfDb = new SimpleFile(thisDir, "skyhook_out_" + which + ".txt");
		List<String> lines = skyhookDfDb.getContents();

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
		}
		switch (which) {
			case "db":
				skyhookVmInfo.setDfDb(result);
				break;
			case "f1":
				skyhookVmInfo.setDfF1(result);
				break;
			case "f2":
				skyhookVmInfo.setDfF2(result);
				break;
		}
	}

}
