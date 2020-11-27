/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.web;

import com.asofterspace.assSecretary.accountant.MariDatabase;
import com.asofterspace.assSecretary.accountant.MariTaskCtrl;
import com.asofterspace.assSecretary.AssSecretary;
import com.asofterspace.assSecretary.Database;
import com.asofterspace.assSecretary.missionControl.McInfo;
import com.asofterspace.assSecretary.missionControl.VmInfo;
import com.asofterspace.assSecretary.missionControl.WebInfo;
import com.asofterspace.assSecretary.tasks.Task;
import com.asofterspace.assSecretary.tasks.TaskCtrl;
import com.asofterspace.toolbox.calendar.GenericTask;
import com.asofterspace.toolbox.io.Directory;
import com.asofterspace.toolbox.io.File;
import com.asofterspace.toolbox.io.JSON;
import com.asofterspace.toolbox.io.JsonParseException;
import com.asofterspace.toolbox.io.TextFile;
import com.asofterspace.toolbox.utils.DateUtils;
import com.asofterspace.toolbox.utils.Record;
import com.asofterspace.toolbox.utils.StrUtils;
import com.asofterspace.toolbox.web.WebAccessor;
import com.asofterspace.toolbox.web.WebServer;
import com.asofterspace.toolbox.web.WebServerAnswer;
import com.asofterspace.toolbox.web.WebServerAnswerInJson;
import com.asofterspace.toolbox.web.WebServerRequestHandler;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;


public class ServerRequestHandler extends WebServerRequestHandler {

	public final static String MARI_DATABASE_FILE = "../assAccountant/config/database.cnf";

	private Database db;

	private TaskCtrl taskCtrl;

	private Directory serverDir;


	public ServerRequestHandler(WebServer server, Socket request, Directory webRoot, Directory serverDir,
		Database db, TaskCtrl taskCtrl) {

		super(server, request, webRoot);

		this.db = db;

		this.taskCtrl = taskCtrl;

		this.serverDir = serverDir;
	}

	@Override
	protected void handlePost(String fileLocation) throws IOException {

		String jsonData = receiveJsonContent();

		if (jsonData == null) {
			respond(400);
			return;
		}


		// TODO :: catch some IO exceptions? (or make sure that none are thrown?)

		JSON json;
		try {
			json = new JSON(jsonData);
		} catch (JsonParseException e) {
			respond(400);
			return;
		}

		WebServerAnswer answer = new WebServerAnswerInJson("{\"success\": true}");

		try {

			switch (fileLocation) {

				case "/addSingleTask":

					Task addedOrEditedTask = addOrEditSingleTask(json);
					if (addedOrEditedTask == null) {
						return;
					}
					answer = new WebServerAnswerInJson(new JSON("{\"success\": true}"));
					break;

				case "/taskPreRelease":

					String editingId = json.getString("id");

					if (editingId == null) {
						respond(404);
						return;
					} else {
						Task task = taskCtrl.getTaskById(editingId);
						if (task != null) {
							// we release on the actual date on which we should have released the task, so that
							// if anything automated happens (like a title depending on the date), it happens
							// as it would have happened for the actual release date
							GenericTask newGenericTask = taskCtrl.releaseTaskOn(task, json.getDate("date"));
							if (newGenericTask instanceof Task) {
								Task newTask = (Task) newGenericTask;
								// and then switch the release date to today after the fact
								newTask.setReleasedDate(DateUtils.now());
								taskCtrl.save();
								answer = new WebServerAnswerInJson(new JSON("{\"success\": true, \"newId\": \"" + newTask.getId() + "\"}"));
							} else {
								respond(400);
								return;
							}
						} else {
							respond(404);
							return;
						}
					}
					break;

				case "/taskAddToShortList":

					editingId = json.getString("id");

					if (editingId == null) {
						respond(404);
						return;
					} else {
						taskCtrl.addTaskToShortListById(editingId);
						taskCtrl.save();
						answer = new WebServerAnswerInJson(new JSON("{\"success\": true}"));
					}
					break;

				case "/taskRemoveFromShortList":

					editingId = json.getString("id");

					if (editingId == null) {
						respond(404);
						return;
					} else {
						taskCtrl.removeTaskFromShortListById(editingId);
						taskCtrl.save();
						answer = new WebServerAnswerInJson(new JSON("{\"success\": true}"));
					}
					break;

				case "/taskPutOnShortListTomorrow":

					editingId = json.getString("id");

					if (editingId == null) {
						respond(404);
						return;
					} else {
						taskCtrl.removeTaskFromShortListById(editingId);
						taskCtrl.addTaskToShortListTomorrowById(editingId);
						taskCtrl.save();
						answer = new WebServerAnswerInJson(new JSON("{\"success\": true}"));
					}
					break;

				case "/taskDelete":

					editingId = json.getString("id");

					if (editingId == null) {
						respond(404);
						return;
					} else {
						boolean done = taskCtrl.deleteTaskById(editingId);
						if (done) {
							taskCtrl.save();
						}
						answer = new WebServerAnswerInJson(new JSON("{\"success\": " + done + "}"));
					}
					break;

				case "/doneSingleTask":

					addedOrEditedTask = addOrEditSingleTask(json);
					if (addedOrEditedTask == null) {
						return;
					}

					taskCtrl.setTaskToDone(addedOrEditedTask.getId());

					Date doneDate = json.getDate("doneDate");
					if (doneDate != null) {
						addedOrEditedTask.setDoneDate(doneDate);
					}

					taskCtrl.save();

					answer = new WebServerAnswerInJson(new JSON("{\"success\": true}"));

					// only continue if a copy of the task should actually be made
					if ((json.getBoolean("copyAfterwards") == null) || (json.getBoolean("copyAfterwards") == false)) {
						break;
					}

					// add new task
					Date newReleaseDate = DateUtils.addDays(DateUtils.now(), 1);
					Task newTask = taskCtrl.addAdHocTask(
						json.getString("title"),
						json.getString("details"),
						newReleaseDate,
						json.getString("origin"),
						json.getInteger("priority"),
						json.getInteger("priorityEscalationAfterDays"),
						json.getString("duration")
					);
					answer = new WebServerAnswerInJson(new JSON("{\"success\": " + (newTask != null) + ", " +
						"\"newId\": \"" + newTask.getId() + "\", \"newReleaseDate\": \"" +
						DateUtils.serializeDate(newReleaseDate) + "\"}"));

					break;

				case "/taskDone":
					boolean didSetToDone = taskCtrl.setTaskToDone(
						json.getString("id")
					);
					answer = new WebServerAnswerInJson(new JSON("{\"success\": " + didSetToDone + "}"));
					break;

				case "/taskUnDone":
					boolean didSetToNotDone = taskCtrl.setTaskToNotDone(
						json.getString("id")
					);
					answer = new WebServerAnswerInJson(new JSON("{\"success\": " + didSetToNotDone + "}"));
					break;

				case "/saveInbox":
					db.setInboxContent(json.getString("content"));
					db.save();
					answer = new WebServerAnswerInJson(new JSON("{\"success\": true}"));
					break;

				default:
					respond(404);
					return;
			}

		} catch (JsonParseException e) {
			respond(403);
			return;
		}

		respond(200, answer);
	}

	@Override
	protected WebServerAnswer answerGet(String location, String[] arguments) {

		if ("/task".equals(location)) {
			String id = null;
			for (String arg : arguments) {
				if (!arg.contains("=")) {
					continue;
				}
				String key = arg.substring(0, arg.indexOf("="));
				String value = arg.substring(arg.indexOf("=") + 1);
				if (key.equals("id")) {
					id = value;
				}
			}
			if (id != null) {
				Task task = taskCtrl.getTaskById(id);
				if (task != null) {
					JSON response = new JSON(Record.emptyObject());
					response.set("success", true);
					response.set("title", task.getTitle());
					response.set("details", StrUtils.join("\n", task.getDetails()));
					response.set("releaseDate", task.getReleasedDateStr());
					if (task.hasBeenDone()) {
						response.set("doneDate", DateUtils.serializeDate(task.getDoneDate()));
					} else {
						response.set("doneDate", null);
					}
					response.set("origin", task.getOrigin());
					response.set("priority", task.getPriority());
					response.set("priorityEscalationAfterDays", task.getPriorityEscalationAfterDays());
					response.set("duration", task.getDurationStr());
					return new WebServerAnswerInJson(response);
				}
			}
		}

		return null;
	}

	@Override
	protected File getFileFromLocation(String location, String[] arguments) {

		// get project logo files from assWorkbench
		if (location.startsWith("/projectlogos/") && location.endsWith(".png") && !location.contains("..")) {
			location = location.substring("/projectlogos/".length());
			location = System.getProperty("java.class.path") + "/../../assWorkbench/server/projects/" + location;
			File result = new File(location);
			if (result.exists()) {
				return result;
			}
		}

		String locEquiv = getWhitelistedLocationEquivalent(location);

		// if no root is specified, then we are just not serving any files at all
		// and if no location equivalent is found on the whitelist, we are not serving this request
		if ((webRoot != null) && (locEquiv != null)) {

			// serves images and text files directly from the server dir, rather than the deployed dir
			if (locEquiv.toLowerCase().endsWith(".jpg") || locEquiv.toLowerCase().endsWith(".pdf") ||
				locEquiv.toLowerCase().endsWith(".png") || locEquiv.toLowerCase().endsWith(".stp") ||
				locEquiv.toLowerCase().endsWith(".txt") || locEquiv.toLowerCase().endsWith(".stpu") ||
				locEquiv.toLowerCase().endsWith(".json")) {

				File result = new File(serverDir, locEquiv);
				if (result.exists()) {
					return result;
				}
			}


			// answering a request for general information
			if (locEquiv.equals("index.htm")) {

				System.out.println("Answering index request...");

				TextFile indexBaseFile = new TextFile(webRoot, locEquiv);
				String indexContent = indexBaseFile.getContent();

				indexContent = StrUtils.replaceAll(indexContent, "[[USERNAME]]", db.getUsername());

				Date now = new Date();
				// Today is Monday the 23rd of April 2027 and it is 08:37 right now. You are on planet Earth.
				String generalInfo = "Today is <span id='curdatetime'>" + DateUtils.getDayOfWeekNameEN(now) + " the " +
					StrUtils.replaceAll(DateUtils.serializeDateTimeLong(now, "<span class='sup'>", "</span>"), ", ", " and it is ") +
					"</span> right now. You are currently on planet Earth.";
				indexContent = StrUtils.replaceAll(indexContent, "[[GENERAL_INFO]]", generalInfo);


				String tabsHtml = "<div id='tabList'>";
				tabsHtml += "<a href='/inbox.htm'>Inbox</a>";
				tabsHtml += "<a href='/tasklog.htm'>Task Log</a>";
				tabsHtml += "<a href='/weekly.htm'>Weekly Plan</a>";
				tabsHtml += "</div>";

				indexContent = StrUtils.replaceAll(indexContent, "[[TABS]]", tabsHtml);


				String taskShortlistHtml = "";

				Date today = DateUtils.now();

				List<Task> shortlistTasks = taskCtrl.getTasksOnShortlist();

				Collections.sort(shortlistTasks, new Comparator<Task>() {
					public int compare(Task a, Task b) {
						return a.getCurrentPriority(today) - b.getCurrentPriority(today);
					}
				});

				if (shortlistTasks.size() == 0) {
					taskShortlistHtml += "<div>The task shortlist is empty - well done!</div>";
				} else {
					taskShortlistHtml += "<div style='padding-bottom:0;'>Here is the task shortlist for today:</div>";
					taskShortlistHtml += "<div>";
					boolean historicalView = false;
					boolean reducedView = false;
					boolean onShortlist = true;
					for (Task shortlistTask : shortlistTasks) {
						taskShortlistHtml += shortlistTask.toHtmlStr(historicalView, reducedView, onShortlist, today);
					}
					taskShortlistHtml += "</div>";
				}

				indexContent = StrUtils.replaceAll(indexContent, "[[TASK_SHORTLIST]]", taskShortlistHtml);


				String mariHtml = "";

				String problems = WebAccessor.get("http://localhost:3011/unacknowledged-problems");

				MariDatabase mariDatabase = new MariDatabase(MARI_DATABASE_FILE);

				if ((problems == null) || "".equals(problems) || !mariDatabase.isAvailable()) {

					mariHtml += "<div>";
					mariHtml += "<div><span class='warning'>I haven't heard anything from Mari recently,</span> I wonder how she is doing...</div>";

					if ((problems == null) || "".equals(problems)) {
						mariHtml += "<div>(She did not react to me sending a web request to her.)</div>";
					}

					if (!mariDatabase.isAvailable()) {
						mariHtml += "<div>(She did not let me access her database.)</div>";
					}
					mariHtml += "</div>";

				} else {

					boolean talkedToMari = false;

					MariTaskCtrl mariTaskCtrl = new MariTaskCtrl(mariDatabase);

					List<GenericTask> tasks = mariTaskCtrl.getCurrentTaskInstances();
					/*
					int upcomingDays = 5;
					List<GenericTask> upcomingTasks = mariTaskCtrl.getUpcomingTaskInstances(upcomingDays);
					*/

					if (!"no problems".equals(problems)) {
						mariHtml += "<div>";
						mariHtml += "<div>I talked to Mari and she mentioned these problems:</div>";
						mariHtml += problems;
						mariHtml += "</div>";
						talkedToMari = true;
					}

					if (tasks.size() > 0) {
						mariHtml += "<div>";
						if (!talkedToMari) {
							mariHtml += "<div>I talked to Mari, and she ";
						} else {
							mariHtml += "<div>She also ";
						}
						mariHtml += "mentioned that these things should be done today:</div>";
						for (GenericTask task : tasks) {
							mariHtml += "<div class='line'><span class='warning'>" + task.getReleasedDateStr() + " " + task.getTitle() + "</span></div>";
						}
						mariHtml += "</div>";
						talkedToMari = true;
					}

					/*
					if (upcomingTasks.size() > 0) {
						mariHtml += "<div>";
						if (!talkedToMari) {
							mariHtml += "<div>I talked to Mari, and she ";
						} else {
							mariHtml += "<div>She also ";
						}
						mariHtml += "mentioned these things coming up in the next " + upcomingDays + " days:</div>";
						for (GenericTask task : upcomingTasks) {
							mariHtml += "<div class='line'>" + task.getReleasedDateStr() + " " + task.getTitle() + "</div>";
						}
						mariHtml += "</div>";
						talkedToMari = true;
					}
					*/
				}
				indexContent = StrUtils.replaceAll(indexContent, "[[MARI]]", mariHtml);

				VmInfo vmInfo = AssSecretary.getVmInfo();
				WebInfo webInfo = AssSecretary.getWebInfo();
				StringBuilder vmStatsHtml = new StringBuilder();

				addLine(vmStatsHtml, "asofterspace.com", webInfo, "assEn");
				addLine(vmStatsHtml, "asofterspace.de", webInfo, "assDe");

				addLine(vmStatsHtml, "Skyhook DB", vmInfo, "db");
				addLine(vmStatsHtml, "Skyhook DB", webInfo, "skyDb");
				addLine(vmStatsHtml, "Skyhook F1", vmInfo, "f1");
				addLine(vmStatsHtml, "Skyhook F2", vmInfo, "f2");
				addLine(vmStatsHtml, "Skyhook App", webInfo, "skyApp");
				addLine(vmStatsHtml, "Skyhook Webpage", webInfo, "skyWeb");

				addLine(vmStatsHtml, "Supervision Earth svs-backend", vmInfo, "svs-backend");
				addLine(vmStatsHtml, "Supervision Earth App Frontend", webInfo, "sveApp");
				addLine(vmStatsHtml, "Supervision Earth App Backend", webInfo, "sveLB");
				addLine(vmStatsHtml, "Supervision Earth Webpage", webInfo, "sveWeb");

				if (vmStatsHtml.length() < 1) {
					vmStatsHtml.insert(0, "<div>I have checked the VM disk statusses and web accessibility - and all is fine.");
				} else {
					vmStatsHtml.insert(0, "<div>I have checked the VM disk statusses and web accessibility - the status is:");
				}

				vmStatsHtml.append("</div>");
				indexContent = StrUtils.replaceAll(indexContent, "[[VM_STATS]]", vmStatsHtml.toString());


				List<Task> tasks = taskCtrl.getCurrentTaskInstancesAsTasks();

				Collections.sort(tasks, new Comparator<Task>() {
					public int compare(Task a, Task b) {
						return a.getCurrentPriority(today) - b.getCurrentPriority(today);
					}
				});

				String taskHtml = "";
				boolean historicalView = false;
				boolean reducedView = false;
				boolean onShortlist = false;
				if (tasks.size() > 0) {
					for (Task task : tasks) {
						taskHtml += task.toHtmlStr(historicalView, reducedView, onShortlist, today);
					}
				}

				indexContent = StrUtils.replaceAll(indexContent, "[[TASKS]]", taskHtml);


				indexContent = StrUtils.replaceAll(indexContent, "[[MISSION_CONTROL_PREVIEW]]", getMissionControlHtml(false));

				indexContent = StrUtils.replaceAll(indexContent, "[[CURDATE]]", DateUtils.serializeDate(DateUtils.now()));

				indexContent = StrUtils.replaceAll(indexContent, "[[MINI_CALENDAR]]", getMiniCalendarHtml());

				locEquiv = "_" + locEquiv;
				TextFile indexFile = new TextFile(webRoot, locEquiv);
				indexFile.saveContent(indexContent);
			}


			// answering a request for the inbox tab
			if (locEquiv.equals("inbox.htm")) {

				System.out.println("Answering inbox request...");

				TextFile indexBaseFile = new TextFile(webRoot, locEquiv);
				String indexContent = indexBaseFile.getContent();

				indexContent = StrUtils.replaceAll(indexContent, "[[INBOX_CONTENT]]", db.getInboxContent());

				indexContent = StrUtils.replaceAll(indexContent, "[[PROJECTS]]", AssSecretary.getProjHtmlStr());

				indexContent = StrUtils.replaceAll(indexContent, "[[CURDATE]]", DateUtils.serializeDate(DateUtils.now()));

				indexContent = StrUtils.replaceAll(indexContent, "[[MINI_CALENDAR]]", getMiniCalendarHtml());

				locEquiv = "_" + locEquiv;
				TextFile indexFile = new TextFile(webRoot, locEquiv);
				indexFile.saveContent(indexContent);
			}


			// answering a request for the task log tab
			if (locEquiv.equals("tasklog.htm")) {

				System.out.println("Answering task log request...");

				TextFile indexBaseFile = new TextFile(webRoot, locEquiv);
				String indexContent = indexBaseFile.getContent();

				List<Task> tasks = taskCtrl.getDoneTaskInstancesAsTasks();

				try {
					// get task instances from Mari
					JSON mariTasks = new JSON(WebAccessor.get("http://localhost:3011/taskInstances"));

					// copy the task list we are currently looking at
					tasks = new ArrayList<>(tasks);

					// generate tasks locally based on the data we got from Mari
					List<Record> mariRecs = mariTasks.getValues();
					for (Record mariRec : mariRecs) {
						Task localTask = taskCtrl.taskFromMariRecord(mariRec);
						if (localTask.hasBeenDone()) {
							tasks.add(localTask);
						}
					}

				} catch (JsonParseException e) {
					System.out.println("Mari responded with nonsense for a task instances request!");
				}

				Collections.sort(tasks, new Comparator<Task>() {
					public int compare(Task a, Task b) {
						Date aDone = a.getDoneDate();
						Date bDone = b.getDoneDate();
						if (DateUtils.isSameDay(aDone, bDone)) {
							return a.getCurrentPriority(aDone) - b.getCurrentPriority(bDone);
						}
						if (aDone.before(bDone)) {
							return 1;
						}
						return -1;
					}
				});

				String taskHtml = "";
				boolean historicalView = true;
				boolean reducedView = false;
				boolean onShortlist = false;
				if (tasks.size() > 0) {
					Date prevDate = tasks.get(0).getDoneDate();
					for (Task task : tasks) {
						Date curDate = task.getDoneDate();
						if (!DateUtils.isSameDay(curDate, prevDate)) {
							prevDate = curDate;
							taskHtml += "<div class='separator_top'>&nbsp;</div>";
							taskHtml += "<div class='separator_bottom'>&nbsp;</div>";
						}
						taskHtml += task.toHtmlStr(historicalView, reducedView, onShortlist, curDate);
					}
				}

				indexContent = StrUtils.replaceAll(indexContent, "[[TASKS]]", taskHtml);

				indexContent = StrUtils.replaceAll(indexContent, "[[PROJECTS]]", AssSecretary.getProjHtmlStr());

				indexContent = StrUtils.replaceAll(indexContent, "[[CURDATE]]", DateUtils.serializeDate(DateUtils.now()));

				indexContent = StrUtils.replaceAll(indexContent, "[[MINI_CALENDAR]]", getMiniCalendarHtml());

				locEquiv = "_" + locEquiv;
				TextFile indexFile = new TextFile(webRoot, locEquiv);
				indexFile.saveContent(indexContent);
			}


			// answering a request for the weekly view
			if (locEquiv.equals("weekly.htm")) {

				System.out.println("Answering weekly view request...");

				TextFile indexBaseFile = new TextFile(webRoot, locEquiv);
				String indexContent = indexBaseFile.getContent();

				String weeklyHtmlStr = "";
				// the actual today that it really is... today
				Date actualToday = DateUtils.now();
				// the "today" for which the chart is generated, by default the actual today
				Date today = actualToday;
				for (String arg : arguments) {
					if (arg.contains("=")) {
						String key = arg.substring(0, arg.indexOf("="));
						if ("date".equals(key)) {
							today = DateUtils.parseDate(arg.substring(arg.indexOf("=") + 1));
						}
					}
				}
				List<Date> weekDays = DateUtils.getWeekForDate(today);

				List<Task> tasks = taskCtrl.getAllTaskInstancesAsTasks();

				try {
					// get task instances from Mari
					JSON mariTasks = new JSON(WebAccessor.get("http://localhost:3011/taskInstances?from=" +
						DateUtils.serializeDate(weekDays.get(0)) + "&to=" + DateUtils.serializeDate(weekDays.get(6))));

					// copy the task list we are currently looking at
					tasks = new ArrayList<>(tasks);

					// generate tasks locally based on the data we got from Mari
					List<Record> mariRecs = mariTasks.getValues();
					for (Record mariRec : mariRecs) {
						Task localTask = taskCtrl.taskFromMariRecord(mariRec);
						tasks.add(localTask);
					}

				} catch (JsonParseException e) {
					System.out.println("Mari responded with nonsense for a task instances request!");
				}

				List<GenericTask> baseTasksForSchedule = taskCtrl.getTasks();

				try {
					// get scheduled tasks from Mari
					JSON mariTasks = new JSON(WebAccessor.get("http://localhost:3011/tasks"));

					// copy the task list we are currently looking at
					baseTasksForSchedule = new ArrayList<>(baseTasksForSchedule);

					// generate tasks locally based on the data we got from Mari
					List<Record> mariRecs = mariTasks.getValues();
					for (Record mariRec : mariRecs) {
						Task localTask = taskCtrl.taskFromMariRecord(mariRec);
						baseTasksForSchedule.add(localTask);
					}

				} catch (JsonParseException e) {
					System.out.println("Mari responded with nonsense for a tasks request!");
				}

				for (Date day : weekDays) {
					boolean isToday = DateUtils.isSameDay(actualToday, day);
					weeklyHtmlStr += "<div class='weekly_day";
					String boldness = "";
					if (isToday) {
						weeklyHtmlStr += " today";
						boldness = "font-weight: bold;";
					}
					weeklyHtmlStr += "'>";
					weeklyHtmlStr += "<div style='text-align: center; " + boldness + "'>" + DateUtils.serializeDate(day) + "</div>";
					weeklyHtmlStr += "<div style='text-align: center; " + boldness + " padding-bottom: 10pt;'>" + DateUtils.getDayOfWeekNameEN(day) + "</div>";

					List<Task> tasksToday = new ArrayList<>();

					// check for all task instances if they apply today
					for (Task task : tasks) {
						if (task.appliesTo(day)) {
							tasksToday.add(task);
						}
					}

					// for days in the future, also add ghost tasks (non-instances) - but no in the past,
					// as there we would expect real instances to have been created instead!
					if (day.after(actualToday)) {
						for (GenericTask task : baseTasksForSchedule) {
							if (task instanceof Task) {
								if (task.isScheduledOn(day)) {
									tasksToday.add((Task) task);
								}
							}
						}
					}

					Collections.sort(tasksToday, new Comparator<Task>() {
						public int compare(Task a, Task b) {
							return a.getCurrentPriority(day) - b.getCurrentPriority(day);
						}
					});

					for (Task task : tasksToday) {
						boolean historicalView = false;
						boolean reducedView = true;
						boolean onShortlist = false;
						weeklyHtmlStr += task.toHtmlStr(historicalView, reducedView, onShortlist, day);
					}

					weeklyHtmlStr += "</div>";
				}

				indexContent = StrUtils.replaceAll(indexContent, "[[WEEKLY_PLAN]]", weeklyHtmlStr);

				indexContent = StrUtils.replaceAll(indexContent, "[[PREV_DATE_YEAR]]", DateUtils.serializeDate(DateUtils.addDays(today, -365)));
				indexContent = StrUtils.replaceAll(indexContent, "[[PREV_DATE_MONTH]]", DateUtils.serializeDate(DateUtils.addDays(today, -30)));
				indexContent = StrUtils.replaceAll(indexContent, "[[PREV_DATE_WEEK]]", DateUtils.serializeDate(DateUtils.addDays(today, -7)));
				indexContent = StrUtils.replaceAll(indexContent, "[[NEXT_DATE_WEEK]]", DateUtils.serializeDate(DateUtils.addDays(today, 7)));
				indexContent = StrUtils.replaceAll(indexContent, "[[NEXT_DATE_MONTH]]", DateUtils.serializeDate(DateUtils.addDays(today, 30)));
				indexContent = StrUtils.replaceAll(indexContent, "[[NEXT_DATE_YEAR]]", DateUtils.serializeDate(DateUtils.addDays(today, 365)));

				indexContent = StrUtils.replaceAll(indexContent, "[[PROJECTS]]", AssSecretary.getProjHtmlStr());

				indexContent = StrUtils.replaceAll(indexContent, "[[CURDATE]]", DateUtils.serializeDate(DateUtils.now()));

				indexContent = StrUtils.replaceAll(indexContent, "[[MINI_CALENDAR]]", getMiniCalendarHtml());

				locEquiv = "_" + locEquiv;
				TextFile indexFile = new TextFile(webRoot, locEquiv);
				indexFile.saveContent(indexContent);
			}


			// answering a request for the a softer space mission control center
			if (locEquiv.equals("missioncontrol.htm")) {

				System.out.println("Answering mission control request...");

				TextFile indexBaseFile = new TextFile(webRoot, locEquiv);
				String indexContent = indexBaseFile.getContent();

				indexContent = StrUtils.replaceAll(indexContent, "[[MISSION_CONTROL]]", getMissionControlHtml(true));

				indexContent = StrUtils.replaceAll(indexContent, "[[PROJECTS]]", AssSecretary.getProjHtmlStr());

				locEquiv = "_" + locEquiv;
				TextFile indexFile = new TextFile(webRoot, locEquiv);
				indexFile.saveContent(indexContent);
			}


			// actually get the file
			return webRoot.getFile(locEquiv);
		}

		// if the file was not found on the whitelist, do not return it
		// - even if it exists on the server!
		return null;
	}

	private String getMissionControlHtml(boolean createLinks) {

		String mcHtml = "";

		VmInfo vmInfo = AssSecretary.getVmInfo();
		WebInfo webInfo = AssSecretary.getWebInfo();

		String companyStart = "<div class='company_outer'>";
		String companyEnd = "</div>";
		String machineStart = "<div class='machine_outer'><div class='machine_inner'>";
		String machineEnd = "</div></div>";

		mcHtml += companyStart;
		mcHtml += machineStart + "ASS Odyssey MM-01" + machineEnd;
		mcHtml += machineStart + "asofterspace.com<br>" + webInfo.get("assEn") + machineEnd;
		mcHtml += machineStart + "asofterspace.de<br>" + webInfo.get("assDe") + machineEnd;
		mcHtml += "<img class='logo' src='projectlogos/asofterspace/logo.png' />";
		mcHtml += companyEnd;

		mcHtml += companyStart;
		mcHtml += machineStart + "Webpage<br>" + webInfo.get("skyWeb") + machineEnd;
		mcHtml += machineStart + "App<br>" + webInfo.get("skyApp") + machineEnd;
		mcHtml += machineStart + "F1<br>" + vmInfo.get("f1") + machineEnd;
		mcHtml += machineStart + "F2<br>" + vmInfo.get("f2") + machineEnd;
		mcHtml += machineStart + "DB<br>" + webInfo.get("skyDb") + "<br>" + vmInfo.get("db") + machineEnd;
		mcHtml += "<img class='logo' src='projectlogos/skyhook/logo.png' />";
		mcHtml += companyEnd;

		mcHtml += companyStart;
		mcHtml += machineStart + "Webpage<br>" + webInfo.get("sveWeb") + machineEnd;
		mcHtml += machineStart + "App Frontend<br>" + webInfo.get("sveApp") + machineEnd;
		mcHtml += machineStart + "App Backend<br>" + webInfo.get("sveLB") + "<br>" + vmInfo.get("svs-backend") + machineEnd;
		mcHtml += "<img class='logo' src='projectlogos/supervisionearth/logo.png' />";
		mcHtml += companyEnd;

		return mcHtml;
	}

	private void addLine(StringBuilder vmStatsHtml, String name, McInfo mcInfo, String key) {
		if (mcInfo.isImportant(key)) {
			vmStatsHtml.append("<div class='line'>" + name + ": " + mcInfo.get(key) + "</div>");
		}
	}

	private String getMiniCalendarHtml() {
		String html = "";
		Date today = DateUtils.now();
		for (int weekCounter = 0; weekCounter < 6; weekCounter++) {
			List<Date> week = DateUtils.getWeekForDate(today);
			html += "<tr>";
			html += "<td>";
			html += DateUtils.getMonthNameShortEN(week.get(0));
			html += "</td>";
			for (Date day : week) {
				html += "<td style='cursor: pointer;";
				if (weekCounter == 0) {
					if (DateUtils.isSameDay(today, day)) {
						html += " background: rgba(150, 200, 250, 0.3)'";
					}
				}
				html += "' onclick='document.getElementById(\"singleTaskReleaseDate\").value = \"";
				html += DateUtils.serializeDate(day) + "\"'>";
				html += DateUtils.getDayOfMonth(day) + "</td>";
			}
			html += "</tr>";
			today = DateUtils.addDays(today, 7);
		}
		return html;
	}

	private void setDoneDateBasedOnJson(Task task, Record json) {
		Date doneDate = json.getDate("doneDate");
		if (doneDate == null) {
			task.setDone(false);
			task.setDoneDate(null);
		} else {
			task.setDone(true);
			task.setDoneDate(doneDate);
			taskCtrl.removeTaskFromShortListById(task.getId());
			taskCtrl.save();
		}
	}

	private Task addOrEditSingleTask(JSON json) throws IOException {

		Task result = null;

		String editingId = json.getString("editingId");

		Date firstReleaseDate = json.getDate("releaseDate");
		if (firstReleaseDate == null) {
			respond(404);
			return null;
		}

		if (editingId == null) {
			// add new task
			Task newTask = taskCtrl.addAdHocTask(
				json.getString("title"),
				json.getString("details"),
				firstReleaseDate,
				json.getString("origin"),
				json.getInteger("priority"),
				json.getInteger("priorityEscalationAfterDays"),
				json.getString("duration")
			);
			setDoneDateBasedOnJson(newTask, json);
			result = newTask;

		} else {
			// edit existing task
			Task task = taskCtrl.getTaskById(editingId);
			if (task != null) {
				task.setTitle(json.getString("title"));
				task.setDetailsStr(json.getString("details"));
				task.setReleasedDate(json.getDate("releaseDate"));
				setDoneDateBasedOnJson(task, json);
				task.setOrigin(json.getString("origin"));
				task.setPriority(json.getInteger("priority"));
				task.setPriorityEscalationAfterDays(json.getInteger("priorityEscalationAfterDays"));
				task.setDurationStr(json.getString("duration"));
				taskCtrl.save();
				result = task;
			} else {
				respond(404);
				return null;
			}
		}

		Date lastReleaseDate = json.getDate("releaseUntil");
		if (lastReleaseDate != null) {
			List<Date> releaseDates = DateUtils.listDaysFromTo(
				DateUtils.addDays(firstReleaseDate, 1),
				DateUtils.addDays(lastReleaseDate, 1)
			);
			for (Date relDate : releaseDates) {
				Task newTask = taskCtrl.addAdHocTask(
					json.getString("title"),
					json.getString("details"),
					relDate,
					json.getString("origin"),
					json.getInteger("priority"),
					json.getInteger("priorityEscalationAfterDays"),
					json.getString("duration")
				);
				setDoneDateBasedOnJson(newTask, json);
			}
		}

		return result;
	}

}
