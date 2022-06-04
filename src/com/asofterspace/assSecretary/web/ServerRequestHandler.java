/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.web;

import com.asofterspace.assSecretary.accountant.MariDatabase;
import com.asofterspace.assSecretary.accountant.MariTaskCtrl;
import com.asofterspace.assSecretary.AssSecretary;
import com.asofterspace.assSecretary.Database;
import com.asofterspace.assSecretary.facts.Fact;
import com.asofterspace.assSecretary.facts.FactDatabase;
import com.asofterspace.assSecretary.ltc.LtcDatabase;
import com.asofterspace.assSecretary.missionControl.McInfo;
import com.asofterspace.assSecretary.missionControl.VmInfo;
import com.asofterspace.assSecretary.missionControl.WebInfo;
import com.asofterspace.assSecretary.QuickDatabase;
import com.asofterspace.assSecretary.tasks.Task;
import com.asofterspace.assSecretary.tasks.TaskCtrl;
import com.asofterspace.toolbox.calendar.GenericTask;
import com.asofterspace.toolbox.calendar.TaskCtrlBase;
import com.asofterspace.toolbox.io.Directory;
import com.asofterspace.toolbox.io.File;
import com.asofterspace.toolbox.io.HTML;
import com.asofterspace.toolbox.io.JSON;
import com.asofterspace.toolbox.io.JsonParseException;
import com.asofterspace.toolbox.io.TextFile;
import com.asofterspace.toolbox.utils.DateUtils;
import com.asofterspace.toolbox.utils.Record;
import com.asofterspace.toolbox.utils.SortUtils;
import com.asofterspace.toolbox.utils.StrUtils;
import com.asofterspace.toolbox.virtualEmployees.SideBarCtrl;
import com.asofterspace.toolbox.virtualEmployees.SideBarEntryForEmployee;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class ServerRequestHandler extends WebServerRequestHandler {

	public final static String MARI_DATABASE_FILE = "../assAccountant/config/database.cnf";

	private Database db;

	private TaskCtrl taskCtrl;

	private FactDatabase factDatabase;

	private QuickDatabase quickDB;

	private Directory serverDir;

	private static boolean firstIndexCall = true;

	private final static SideBarEntryForEmployee EMPLOYEE_HUGO = new SideBarEntryForEmployee("Hugo");


	public ServerRequestHandler(WebServer server, Socket request, Directory webRoot, Directory serverDir,
		Database db, TaskCtrl taskCtrl, FactDatabase factDatabase, QuickDatabase quickDB) {

		super(server, request, webRoot);

		this.db = db;

		this.taskCtrl = taskCtrl;

		this.factDatabase = factDatabase;

		this.quickDB = quickDB;

		this.serverDir = serverDir;
	}

	@Override
	protected void handlePost(String fileLocation) throws IOException {

		quickDB.access();

		String jsonData = receiveJsonContent();

		if (jsonData == null) {
			respond(400);
			return;
		}

		WebServerAnswer sideBarAnswer = SideBarCtrl.handlePost(fileLocation, jsonData);
		if (sideBarAnswer != null) {
			respond(200, sideBarAnswer);
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

				case "/addRepeatingTask":

					addedOrEditedTask = addOrEditRepeatingTask(json);
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
								taskCtrl.addTaskToShortListById(newTask.getId());
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

				case "/tasksPutOnShortListTomorrow":

					List<String> editingIds = json.getArrayAsStringList("ids");

					if (editingIds == null) {
						respond(404);
						return;
					} else {
						for (String id : editingIds) {
							taskCtrl.removeTaskFromShortListById(id);
							taskCtrl.addTaskToShortListTomorrowById(id);
						}
						taskCtrl.save();
						answer = new WebServerAnswerInJson(new JSON("{\"success\": true}"));
					}
					break;

				case "/taskDelete":

					List<String> deletingIds = json.getArrayAsStringList("id");

					if (deletingIds == null) {
						respond(404);
						return;
					} else {
						taskCtrl.deleteTasksByIds(deletingIds);
						taskCtrl.save();
						answer = new WebServerAnswerInJson(new JSON("{\"success\": true}"));
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
						json.getString("duration"),
						json.getBoolean(TaskCtrl.SHOW_AS_SCHEDULED),
						json.getBoolean(TaskCtrl.AUTO_CLEAN_TASK)
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

				case "/taskUpDown":
					List<Task> tasks = getDoneTaskInstancesSortedByDoneDateTime();
					String id = json.getString("id");
					Integer direction = json.getInteger("direction");
					Task curTask = null;
					Date curDate = null;
					for (Task task : tasks) {
						if (task.hasId(id)) {
							curTask = task;
							curDate = task.getDoneDate();
						}
					}
					if (curTask == null) {
						answer = new WebServerAnswerInJson(new JSON("{\"success\": false}"));
						break;
					}

					// get all tasks of this day
					List<Task> tasksOfThisDay = new ArrayList<>();
					for (Task task : tasks) {
						if (DateUtils.isSameDay(curDate, task.getDoneDate())) {
							tasksOfThisDay.add(task);
						}
					}

					boolean success = false;

					for (int i = 0; i < tasksOfThisDay.size(); i++) {
						if (curTask.getId().equals(tasksOfThisDay.get(i).getId())) {
							if (direction > 0) {
								if (i > 0) {
									Date newDate = tasksOfThisDay.get(i-1).getSetToDoneDateTime();
									newDate = DateUtils.addSeconds(newDate, 1);
									curTask.setSetToDoneDateTime(newDate);
									success = true;
								}
							}
							if (direction < 0) {
								if (i < tasksOfThisDay.size() - 1) {
									Date newDate = tasksOfThisDay.get(i+1).getSetToDoneDateTime();
									newDate = DateUtils.addSeconds(newDate, -1);
									curTask.setSetToDoneDateTime(newDate);
									success = true;
								}
							}
						}
					}

					if (success) {
						taskCtrl.save();
					}
					answer = new WebServerAnswerInJson(new JSON("{\"success\": " + success + "}"));
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
	protected WebServerAnswer answerGet(String location, Map<String, String> arguments) {

		if (location.equals("/exit")) {
			System.exit(0);
		}

		quickDB.access();

		if ("/task".equals(location)) {
			String id = arguments.get("id");
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
					response.set(TaskCtrl.SHOW_AS_SCHEDULED, task.getShowAsScheduled());
					response.set(TaskCtrl.AUTO_CLEAN_TASK, task.getAutoCleanTask());
					response.set("releasedBasedOnId", task.getReleasedBasedOnId());

					return new WebServerAnswerInJson(response);
				}
			}
		}

		if ("/repeatingTask".equals(location)) {
			String id = arguments.get("id");
			if (id != null) {
				Task task = taskCtrl.getTaskById(id);
				if (task != null) {
					JSON response = new JSON(Record.emptyObject());
					String sep;
					response.set("success", true);
					response.set("title", task.getTitle());
					response.set("details", StrUtils.join("\n", task.getDetails()));
					response.set("origin", task.getOrigin());
					response.set("priority", task.getPriority());
					response.set("priorityEscalationAfterDays", task.getPriorityEscalationAfterDays());
					response.set("duration", task.getDurationStr());

					String weekdaysStr = "";
					sep = "";
					List<String> schedWeekdays = task.getScheduledOnDaysOfWeek();
					if (schedWeekdays != null) {
						for (String schedWeekday : schedWeekdays) {
							weekdaysStr += sep;
							weekdaysStr += schedWeekday;
							sep = ", ";
						}
					}
					if ("".equals(weekdaysStr)) {
						response.set("day", task.getScheduledOnDay());
					} else {
						response.set("day", weekdaysStr);
					}

					String monthsStr = "";
					sep = "";
					List<Integer> schedMonths = task.getScheduledInMonths();
					if (schedMonths != null) {
						for (Integer schedMonth : schedMonths) {
							monthsStr += sep;
							monthsStr += DateUtils.monthNumToName(schedMonth);
							sep = ", ";
						}
					}
					response.set("months", monthsStr);

					String yearsStr = "";
					sep = "";
					List<Integer> schedYears = task.getScheduledInYears();
					if (schedYears != null) {
						for (Integer schedYear : schedYears) {
							yearsStr += sep;
							yearsStr += schedYear;
							sep = ", ";
						}
					}
					response.set("years", yearsStr);
					response.set(TaskCtrl.SHOW_AS_SCHEDULED, task.getShowAsScheduled());
					response.set(TaskCtrl.AUTO_CLEAN_TASK, task.getAutoCleanTask());
					response.set(TaskCtrlBase.BIWEEKLY_EVEN, task.getBiweeklyEven());
					response.set(TaskCtrlBase.BIWEEKLY_ODD, task.getBiweeklyOdd());

					return new WebServerAnswerInJson(response);
				}
			}
		}

		return null;
	}

	@Override
	protected File getFileFromLocation(String location, String[] arguments) {

		File sideBarImageFile = SideBarCtrl.getSideBarImageFile(location);
		if (sideBarImageFile != null) {
			return sideBarImageFile;
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


			// whenever any page is loaded, generate instances up until today, but do not save them yet
			// (saving will be done when anything is actually done with the generated instances)
			if (locEquiv.endsWith(".htm")) {
				taskCtrl.generateNewInstances(DateUtils.now());
			}


			// answering a request for general information
			if (locEquiv.equals("index.htm")) {

				String REFRESH_VM_STATS = "refreshVmStats";
				if (arguments.length > 0) {
					if (REFRESH_VM_STATS.equals(arguments[0])) {
						AssSecretary.runStartupTasks();

						String content =
							"<!DOCTYPE html>\n" +
							"<html>\n" +
							"  <head>\n" +
							"    <meta http-equiv=\"refresh\" content=\"1; url='/'\" />\n" +
							"  </head>\n" +
							"  <body style=\"background: #102; color: #88AAFF;\">\n" +
							"    <a href=\"/\" style=\"height: 99pt; padding-top: 15pt; text-align: center; color: #88AAFF;\">\n" +
							"    	If automatic refreshing is down, please click to refresh\n" +
							"    </a>\n" +
							"  </body>\n" +
							"</html>";

						locEquiv = "_" + locEquiv;
						TextFile indexFile = new TextFile(webRoot, locEquiv);
						indexFile.saveContent(content);

						return webRoot.getFile(locEquiv);
					}
				}

				System.out.println("Answering index request...");

				TextFile indexBaseFile = new TextFile(webRoot, locEquiv);
				String indexContent = indexBaseFile.getContent();

				indexContent = StrUtils.replaceAll(indexContent, "[[USERNAME]]", db.getUsername());

				String factsDiv = "";
				String factsHidingStyle = "";
				if (firstIndexCall) {
					firstIndexCall = false;
					Fact fact = factDatabase.getRandomFact();
					if (fact != null) {
						String answerHtml = HTML.escapeHTMLstr(fact.getAnswer());
						answerHtml = StrUtils.replaceAll(answerHtml, "&#10;", "<br>");
						factsDiv = "<div id='factsDiv' " +
							"onclick='document.getElementById(\"factsDiv\").style.display = \"none\"; " +
							"document.getElementById(\"hiddenMainContent\").style.display = \"block\";' " +
							"style='padding-top: 45pt;padding-bottom: 1555pt;'>" +
							answerHtml + "</div>";
						factsHidingStyle = " style='display:none;' ";
					}
				}
				indexContent = StrUtils.replaceAll(indexContent, "[[FACTS]]", factsDiv);
				indexContent = StrUtils.replaceAll(indexContent, "[[FACTS_HIDING_STYLE]]", factsHidingStyle);

				Date now = new Date();
				int hour = DateUtils.getHour(now);
				String sleepStr = "";
				if ((hour >= 3) && (hour < 7)) {
					sleepStr = "Time to sleep! ";
				}
				// Today is Monday the 23rd of April 2027 and it is 08:37 right now. You are on planet Earth.
				String generalInfo = "Today is <span id='curdatetime'>" + DateUtils.getDayOfWeekNameEN(now) + " the " +
					StrUtils.replaceAll(DateUtils.serializeDateTimeLong(now, "<span class='sup'>", "</span>"), ", ", " and it is ") +
					"</span> right now. <span id='cursleepstr'>" + sleepStr + "</span>You are currently on planet Earth.";

				Date awakeUntilAtLeast = quickDB.getPreviousStartLastAccessDate();
				Integer minutesSleptLastNight = taskCtrl.getMinutesSleptLastNight(awakeUntilAtLeast);

				if (minutesSleptLastNight != null) {

					generalInfo += "<br>Last night, you seem to have gone to sleep at " +
						DateUtils.serializeTimeShort(taskCtrl.getLatestTaskDoneTimeAtLoad()) + " and slept for " +
						(minutesSleptLastNight / 60) + " hours, " + (minutesSleptLastNight % 60) +
						" minutes.";

					if (minutesSleptLastNight < 6*60) {
						generalInfo += "<br>After a night with little sleep, in the first half hour " +
							"you usually don't want to get up.<br>Then there will be several " +
							"hours of genuine euphoria.<br>Then in the afternoon, a tiny random thing will " +
							"totally devastate / sadden you - but knowing this about yourself might help " +
							"with enjoying the euphoria, and brushing off the sadness once it happens.<br>" +
							"So have a great day! And please do go to sleep earlier tonight.";
					}
				}

				indexContent = StrUtils.replaceAll(indexContent, "[[GENERAL_INFO]]", generalInfo);


				String tabsHtml = "<div id='tabList'>";
				tabsHtml += "<a href='/inbox.htm'>Inbox</a>";
				tabsHtml += "<a href='/repeating.htm'>Repeating Tasks</a>";
				tabsHtml += "<a href='/tasklog.htm'>Task Log</a>";
				tabsHtml += "<a href='/currenttasklog.htm'>Current Task Log</a>";
				tabsHtml += "<a href='/weekly.htm'>Weekly View</a>";
				tabsHtml += "<a href='/monthly.htm'>Monthly View</a>";
				tabsHtml += "<a href='/stats.htm'>Statistics</a>";
				tabsHtml += "</div>";

				indexContent = StrUtils.replaceAll(indexContent, "[[TABS]]", tabsHtml);

				indexContent = StrUtils.replaceAll(indexContent, "[[AVATAR_DESCRIPTION]]", SideBarCtrl.getAvatarDescription(EMPLOYEE_HUGO));


				StringBuilder taskShortlistHtml = new StringBuilder();

				Date today = DateUtils.now();

				List<Task> shortlistTasks = taskCtrl.getTasksOnShortlist();

				boolean historicalView = false;

				Collections.sort(shortlistTasks, new Comparator<Task>() {
					public int compare(Task a, Task b) {
						return a.getCurrentPriority(today, historicalView) - b.getCurrentPriority(today, historicalView);
					}
				});

				if (shortlistTasks.size() == 0) {
					taskShortlistHtml.append("<div>The task shortlist is empty - well done!</div>");
				} else {
					taskShortlistHtml.append("<div style='padding-bottom:0;'>Here is the task shortlist for today:</div>");
					taskShortlistHtml.append("<div>");
					boolean reducedView = false;
					boolean onShortlist = true;
					boolean standalone = false;
					for (Task shortlistTask : shortlistTasks) {
						shortlistTask.appendHtmlTo(taskShortlistHtml, historicalView, reducedView, onShortlist, today, standalone, "");
					}
					taskShortlistHtml.append("</div>");
				}

				indexContent = StrUtils.replaceAll(indexContent, "[[TASK_SHORTLIST]]", taskShortlistHtml.toString());


				String mariHtml = "";

				if (db.connectToMari()) {

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

							StringBuilder mariHtmlBuilder = new StringBuilder();
							for (GenericTask task : tasks) {
								Task taskTask = new Task(task);
								taskTask.setPriority(200000);
								taskTask.setOrigin(TaskCtrl.FINANCE_ORIGIN);
								boolean reducedView = false;
								boolean onShortlist = false;
								boolean standalone = false;
								String additionalClassName = "-mari";
								taskTask.appendHtmlTo(mariHtmlBuilder, historicalView, reducedView, onShortlist,
									today, standalone, additionalClassName);
							}
							mariHtml += mariHtmlBuilder.toString() + "</div>";
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

				// addLine(vmStatsHtml, "Supervision Earth svs-backend", vmInfo, "svs-backend");
				addLine(vmStatsHtml, "Supervision Earth App Frontend", webInfo, "sveApp");
				addLine(vmStatsHtml, "Supervision Earth App Backend", webInfo, "sveLB");
				addLine(vmStatsHtml, "Supervision Earth Webpage", webInfo, "sveWeb");

				if (vmStatsHtml.length() < 1) {
					vmStatsHtml.insert(0, "I have checked the VM disk statusses and web accessibility - and all is fine.");
				} else {
					vmStatsHtml.insert(0, "I have checked the VM disk statusses and web accessibility - the status is:");
				}
				vmStatsHtml.insert(0, "<div style='position:relative;'>");

				vmStatsHtml.append("<a class='button' href='/?" + REFRESH_VM_STATS + "' " +
					"style='position: absolute; right: 0; top: 0;'>Refresh</a>");
				vmStatsHtml.append("</div>");
				indexContent = StrUtils.replaceAll(indexContent, "[[VM_STATS]]", vmStatsHtml.toString());


				String towaHtml = "";

				if (db.connectToTowa()) {

					String currentAdvice = WebAccessor.get("http://localhost:3016/currentAdvice");

					if ((currentAdvice == null) || "".equals(currentAdvice)) {
						towaHtml += "<div>";
						towaHtml += "<div><span class='warning'>I haven't heard anything from Towa recently,</span> maybe he is super busy...</div>";
						towaHtml += "<div>(He did not react to me sending a web request to him.)</div>";
						towaHtml += "</div>";
					} else {
						towaHtml =
							"<div>" +
							"<div>Towa says:</div>" +
							"<div class='line'>" + currentAdvice + "</div>" +
							"</div>";
					}
				}
				indexContent = StrUtils.replaceAll(indexContent, "[[TOWA]]", towaHtml);


				List<Task> tasks = taskCtrl.getCurrentTaskInstancesAsTasks();

				StringBuilder taskHtml = new StringBuilder();
				boolean reducedView = false;
				boolean onShortlist = false;
				boolean standalone = false;

				sortTasksByDate(tasks);

				for (Task task : tasks) {
					task.appendHtmlTo(taskHtml, historicalView, reducedView, onShortlist, today, standalone, " date-sorted-task");
				}

				sortTasksByPriority(tasks, today, historicalView);

				for (Task task : tasks) {
					task.appendHtmlTo(taskHtml, historicalView, reducedView, onShortlist, today, standalone, " priority-sorted-task");
				}

				indexContent = StrUtils.replaceAll(indexContent, "[[TASKS]]", taskHtml.toString());


				tasks = new ArrayList<>();

				Date tomorrow = DateUtils.daysInTheFuture(1);

				List<Task> currentTasks = taskCtrl.getCurrentTaskInstancesAsTasks();
				for (Task task : currentTasks) {
					if (task.appliesTo(tomorrow)) {
						tasks.add(task);
					}
				}

				List<Task> baseTasksForSchedule = getHugoAndMariTasks();

				for (Task task : baseTasksForSchedule) {
					if (task.isScheduledOn(tomorrow) && task.getShowAsScheduled()) {
						tasks.add(task);
					}
				}

				sortTasksByPriority(tasks, tomorrow, historicalView);

				taskHtml = new StringBuilder();

				Date tomorrowDate = DateUtils.addDays(now, 1);
				String tomorrowDateStr = "tomorrow, <span id='tomorrowdate'>" + DateUtils.getDayOfWeekNameEN(tomorrowDate) + " the " +
					DateUtils.serializeDateLong(tomorrowDate, "<span class='sup'>", "</span>") + "</span>";

				taskHtml.append("<div>");
				taskHtml.append("<div>");
				taskHtml.append("These tasks are scheduled for " + tomorrowDateStr + ":");
				taskHtml.append("</div>");

				standalone = true;

				boolean appendedOne = false;

				if (tasks.size() > 0) {
					for (Task task : tasks) {
						task.appendHtmlTo(taskHtml, historicalView, reducedView, onShortlist, today, standalone, "");
						appendedOne = true;
					}
				}

				taskHtml.append("</div>");

				if (!appendedOne) {
					taskHtml = new StringBuilder();

					taskHtml.append("<div>");
					taskHtml.append("For " + tomorrowDateStr + ", no tasks at all are scheduled.");
					taskHtml.append("</div>");
				}

				indexContent = StrUtils.replaceAll(indexContent, "[[TASKS_TOMORROW]]", taskHtml.toString());


				indexContent = StrUtils.replaceAll(indexContent, "[[MISSION_CONTROL_PREVIEW]]", getMissionControlHtml(false));

				indexContent = StrUtils.replaceAll(indexContent, "[[CURDATE]]", DateUtils.serializeDate(DateUtils.now()));

				indexContent = StrUtils.replaceAll(indexContent, "[[MINI_CALENDAR]]", getMiniCalendarHtml());

				indexContent = StrUtils.replaceAll(indexContent, "[[SIDEBAR]]", SideBarCtrl.getSidebarHtmlStr(EMPLOYEE_HUGO));

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

				indexContent = StrUtils.replaceAll(indexContent, "[[CURDATE]]", DateUtils.serializeDate(DateUtils.now()));

				indexContent = StrUtils.replaceAll(indexContent, "[[MINI_CALENDAR]]", getMiniCalendarHtml());

				indexContent = StrUtils.replaceAll(indexContent, "[[SIDEBAR]]", SideBarCtrl.getSidebarHtmlStr());

				locEquiv = "_" + locEquiv;
				TextFile indexFile = new TextFile(webRoot, locEquiv);
				indexFile.saveContent(indexContent);
			}


			// answering a request for the repeating tasks tab
			if (locEquiv.equals("repeating.htm")) {

				System.out.println("Answering repeating tasks request...");

				TextFile indexBaseFile = new TextFile(webRoot, locEquiv);
				String indexContent = indexBaseFile.getContent();

				List<Task> tasks = getHugoAndMariTasks();

				Collections.sort(tasks, new Comparator<Task>() {
					public int compare(Task a, Task b) {
						return getScheduleSortValue(a) - getScheduleSortValue(b);
					}
				});

				StringBuilder taskHtml = new StringBuilder();
				boolean historicalView = true;
				boolean reducedView = false;
				boolean onShortlist = false;
				boolean standalone = false;
				Date curDate = DateUtils.now();
				if (tasks.size() > 0) {
					for (Task task : tasks) {
						task.appendHtmlTo(taskHtml, historicalView, reducedView, onShortlist, curDate, standalone, "");
					}
				}

				indexContent = StrUtils.replaceAll(indexContent, "[[TASKS]]", taskHtml.toString());

				// mini-calendar is needed in case a task is pre-released which opens the single task entry modal
				indexContent = StrUtils.replaceAll(indexContent, "[[MINI_CALENDAR]]", getMiniCalendarHtml());

				indexContent = StrUtils.replaceAll(indexContent, "[[SIDEBAR]]", SideBarCtrl.getSidebarHtmlStr());

				locEquiv = "_" + locEquiv;
				TextFile indexFile = new TextFile(webRoot, locEquiv);
				indexFile.saveContent(indexContent);
			}


			// answering a request for the task log tab
			if (locEquiv.equals("tasklog.htm") ||
				locEquiv.equals("currenttasklog.htm")) {

				System.out.println("Answering task log request...");

				TextFile indexBaseFile = new TextFile(webRoot, locEquiv);
				String indexContent = indexBaseFile.getContent();

				indexContent = StrUtils.replaceAll(indexContent, "[[CURDATE]]", DateUtils.serializeDate(DateUtils.now()));

				indexContent = StrUtils.replaceAll(indexContent, "[[MINI_CALENDAR]]", getMiniCalendarHtml());

				indexContent = StrUtils.replaceAll(indexContent, "[[SIDEBAR]]", SideBarCtrl.getSidebarHtmlStr());

				List<Task> tasks = getDoneTaskInstancesSortedByDoneDateTime();

				boolean historicalView = true;

				StringBuilder taskHtml = new StringBuilder();
				boolean reducedView = false;
				boolean onShortlist = false;
				boolean standalone = false;
				if (tasks.size() > 0) {
					Date prevDate = tasks.get(0).getDoneDate();
					appendDateToHtml(taskHtml, prevDate);

					boolean useCutoffDate = false;
					Date cutoffDate = null;
					if (locEquiv.equals("currenttasklog.htm")) {
						useCutoffDate = true;
						cutoffDate = DateUtils.addDays(DateUtils.now(), -7);
					}

					for (Task task : tasks) {
						Date curDate = task.getDoneDate();
						if (!DateUtils.isSameDay(curDate, prevDate)) {
							if (useCutoffDate) {
								if (curDate.before(cutoffDate)) {
									break;
								}
							}
							prevDate = curDate;
							taskHtml.append("<div class='separator_top'>&nbsp;</div>");
							taskHtml.append("<div class='separator_bottom'>&nbsp;</div>");
							appendDateToHtml(taskHtml, curDate);
						}
						task.appendHtmlTo(taskHtml, historicalView, reducedView, onShortlist, curDate, standalone, "");
					}
				}

				indexContent = StrUtils.replaceAll(indexContent, "[[TASKS]]", taskHtml.toString());

				locEquiv = "_" + locEquiv;
				TextFile indexFile = new TextFile(webRoot, locEquiv);
				indexFile.saveContent(indexContent);
			}


			// answering a request for the weekly view
			if (locEquiv.equals("weekly.htm")) {

				System.out.println("Answering weekly view request...");

				TextFile indexBaseFile = new TextFile(webRoot, locEquiv);
				String indexContent = indexBaseFile.getContent();

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

				indexContent = StrUtils.replaceAll(indexContent, "[[PREV_DATE_YEAR]]", DateUtils.serializeDate(DateUtils.addDays(today, -365)));
				indexContent = StrUtils.replaceAll(indexContent, "[[PREV_DATE_MONTH]]", DateUtils.serializeDate(DateUtils.addDays(today, -30)));
				indexContent = StrUtils.replaceAll(indexContent, "[[PREV_DATE_WEEK]]", DateUtils.serializeDate(DateUtils.addDays(today, -7)));
				indexContent = StrUtils.replaceAll(indexContent, "[[NEXT_DATE_WEEK]]", DateUtils.serializeDate(DateUtils.addDays(today, 7)));
				indexContent = StrUtils.replaceAll(indexContent, "[[NEXT_DATE_MONTH]]", DateUtils.serializeDate(DateUtils.addDays(today, 30)));
				indexContent = StrUtils.replaceAll(indexContent, "[[NEXT_DATE_YEAR]]", DateUtils.serializeDate(DateUtils.addDays(today, 365)));

				indexContent = StrUtils.replaceAll(indexContent, "[[CURDATE]]", DateUtils.serializeDate(DateUtils.now()));

				indexContent = StrUtils.replaceAll(indexContent, "[[MINI_CALENDAR]]", getMiniCalendarHtml());

				indexContent = StrUtils.replaceAll(indexContent, "[[SIDEBAR]]", SideBarCtrl.getSidebarHtmlStr());

				StringBuilder weeklyHtmlStr = new StringBuilder();

				List<Date> weekDays = DateUtils.getWeekForDate(today);

				List<Task> tasks = taskCtrl.getAllTaskInstancesAsTasks();

				boolean onlyGetDone = false;
				tasks = addExternalTaskInstances(tasks, weekDays.get(0), weekDays.get(6), onlyGetDone);

				List<Task> baseTasksForSchedule = getHugoAndMariTasks();

				for (Date day : weekDays) {
					boolean isToday = DateUtils.isSameDay(actualToday, day);
					weeklyHtmlStr.append("<div class='weekly_day");
					String boldness = "";
					if (isToday) {
						weeklyHtmlStr.append(" today");
						boldness = "font-weight: bold;";
					}
					weeklyHtmlStr.append("'>");
					weeklyHtmlStr.append("<div style='text-align: center; ");
					weeklyHtmlStr.append(boldness);
					weeklyHtmlStr.append("'>");
					weeklyHtmlStr.append(DateUtils.serializeDate(day));
					weeklyHtmlStr.append("</div>");
					weeklyHtmlStr.append("<div style='text-align: center; ");
					weeklyHtmlStr.append(boldness);
					weeklyHtmlStr.append(" padding-bottom: 10pt;'>");
					weeklyHtmlStr.append(DateUtils.getDayOfWeekNameEN(day));
					weeklyHtmlStr.append("</div>");

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
						for (Task task : baseTasksForSchedule) {
							if (task.isScheduledOn(day) && task.getShowAsScheduled()) {
								tasksToday.add(task);
							}
						}
					}

					boolean historicalView = false;

					Collections.sort(tasksToday, new Comparator<Task>() {
						public int compare(Task a, Task b) {
							return a.getCurrentPriority(day, historicalView) - b.getCurrentPriority(day, historicalView);
						}
					});

					boolean reducedView = true;
					boolean onShortlist = false;
					boolean standalone = false;

					for (Task task : tasksToday) {
						task.appendHtmlTo(weeklyHtmlStr, historicalView, reducedView, onShortlist, day, standalone, "");
					}

					weeklyHtmlStr.append("</div>");
				}

				indexContent = StrUtils.replaceAll(indexContent, "[[WEEKLY_PLAN]]", weeklyHtmlStr.toString());

				locEquiv = "_" + locEquiv;
				TextFile indexFile = new TextFile(webRoot, locEquiv);
				indexFile.saveContent(indexContent);
			}


			// answering a request for the monthly view
			if (locEquiv.equals("monthly.htm")) {

				System.out.println("Answering monthly view request...");

				TextFile indexBaseFile = new TextFile(webRoot, locEquiv);
				String indexContent = indexBaseFile.getContent();

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

				int dayNum = 1;
				int month = DateUtils.getMonth(today);
				int year = DateUtils.getYear(today);
				indexContent = StrUtils.replaceAll(indexContent, "[[PREV_DATE_YEAR]]", DateUtils.serializeDate(DateUtils.parseDateNumbers(1, month, year - 1)));
				indexContent = StrUtils.replaceAll(indexContent, "[[PREV_DATE_MONTH]]", DateUtils.serializeDate(DateUtils.parseDateNumbers(1, month - 1, year)));
				indexContent = StrUtils.replaceAll(indexContent, "[[NEXT_DATE_MONTH]]", DateUtils.serializeDate(DateUtils.parseDateNumbers(1, month + 1, year)));
				indexContent = StrUtils.replaceAll(indexContent, "[[NEXT_DATE_YEAR]]", DateUtils.serializeDate(DateUtils.parseDateNumbers(1, month, year + 1)));

				indexContent = StrUtils.replaceAll(indexContent, "[[CURDATE]]", DateUtils.serializeDate(DateUtils.now()));

				indexContent = StrUtils.replaceAll(indexContent, "[[MINI_CALENDAR]]", getMiniCalendarHtml());

				indexContent = StrUtils.replaceAll(indexContent, "[[SIDEBAR]]", SideBarCtrl.getSidebarHtmlStr());

				StringBuilder monthlyHtmlStr = new StringBuilder();

				today = DateUtils.parseDateNumbers(dayNum, month, year);

				List<Task> baseTasksForSchedule = getHugoAndMariTasks();

				while (DateUtils.getMonth(today) == month) {

					monthlyHtmlStr.append("<div>");

					StringBuilder weeklyHtmlStr = new StringBuilder();

					List<Date> weekDays = DateUtils.getWeekForDate(today);

					List<Task> tasks = taskCtrl.getAllTaskInstancesAsTasks();

					boolean onlyGetDone = false;
					tasks = addExternalTaskInstances(tasks, weekDays.get(0), weekDays.get(6), onlyGetDone);

					for (Date day : weekDays) {
						boolean isToday = DateUtils.isSameDay(actualToday, day);
						weeklyHtmlStr.append("<div class='weekly_day");
						String boldness = "";
						if (isToday) {
							weeklyHtmlStr.append(" today");
							boldness = "font-weight: bold;";
						}
						weeklyHtmlStr.append("'");
						// set days to transparent which do not actually belong to the current month
						if (DateUtils.getMonth(day) != month) {
							weeklyHtmlStr.append(" style='opacity: 0.4;'");
						}
						weeklyHtmlStr.append(">");
						weeklyHtmlStr.append("<div style='text-align: center; ");
						weeklyHtmlStr.append(boldness);
						weeklyHtmlStr.append("'>");
						weeklyHtmlStr.append(DateUtils.serializeDate(day));
						weeklyHtmlStr.append("</div>");
						weeklyHtmlStr.append("<div style='text-align: center; ");
						weeklyHtmlStr.append(boldness);
						weeklyHtmlStr.append(" padding-bottom: 10pt;'>");
						weeklyHtmlStr.append(DateUtils.getDayOfWeekNameEN(day));
						weeklyHtmlStr.append("</div>");

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
							for (Task task : baseTasksForSchedule) {
								if (task.isScheduledOn(day) && task.getShowAsScheduled()) {
									tasksToday.add(task);
								}
							}
						}

						boolean historicalView = false;

						Collections.sort(tasksToday, new Comparator<Task>() {
							public int compare(Task a, Task b) {
								return a.getCurrentPriority(day, historicalView) - b.getCurrentPriority(day, historicalView);
							}
						});

						boolean reducedView = true;
						boolean onShortlist = false;
						boolean standalone = false;

						for (Task task : tasksToday) {
							task.appendHtmlTo(weeklyHtmlStr, historicalView, reducedView, onShortlist, day, standalone, "");
						}

						weeklyHtmlStr.append("</div>");
					}

					monthlyHtmlStr.append("</div>");

					monthlyHtmlStr.append(weeklyHtmlStr);

					dayNum += 7;
					today = DateUtils.parseDateNumbers(dayNum, month, year);
				}

				indexContent = StrUtils.replaceAll(indexContent, "[[MONTHLY_PLAN]]", monthlyHtmlStr.toString());

				locEquiv = "_" + locEquiv;
				TextFile indexFile = new TextFile(webRoot, locEquiv);
				indexFile.saveContent(indexContent);
			}


			// answering a request for the stats page
			if (locEquiv.equals("stats.htm")) {

				System.out.println("Answering stats request...");

				TextFile indexBaseFile = new TextFile(webRoot, locEquiv);
				String indexContent = indexBaseFile.getContent();

				indexContent = StrUtils.replaceAll(indexContent, "[[STATISTICS]]", getStatsHtml());

				indexContent = StrUtils.replaceAll(indexContent, "[[SIDEBAR]]", SideBarCtrl.getSidebarHtmlStr());

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

				indexContent = StrUtils.replaceAll(indexContent, "[[SIDEBAR]]", SideBarCtrl.getSidebarHtmlStr());

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

	private String getStatsHtml() {

		StringBuilder html = new StringBuilder();

		Map<String, Object> currentTasks = db.getCurrentTaskInstanceAmounts();
		Map<String, Object> doneTasks = db.getDoneTaskInstanceAmounts();

		Set<String> combinedDateSet = new HashSet<>();
		combinedDateSet.addAll(currentTasks.keySet());
		combinedDateSet.addAll(doneTasks.keySet());

		List<String> dates = new ArrayList<>();
		dates.addAll(combinedDateSet);

		dates = SortUtils.sortAlphabetically(dates);


		if (dates.size() < 1) {
			return "No stats data available!";
		}


		String startSpan = "<span style='display: inline-block; width: 16%; text-align: center;'>";

		html.append("<div class='line'>");
		html.append(startSpan);
		html.append("Date");
		html.append("</span>");
		html.append(startSpan);
		html.append("Tasks Opened This Day");
		html.append("</span>");
		html.append(startSpan);
		html.append("Tasks Done This Day");
		html.append("</span>");
		html.append(startSpan);
		html.append("Total Open Tasks");
		html.append("</span>");
		html.append(startSpan);
		html.append("Total Tasks Done");
		html.append("</span>");
		html.append(startSpan);
		html.append("Total Amount of All Tasks");
		html.append("</span>");
		html.append("</div>");

		int tasksDonePrev = 0;
		int tasksCurPrev = 0;

		boolean firstline = true;

		List<String> lines = new ArrayList<>();

		for (String date : dates) {

			Object tasksDoneObj = doneTasks.get(date);
			Object tasksCurObj = currentTasks.get(date);
			int tasksDone = 0;
			int tasksCur = 0;
			if (tasksDoneObj != null) {
				if (tasksDoneObj instanceof Integer) {
					tasksDone = (Integer) tasksDoneObj;
				} else {
					tasksDone = (int) (long) (Long) tasksDoneObj;
				}
			}
			if (tasksCurObj != null) {
				if (tasksCurObj instanceof Integer) {
					tasksCur = (Integer) tasksCurObj;
				} else {
					tasksCur = (int) (long) (Long) tasksCurObj;
				}
			}
			int tasksTotal = tasksDone + tasksCur;

			StringBuilder line = new StringBuilder();
			line.append("<div class='line'>");
			line.append(startSpan);
			line.append(date);
			line.append("</span>");
			line.append(startSpan);
			if (!firstline) {
				line.append(tasksCur - (tasksCurPrev - (tasksDone - tasksDonePrev)));
			}
			line.append("</span>");
			line.append(startSpan);
			if (!firstline) {
				line.append(tasksDone - tasksDonePrev);
			}
			line.append("</span>");
			line.append(startSpan);
			line.append(tasksCur);
			line.append("</span>");
			line.append(startSpan);
			line.append(tasksDone);
			line.append("</span>");
			line.append(startSpan);
			line.append(tasksTotal);
			line.append("</span>");
			line.append("</div>");
			lines.add(line.toString());

			tasksDonePrev = tasksDone;
			tasksCurPrev = tasksCur;

			firstline = false;
		}

		lines = SortUtils.reverse(lines);

		for (String line : lines) {
			html.append(line);
		}

		return html.toString();
	}

	private String getMissionControlHtml(boolean createLinks) {

		String mcHtml = "";

		VmInfo vmInfo = AssSecretary.getVmInfo();
		WebInfo webInfo = AssSecretary.getWebInfo();

		if ((vmInfo == null) || (webInfo == null)) {
			return "I am afraid I cannot show the mission control; internal information objects are missing entirely...";
		}

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
		mcHtml += machineStart + "App Backend<br>" + webInfo.get("sveLB") +
			// "<br>" + vmInfo.get("svs-backend") +
			machineEnd;
		mcHtml += "<img class='logo' src='projectlogos/supervisionearth/logo.png' />";
		mcHtml += companyEnd;

		return mcHtml;
	}

	private void addLine(StringBuilder vmStatsHtml, String name, McInfo mcInfo, String key) {
		if (mcInfo == null) {
			vmStatsHtml.append("<div class='line'>" + name + ": Weird, the information object is completely missing</div>");
		} else if (mcInfo.isImportant(key)) {
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
			task.setSetToDoneDateTime(DateUtils.now());
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
				json.getString("duration"),
				json.getBoolean(TaskCtrl.SHOW_AS_SCHEDULED),
				json.getBoolean(TaskCtrl.AUTO_CLEAN_TASK)
			);
			setDoneDateBasedOnJson(newTask, json);
			result = newTask;

		} else {
			// edit existing task
			Task task = taskCtrl.getTaskById(editingId);
			if (task != null) {
				task.setTitle(json.getString("title"));
				task.setDetailsStr(json.getString("details"));

				// if we are changing the released date to a new one, which is after the current released date,
				// AND after today, then remove it from the shortlist (in case it is on it)!
				Date newReleasedDate = json.getDate("releaseDate");
				Date prevReleaseDate = task.getReleaseDate();
				if (DateUtils.dateAAfterDateB(newReleasedDate, prevReleaseDate) &&
					DateUtils.dateAAfterDateB(newReleasedDate, DateUtils.now())) {
					taskCtrl.removeTaskFromShortListById(task.getId());
				}
				task.setReleasedDate(newReleasedDate);

				setDoneDateBasedOnJson(task, json);
				task.setOrigin(json.getString("origin"));
				task.setPriority(json.getInteger("priority"));
				task.setPriorityEscalationAfterDays(json.getInteger("priorityEscalationAfterDays"));
				task.setDurationStr(json.getString("duration"));
				task.setShowAsScheduled(json.getBoolean(TaskCtrl.SHOW_AS_SCHEDULED));
				task.setAutoCleanTask(json.getBoolean(TaskCtrl.AUTO_CLEAN_TASK));
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
					json.getString("duration"),
					json.getBoolean(TaskCtrl.SHOW_AS_SCHEDULED),
					json.getBoolean(TaskCtrl.AUTO_CLEAN_TASK)
				);
				setDoneDateBasedOnJson(newTask, json);
			}
		}

		return result;
	}

	private Task addOrEditRepeatingTask(JSON json) throws IOException {

		Task result = null;

		String editingId = json.getString("editingId");

		if (editingId == null) {
			// add new task
			Task newTask = new Task();
			setRepeatingTaskValues(newTask, json);
			taskCtrl.addNewRepeatingTask(newTask);
			taskCtrl.save();
			result = newTask;

		} else {
			// edit existing task
			Task task = taskCtrl.getTaskById(editingId);
			if (task != null) {
				setRepeatingTaskValues(task, json);
				taskCtrl.save();
				result = task;
			} else {
				respond(404);
				return null;
			}
		}

		return result;
	}

	private void setRepeatingTaskValues(Task task, JSON json) {

		task.setTitle(json.getString("title"));
		task.setDetailsStr(json.getString("details"));
		task.setOrigin(json.getString("origin"));
		task.setPriority(json.getInteger("priority"));
		task.setPriorityEscalationAfterDays(json.getInteger("priorityEscalationAfterDays"));
		task.setDurationStr(json.getString("duration"));

		// get integer will just return null if there is a problem
		task.setScheduledOnDay(json.getInteger("day"));

		String[] weekdaysStrs = splitScheduleField(json.getString("day"));
		Set<String> scheduledOnDaysOfWeekSet = new HashSet<>();
		for (String weekStr : weekdaysStrs) {
			weekStr = DateUtils.toDayOfWeekNameEN(weekStr);
			if (weekStr != null) {
				scheduledOnDaysOfWeekSet.add(weekStr);
			}
		}
		List<String> scheduledOnDaysOfWeek = new ArrayList<>(scheduledOnDaysOfWeekSet);
		task.setScheduledOnDaysOfWeek(scheduledOnDaysOfWeek);

		String[] monthsStrs = splitScheduleField(json.getString("months"));
		List<Integer> scheduledInMonths = new ArrayList<>();
		for (String monthStr : monthsStrs) {
			Integer month = DateUtils.monthNameToNum(monthStr);
			if (month != null) {
				scheduledInMonths.add(month);
			}
		}
		task.setScheduledInMonths(scheduledInMonths);

		String[] yearsStrs = splitScheduleField(json.getString("years"));
		List<Integer> scheduledInYears = new ArrayList<>();
		for (String yearStr : yearsStrs) {
			Integer year = StrUtils.strToInt(yearStr);
			if (year != null) {
				scheduledInYears.add(year);
			}
		}
		task.setScheduledInYears(scheduledInYears);

		task.setShowAsScheduled(json.getBoolean(TaskCtrl.SHOW_AS_SCHEDULED));
		task.setAutoCleanTask(json.getBoolean(TaskCtrl.AUTO_CLEAN_TASK));
		task.setBiweeklyEven(json.getBoolean(TaskCtrlBase.BIWEEKLY_EVEN));
		task.setBiweeklyOdd(json.getBoolean(TaskCtrlBase.BIWEEKLY_ODD));
	}

	private String[] splitScheduleField(String weekdaysStr) {
		weekdaysStr = StrUtils.replaceAll(weekdaysStr, ",", " ");
		weekdaysStr = StrUtils.replaceAll(weekdaysStr, ";", " ");
		weekdaysStr = StrUtils.replaceAll(weekdaysStr, "|", " ");
		weekdaysStr = StrUtils.replaceAll(weekdaysStr, "-", " ");
		weekdaysStr = StrUtils.replaceAll(weekdaysStr, ".", " ");
		weekdaysStr = StrUtils.replaceAll(weekdaysStr, "&", " ");
		weekdaysStr = StrUtils.replaceAll(weekdaysStr, "  ", " ");
		weekdaysStr = weekdaysStr.trim();
		return weekdaysStr.split(" ");
	}

	public List<Task> getHugoAndMariTasks() {

		List<GenericTask> baseTasksForSchedule = taskCtrl.getTasks();

		if (db.connectToMari()) {
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
		}

		List<Task> result = new ArrayList<>();
		for (GenericTask task : baseTasksForSchedule) {
			if (task instanceof Task) {
				result.add((Task) task);
			}
		}
		return result;
	}

	/**
	 * To the list of tasks add task instances from Mari, from the assWorkbench and from the legacy LTC
	 */
	private List<Task> addExternalTaskInstances(List<Task> tasks, Date from, Date to, boolean onlyGetDone) {

		String dateStr = "";

		if (from != null) {
			dateStr += "?from=" + DateUtils.serializeDate(from);
		}
		if (to != null) {
			if ("".equals(dateStr)) {
				dateStr += "?";
			} else {
				dateStr += "&";
			}
			dateStr += "to=" + DateUtils.serializeDate(to);
		}

		if (db.connectToMari()) {
			try {
				// get task instances from Mari
				JSON mariTasks = new JSON(WebAccessor.get("http://localhost:3011/taskInstances" + dateStr));

				// copy the task list we are currently looking at
				tasks = new ArrayList<>(tasks);

				// generate tasks locally based on the data we got from Mari
				List<Record> mariRecs = mariTasks.getValues();
				for (Record mariRec : mariRecs) {
					Task localTask = taskCtrl.taskFromMariRecord(mariRec);
					boolean addTask = false;
					if (onlyGetDone) {
						if (localTask.hasBeenDone()) {
							addTask = true;
						}
					} else {
						addTask = true;
					}
					if (addTask) {
						tasks.add(localTask);
					}
				}

			} catch (JsonParseException e) {
				System.out.println("Mari responded with nonsense for a task instances request!");
			}
		}

		if (db.connectToWorkbench()) {
			try {
				// get task instances from assWorkbench
				JSON workbenchTasks = new JSON(WebAccessor.get("http://localhost:3010/taskInstances" + dateStr));

				// copy the task list we are currently looking at
				tasks = new ArrayList<>(tasks);

				// generate tasks locally based on the data we got from Workbench
				List<Record> workbenchRecs = workbenchTasks.getValues();
				for (Record workbenchRec : workbenchRecs) {
					Task localTask = taskCtrl.taskFromWorkbenchRecord(workbenchRec);
					boolean addTask = false;
					if (onlyGetDone) {
						if (localTask.hasBeenDone()) {
							addTask = true;
						}
					} else {
						addTask = true;
					}
					if (addTask) {
						tasks.add(localTask);
					}
				}

			} catch (JsonParseException e) {
				System.out.println("assWorkbench responded with nonsense for a task instances request!");
			}
		}

		if (db.connectToLtc()) {
			// ignore onlyGetDone, as all LTC tasks are done ;)
			List<Task> ltcTasks = LtcDatabase.getTaskInstances(from, to);
			tasks.addAll(ltcTasks);
		}

		return tasks;
	}

	private int getScheduleSortValue(Task task) {

		int result = 0;

		List<String> daysOfWeek = task.getScheduledOnDaysOfWeek();
		if (daysOfWeek != null) {
			for (String day : daysOfWeek) {
				day = DateUtils.toDayOfWeekNameEN(day);
				if (day != null) {
					for (int i = 0; i < DateUtils.DAY_NAMES.length; i++) {
						if (day.equals(DateUtils.DAY_NAMES[i])) {
							result += i;
						}
					}
				}
			}
		}

		Integer schedDay = task.getScheduledOnDay();
		if (schedDay != null) {
			result += schedDay * 100;
		}

		List<Integer> schedMonths = task.getScheduledInMonths();
		if (schedMonths != null) {
			for (Integer month : schedMonths) {
				if (month != null) {
					result += (month+1) * 10000;
				}
			}
		}

		List<Integer> schedYears = task.getScheduledInYears();
		if (schedYears != null) {
			for (Integer year : schedYears) {
				if (year != null) {
					result += year * 1000000;
				}
			}
		}

		return result;
	}

	private void sortTasksByDate(List<Task> tasks) {

		Collections.sort(tasks, new Comparator<Task>() {
			public int compare(Task a, Task b) {
				Date aDate = a.getMainDateForTaskInstance();
				Date bDate = b.getMainDateForTaskInstance();
				if (aDate.before(bDate)) {
					return -1;
				}
				if (bDate.before(aDate)) {
					return 1;
				}
				return 0;
			}
		});
	}

	private void sortTasksByPriority(List<Task> tasks, Date today, boolean historicalView) {

		Collections.sort(tasks, new Comparator<Task>() {
			public int compare(Task a, Task b) {
				return a.getCurrentPriority(today, historicalView) - b.getCurrentPriority(today, historicalView);
			}
		});
	}

	private void appendDateToHtml(StringBuilder taskHtml, Date curDate) {
		taskHtml.append("<div style='text-align:center;'>" + DateUtils.getDayOfWeekNameEN(curDate) + " the " +
			DateUtils.serializeDateLong(curDate, "<span class=\"sup\">", "</span>") + "</div>");
	}

	private List<Task> getDoneTaskInstancesSortedByDoneDateTime() {

		List<Task> tasks = taskCtrl.getDoneTaskInstancesAsTasks();

		boolean onlyGetDone = true;
		tasks = addExternalTaskInstances(tasks, null, null, onlyGetDone);

		boolean historicalView = true;

		Collections.sort(tasks, new Comparator<Task>() {
			public int compare(Task a, Task b) {
				Date aDone = a.getDoneDate();
				Date bDone = b.getDoneDate();
				if (DateUtils.isSameDay(aDone, bDone)) {
					// on tasklog, sort tasks within a day by the datetime at which "done" was set
					Date aSetToDone = a.getSetToDoneDateTime();
					Date bSetToDone = b.getSetToDoneDateTime();
					if ((aSetToDone != null) && (bSetToDone != null)) {
						if (aSetToDone.before(bSetToDone)) {
							return 1;
						}
						if (bSetToDone.before(aSetToDone)) {
							return -1;
						}
					}
					return a.getCurrentPriority(aDone, historicalView) - b.getCurrentPriority(bDone, historicalView);
				}
				if (aDone.before(bDone)) {
					return 1;
				}
				return -1;
			}
		});

		return tasks;
	}

}
