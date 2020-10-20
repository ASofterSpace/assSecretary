/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.web;

import com.asofterspace.assSecretary.Database;
import com.asofterspace.toolbox.io.Directory;
import com.asofterspace.toolbox.web.WebServer;
import com.asofterspace.toolbox.web.WebServerRequestHandler;

import java.net.Socket;


public class Server extends WebServer {

	private Database db;

	private Directory serverDir;


	public Server(Directory webRoot, Directory serverDir, Database db) {

		super(webRoot, db.getPort());

		this.db = db;

		this.serverDir = serverDir;
	}

	protected WebServerRequestHandler getHandler(Socket request) {
		return new ServerRequestHandler(this, request, webRoot, serverDir, db);
	}

}
