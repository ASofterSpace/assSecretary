/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary;

import com.asofterspace.assSecretary.web.Server;
import com.asofterspace.toolbox.io.Directory;
import com.asofterspace.toolbox.io.JSON;
import com.asofterspace.toolbox.io.JsonFile;
import com.asofterspace.toolbox.io.JsonParseException;
import com.asofterspace.toolbox.io.TextFile;
import com.asofterspace.toolbox.utils.StrUtils;
import com.asofterspace.toolbox.Utils;
import com.asofterspace.toolbox.web.WebTemplateEngine;

import java.util.List;


public class AssSecretary {

	public final static String DATA_DIR = "config";
	public final static String SERVER_DIR = "server";
	public final static String WEB_ROOT_DIR = "deployed";

	public final static String PROGRAM_TITLE = "assSecretary (Hugo)";
	public final static String VERSION_NUMBER = "0.0.0.1(" + Utils.TOOLBOX_VERSION_NUMBER + ")";
	public final static String VERSION_DATE = "21. Oct 2020 - 21. Oct 2020";


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

		Database database = new Database(dataDir);


		try {

			JsonFile jsonConfigFile = new JsonFile(serverDir, "webengine.json");
			JSON jsonConfig = jsonConfigFile.getAllContents();
			jsonConfig.inc("version");
			jsonConfigFile.save(jsonConfig);

			List<String> whitelist = jsonConfig.getArrayAsStringList("files");

			System.out.println("Templating the web application...");

			WebTemplateEngine engine = new WebTemplateEngine(serverDir, jsonConfig);

			engine.compileTo(webRoot);


			TextFile indexFile = new TextFile(webRoot, "index.htm");
			String indexContent = indexFile.getContent();
			indexContent = StrUtils.replaceAll(indexContent, "[[USERNAME]]", database.getUsername());
			indexFile.saveContent(indexContent);


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

}
