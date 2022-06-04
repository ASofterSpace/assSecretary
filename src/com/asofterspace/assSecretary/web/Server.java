/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.web;

import com.asofterspace.assSecretary.Database;
import com.asofterspace.assSecretary.facts.FactDatabase;
import com.asofterspace.assSecretary.QuickDatabase;
import com.asofterspace.assSecretary.tasks.TaskCtrl;
import com.asofterspace.toolbox.io.Directory;
import com.asofterspace.toolbox.web.WebServer;
import com.asofterspace.toolbox.web.WebServerRequestHandler;

import java.net.Socket;


public class Server extends WebServer {

	private Database db;

	private TaskCtrl taskCtrl;

	private FactDatabase factDatabase;

	private QuickDatabase quickDB;

	private Directory serverDir;


	public Server(Directory webRoot, Directory serverDir, Database db, TaskCtrl taskCtrl,
		FactDatabase factDatabase, QuickDatabase quickDB) {

		super(webRoot, db.getPort());

		this.db = db;

		this.taskCtrl = taskCtrl;

		this.factDatabase = factDatabase;

		this.quickDB = quickDB;

		this.serverDir = serverDir;
	}

	@Override
	protected WebServerRequestHandler getHandler(Socket request) {
		return new ServerRequestHandler(this, request, webRoot, serverDir, db, taskCtrl, factDatabase, quickDB);
	}

}
