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
import com.asofterspace.assSecretary.locations.LocationDatabase;
import com.asofterspace.assSecretary.locations.LocationUtils;
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
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
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

	private LocationDatabase locationDB;

	private Directory serverDir;

	// only calculate this once at startup, not again and again for performance reasons
	private List<GenericTask> doneDateProblematicTaskInstances;

	private static boolean firstIndexCall = true;

	private final static SideBarEntryForEmployee EMPLOYEE_HUGO = new SideBarEntryForEmployee("Hugo");

	private final static boolean SHOW_BUTTONS = true;


	public ServerRequestHandler(WebServer server, Socket request, Directory webRoot, Directory serverDir,
		Database db, TaskCtrl taskCtrl, FactDatabase factDatabase, QuickDatabase quickDB, LocationDatabase locationDB) {

		super(server, request, webRoot);

		this.db = db;

		this.taskCtrl = taskCtrl;

		this.factDatabase = factDatabase;

		this.quickDB = quickDB;

		this.locationDB = locationDB;

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
					taskCtrl.save();
					answer = new WebServerAnswerInJson(new JSON("{\"success\": true, \"id\": \"" +
						addedOrEditedTask.getId() + "\"}"));
					break;

				case "/addRepeatingTask":

					addedOrEditedTask = addOrEditRepeatingTask(json);
					if (addedOrEditedTask == null) {
						return;
					}
					taskCtrl.save();
					answer = new WebServerAnswerInJson(new JSON("{\"success\": true, \"id\": \"" +
						addedOrEditedTask.getId() + "\"}"));
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
								answer = new WebServerAnswerInJson(new JSON("{\"success\": true, \"id\": \"" + newTask.getId() + "\"}"));
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

					answer = new WebServerAnswerInJson(new JSON("{\"success\": true}"));

					// only continue if a copy of the task should actually be made
					if ((json.getBoolean("copyAfterwards") == null) || (json.getBoolean("copyAfterwards") == false)) {
						taskCtrl.save();
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
						"\"id\": \"" + newTask.getId() + "\", \"newReleaseDate\": \"" +
						DateUtils.serializeDate(newReleaseDate) + "\"}"));

					taskCtrl.save();

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
					response.set("xDayOfMonth", task.getScheduledOnXDayOfMonthStr());

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

		// System.out.println("debug 1: " + location);
		// for (String arg : arguments) {
			// System.out.println(arg);
		// }

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


			// System.out.println("debug 2");

			// whenever any page is loaded, generate instances up until today, but do not save them yet
			// (saving will be done when anything is actually done with the generated instances)
			if (locEquiv.endsWith(".htm")) {
				taskCtrl.generateNewInstances(DateUtils.now());
			}


			// answering a request for general information
			if (locEquiv.equals("index.htm")) {

				// System.out.println("debug 3");

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

				// System.out.println("debug 4");

				System.out.println("Answering index request...");

				TextFile indexBaseFile = new TextFile(webRoot, locEquiv);
				String indexContent = indexBaseFile.getContent();

				indexContent = StrUtils.replaceAll(indexContent, "[[USERNAME]]", db.getUsername());

				// System.out.println("debug 5");

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

				// System.out.println("debug 6");

				Date now = new Date();
				int hour = DateUtils.getHour(now);
				String sleepStr = "";
				if ((hour >= 3) && (hour < 7)) {
					sleepStr = "Time to sleep! ";
				}
				// Today is Monday the 23rd of April 2027 and it is 08:37 right now. You are on planet Earth.
				String generalInfo = "Today is <span id='curdatetime'>" + DateUtils.getDayOfWeekNameEN(now) + " the " +
					StrUtils.replaceAll(DateUtils.serializeDateTimeLong(now, "<span class='sup'>", "</span>"), ", ", " and it is ") +
					"</span> right now. <span id='cursleepstr'>" + sleepStr + "</span>You are currently probably " +
					LocationUtils.serializeToday(locationDB.getFromTo(now)) + ".";

				if (doneDateProblematicTaskInstances == null) {
					doneDateProblematicTaskInstances = taskCtrl.getDoneDateProblematicTaskInstances();
				}
				if (doneDateProblematicTaskInstances.size() > 0) {
					StringBuilder probStr = new StringBuilder();
					probStr.append("<br><span class='error'>My database seems slightly corrupted - there are tasks with problematic done dates or set to done dates (either could be wrong)!</span> They are:<br>");
					for (GenericTask task : doneDateProblematicTaskInstances) {
						probStr.append("<div style='position: relative; padding-right: 50pt;'>");
						probStr.append("Done Date: " + DateUtils.serializeDate(task.getDoneDate()) +
							" Set to Done Date: " + DateUtils.serializeDateTime(task.getSetToDoneDateTime()) +
							" " + HTML.escapeHTMLstr(task.getTitle()));
						if (task instanceof Task) {
							Task taskTask = (Task) task;
							probStr.append("<span style='position:absolute; top:0; right: 0; width: 45pt;' class='button' ");
							probStr.append("' class='button' onclick='secretary.taskEdit(\"");
							probStr.append(taskTask.getId());
							probStr.append("\")'>");
							probStr.append("Edit");
							probStr.append("</span>");
						}
						probStr.append("</div>");
					}
					generalInfo = probStr.toString() + "<br>" + generalInfo;
				}

				// System.out.println("debug 7");

				Date latestTaskDoneTimeAtLoad = taskCtrl.getLatestTaskDoneTimeAtLoad();
				if (latestTaskDoneTimeAtLoad != null) {
					if (latestTaskDoneTimeAtLoad.after(now)) {
						generalInfo = "<br><span class='error'>My database seems slightly corrupted - there are tasks done in the future, e.g. at " +
							DateUtils.serializeDateTime(latestTaskDoneTimeAtLoad) + "!</span><br>" +
							"Please look at the current task log and re-set the done dates to useful ones if you have a moment. Thanks. :)<br><br>" +
							generalInfo;
					}

					Date awakeUntilAtLeast = quickDB.getPreviousStartLastAccessDate();
					Integer minutesSleptLastNight = taskCtrl.getMinutesSleptLastNight(awakeUntilAtLeast);

					if (minutesSleptLastNight != null) {

						generalInfo += "<br>Last night, you seem to have gone to sleep at " +
							DateUtils.serializeTimeShort(latestTaskDoneTimeAtLoad) + " and slept for " +
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
				}

				generalInfo += "<br>If you are confused and have no idea what to do next, then eat, sleep, or just pick any item off this list that you feel like doing." +
								"<br>All that said - here is the ever-shifting plan:";

				indexContent = StrUtils.replaceAll(indexContent, "[[GENERAL_INFO]]", generalInfo);

				// System.out.println("debug 8");


				String tabsHtml = "<div id='tabList'>";
				tabsHtml += "<a href='/inbox.htm'>Inbox</a>";
				tabsHtml += "<a href='/repeating.htm'>Repeating Tasks</a>";
				tabsHtml += "<a href='/tasklog.htm'>Task Log</a>";
				tabsHtml += "<a href='/currenttasklog.htm'>Current Task Log</a>";
				tabsHtml += "<a href='/weekly.htm'>Weekly View</a>";
				tabsHtml += "<a href='/monthly.htm'>Monthly View</a>";
				tabsHtml += "<a href='/monthly.htm?empty=true'>Monthly Location View</a>";
				tabsHtml += "<a href='/stats.htm'>Statistics</a>";
				tabsHtml += "</div>";

				indexContent = StrUtils.replaceAll(indexContent, "[[TABS]]", tabsHtml);

				indexContent = StrUtils.replaceAll(indexContent, "[[AVATAR_DESCRIPTION]]", SideBarCtrl.getAvatarDescription(EMPLOYEE_HUGO));


				StringBuilder taskShortlistHtml = new StringBuilder();

				Date today = DateUtils.now();

				List<Task> shortlistTasks = taskCtrl.getTasksOnShortlist();

				boolean historicalView = false;

				sortTasksByPriority(shortlistTasks, today, historicalView);

				// System.out.println("debug 9");

				Map<String, List<String>> tlas = new HashMap<>();

				taskShortlistHtml.append("<div id='shortlist'>");
				if (shortlistTasks.size() == 0) {
					taskShortlistHtml.append("<div>The task shortlist is empty - well done!</div>");
				} else {
					taskShortlistHtml.append("<div style='position:relative;'>Here is the task shortlist for today:");
					taskShortlistHtml.append("<span class='button' style='position:absolute;right:0;top:-5pt;padding:2pt 8pt;' ");
					taskShortlistHtml.append("onclick='secretary.copyShortlistText();'>");
					taskShortlistHtml.append("Extract Shortlist as Text</span>");
					taskShortlistHtml.append("</div>");
					taskShortlistHtml.append("<div id='shortlist-full-label' style='display: none;'><span class='error'>" +
						"The task shortlist is very full - please reschedule, do or outright delete some tasks!</span></div>");
					boolean reducedView = false;
					boolean onShortlist = true;
					boolean standalone = false;
					for (Task shortlistTask : shortlistTasks) {
						shortlistTask.appendHtmlTo(taskShortlistHtml, historicalView, reducedView, onShortlist, today, standalone, SHOW_BUTTONS, "");
						String tla = shortlistTask.getOriginTLA();
						List<String> tlaList = tlas.get(tla);
						if (tlaList == null) {
							tlaList = new ArrayList<>();
							tlas.put(tla, tlaList);
						}
						tlaList.add(shortlistTask.getId());
					}
				}
				taskShortlistHtml.append("</div>\n");
				taskShortlistHtml.append("<script>\n");
				taskShortlistHtml.append("window.shortlistAmount = " + shortlistTasks.size() + ";\n");
				taskShortlistHtml.append("window.reevaluateShortlistAmount = function() {\n");
				taskShortlistHtml.append("  var shortlistDiv = document.getElementById('shortlist');\n");
				taskShortlistHtml.append("  var shortlistFullLabel = document.getElementById('shortlist-full-label');\n");
				taskShortlistHtml.append("  if (shortlistDiv && shortlistFullLabel) {\n");
				taskShortlistHtml.append("    if (shortlistAmount > 36) {\n");
				taskShortlistHtml.append("      shortlistDiv.className = 'pulsating_alarm';\n");
				taskShortlistHtml.append("      shortlistFullLabel.style.display = 'block';\n");
				taskShortlistHtml.append("    } else {\n");
				taskShortlistHtml.append("      shortlistDiv.className = '';\n");
				taskShortlistHtml.append("      shortlistFullLabel.style.display = 'none';\n");
				taskShortlistHtml.append("    }\n");
				taskShortlistHtml.append("  }\n");
				taskShortlistHtml.append("}\n");
				// evaluate on startup of the page
				taskShortlistHtml.append("window.setTimeout(window.reevaluateShortlistAmount, 100);\n");
				taskShortlistHtml.append("window.setTimeout(window.reevaluateShortlistAmount, 1000);\n");
				taskShortlistHtml.append("window.setTimeout(window.reevaluateShortlistAmount, 2000);\n");
				taskShortlistHtml.append("window.setTimeout(function() {\n");
				taskShortlistHtml.append("  for (const key in window.shortlistTLAs) {\n");
				taskShortlistHtml.append("    for (const id of window.shortlistTLAs[key]) {\n");
				taskShortlistHtml.append("      document.getElementById('select-task-' + id + '-on-shortlist').onclick = function (e) {\n");
				taskShortlistHtml.append("        secretary.taskSelect(id, false, false, e);\n");
				taskShortlistHtml.append("      };\n");
				taskShortlistHtml.append("    }\n");
				taskShortlistHtml.append("  }\n");
				taskShortlistHtml.append("}, 100);\n");
				taskShortlistHtml.append("</script>\n");

				StringBuilder scriptHtml = new StringBuilder();
				scriptHtml.append("window.shortlistTLAs = {\n");
				for (Map.Entry<String, List<String>> entry : tlas.entrySet()) {
					String tla = entry.getKey();
					List<String> tlaList = entry.getValue();
					scriptHtml.append("  " + tla + ": [");
					for (String id : tlaList) {
						scriptHtml.append("'" + id + "',");
					}
					scriptHtml.append("],\n");
				}
				scriptHtml.append("};\n");

				indexContent = StrUtils.replaceAll(indexContent, "[[SCRIPT_CODE]]", scriptHtml.toString());

				indexContent = StrUtils.replaceAll(indexContent, "[[TASK_SHORTLIST]]", taskShortlistHtml.toString());

				// System.out.println("debug 10");


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
									today, standalone, SHOW_BUTTONS, additionalClassName);
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

				// System.out.println("debug 11");

				VmInfo vmInfo = AssSecretary.getVmInfo();
				WebInfo webInfo = AssSecretary.getWebInfo();
				StringBuilder vmStatsHtml = new StringBuilder();

				addLine(vmStatsHtml, "asofterspace.com", webInfo, "assEn");
				addLine(vmStatsHtml, "asofterspace.de", webInfo, "assDe");

				addLine(vmStatsHtml, "fem*streik", webInfo, "femOrg");

				addLine(vmStatsHtml, "AGSG", webInfo, "agsgOrg");

				addLine(vmStatsHtml, "WoodWatchers", webInfo, "wwFrontend");
				addLine(vmStatsHtml, "WoodWatchers", webInfo, "wwBackend");

				addLine(vmStatsHtml, "CielHeraTaskList", webInfo, "heraTasks");

				addLine(vmStatsHtml, "SB WegWeiserTool", webInfo, "sbWW");
				addLine(vmStatsHtml, "QZT InstaPostCreator", webInfo, "qztIPC");

				addLine(vmStatsHtml, "Skyhook DB", vmInfo, "db");
				addLine(vmStatsHtml, "Skyhook DB", webInfo, "skyDb");
				addLine(vmStatsHtml, "Skyhook F1", vmInfo, "f1");
				addLine(vmStatsHtml, "Skyhook F2", vmInfo, "f2");
				addLine(vmStatsHtml, "Skyhook App", webInfo, "skyApp");
				addLine(vmStatsHtml, "Skyhook Webpage", webInfo, "skyWeb");

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


				indexContent = StrUtils.replaceAll(indexContent, "[[LOCAL_INFO]]", AssSecretary.getLocalInfo());


				// System.out.println("debug 12");

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


				// System.out.println("debug 12.5");

				indexContent = StrUtils.replaceAll(indexContent, "[[CURDATE]]", DateUtils.serializeDate(DateUtils.now()));

				indexContent = StrUtils.replaceAll(indexContent, "[[MINI_CALENDAR]]", getMiniCalendarHtml());

				indexContent = StrUtils.replaceAll(indexContent, "[[SIDEBAR]]", SideBarCtrl.getSidebarHtmlStr(EMPLOYEE_HUGO));

				indexContent = StrUtils.replaceAll(indexContent, "[[MISSION_CONTROL_PREVIEW]]", getMissionControlHtml(false));


				// System.out.println("debug 13");

				// no need to show the shortlist tasks a second time ^^
				List<Task> tasks = taskCtrl.getCurrentTaskInstancesAsTasksWithoutShortlistTasks();

				StringBuilder taskHtml = new StringBuilder();
				boolean reducedView = false;
				boolean onShortlist = false;
				boolean standalone = false;

				boolean sortbyDate = false;
				for (String arg : arguments) {
					if ("sortby=date".equals(arg)) {
						sortbyDate = true;
					}
				}
				if (sortbyDate) {
					sortTasksByDate(tasks);
					indexContent = StrUtils.replaceAll(indexContent, "[[SORT_MODE]]", "Date");
				} else {
					sortTasksByPriority(tasks, today, historicalView);
					indexContent = StrUtils.replaceAll(indexContent, "[[SORT_MODE]]", "Priority");
				}

				for (Task task : tasks) {
					task.appendHtmlTo(taskHtml, historicalView, reducedView, onShortlist, today, standalone, SHOW_BUTTONS, "");
				}

				indexContent = StrUtils.replaceAll(indexContent, "[[TASKS]]", taskHtml.toString());

				// System.out.println("debug 15");


				List<Task> currentTasks = tasks;

				List<Task> tomorrowTasks = new ArrayList<>();

				Date tomorrow = DateUtils.daysInTheFuture(1);

				for (Task task : currentTasks) {
					if (task.appliesToDay(tomorrow, today)) {
						tomorrowTasks.add(task);
					}
				}

				// System.out.println("debug 16");

				List<Task> baseTasksForSchedule = taskCtrl.getHugoAndMariTasks();

				Calendar tomorrowCal = Calendar.getInstance();
				tomorrowCal.setTime(tomorrow);

				for (Task task : baseTasksForSchedule) {
					if (task.getShowAsScheduled() && task.isScheduledOn(tomorrowCal)) {
						tomorrowTasks.add(task);
					}
				}

				sortTasksByPriority(tomorrowTasks, tomorrow, historicalView);

				// System.out.println("debug 17");

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

				for (Task task : tomorrowTasks) {
					task.appendHtmlTo(taskHtml, historicalView, reducedView, onShortlist, today, standalone, SHOW_BUTTONS, "");
					appendedOne = true;
				}

				taskHtml.append("</div>");

				if (!appendedOne) {
					taskHtml = new StringBuilder();

					taskHtml.append("<div>");
					taskHtml.append("For " + tomorrowDateStr + ", no tasks at all are scheduled.");
					taskHtml.append("</div>");
				}

				indexContent = StrUtils.replaceAll(indexContent, "[[TASKS_TOMORROW]]", taskHtml.toString());

				// System.out.println("debug 18");


				locEquiv = "_" + locEquiv;
				TextFile indexFile = new TextFile(webRoot, locEquiv);
				indexFile.saveContent(indexContent);

				// System.out.println("debug 18.5");
			}


			// answering a request for the inbox tab
			if (locEquiv.equals("inbox.htm")) {

				System.out.println("Answering inbox request...");

				TextFile indexBaseFile = new TextFile(webRoot, locEquiv);
				String indexContent = indexBaseFile.getContent();

				indexContent = StrUtils.replaceAll(indexContent, "[[INBOX_CONTENT]]", db.getInboxContent());

				indexContent = StrUtils.replaceAll(indexContent, "[[CURDATE]]", DateUtils.serializeDate(DateUtils.now()));

				indexContent = StrUtils.replaceAll(indexContent, "[[MINI_CALENDAR]]", getMiniCalendarHtml());

				indexContent = StrUtils.replaceAll(indexContent, "[[SIDEBAR]]", getSidebarHtmlWithHugo());

				locEquiv = "_" + locEquiv;
				TextFile indexFile = new TextFile(webRoot, locEquiv);
				indexFile.saveContent(indexContent);
			}


			// answering a request for the repeating tasks tab
			if (locEquiv.equals("repeating.htm")) {

				System.out.println("Answering repeating tasks request...");

				TextFile indexBaseFile = new TextFile(webRoot, locEquiv);
				String indexContent = indexBaseFile.getContent();

				List<Task> tasks = taskCtrl.getHugoAndMariTasks();

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
						task.appendHtmlTo(taskHtml, historicalView, reducedView, onShortlist, curDate, standalone, SHOW_BUTTONS, "");
					}
				}

				indexContent = StrUtils.replaceAll(indexContent, "[[TASKS]]", taskHtml.toString());

				// mini-calendar is needed in case a task is pre-released which opens the single task entry modal
				indexContent = StrUtils.replaceAll(indexContent, "[[MINI_CALENDAR]]", getMiniCalendarHtml());

				indexContent = StrUtils.replaceAll(indexContent, "[[SIDEBAR]]", getSidebarHtmlWithHugo());

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

				indexContent = StrUtils.replaceAll(indexContent, "[[SIDEBAR]]", getSidebarHtmlWithHugo());

				List<Task> tasks = getDoneTaskInstancesSortedByDoneDateTime();

				boolean historicalView = true;

				StringBuilder taskHtml = new StringBuilder();
				boolean reducedView = false;
				boolean onShortlist = false;
				boolean standalone = false;
				if (tasks.size() > 0) {
					Date prevDate = tasks.get(0).getDoneDate();
					taskCtrl.appendDateToHtml(taskHtml, prevDate);

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
							taskCtrl.appendDateToHtml(taskHtml, curDate);
						}
						task.appendHtmlTo(taskHtml, historicalView, reducedView, onShortlist, curDate, standalone, SHOW_BUTTONS, "");
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
							if (today == null) {
								today = actualToday;
							}
						}
					}
				}

				indexContent = StrUtils.replaceAll(indexContent, "[[PREV_DATE_YEAR]]", DateUtils.serializeDate(DateUtils.addDays(today, -365)));
				indexContent = StrUtils.replaceAll(indexContent, "[[PREV_DATE_MONTH]]", DateUtils.serializeDate(DateUtils.addDays(today, -30)));
				indexContent = StrUtils.replaceAll(indexContent, "[[PREV_DATE_WEEK]]", DateUtils.serializeDate(DateUtils.addDays(today, -7)));
				indexContent = StrUtils.replaceAll(indexContent, "[[NEXT_DATE_WEEK]]", DateUtils.serializeDate(DateUtils.addDays(today, 7)));
				indexContent = StrUtils.replaceAll(indexContent, "[[NEXT_DATE_MONTH]]", DateUtils.serializeDate(DateUtils.addDays(today, 30)));
				indexContent = StrUtils.replaceAll(indexContent, "[[NEXT_DATE_YEAR]]", DateUtils.serializeDate(DateUtils.addDays(today, 365)));

				indexContent = StrUtils.replaceAll(indexContent, "[[CURDATE]]", DateUtils.serializeDate(actualToday));

				indexContent = StrUtils.replaceAll(indexContent, "[[MINI_CALENDAR]]", getMiniCalendarHtml());

				indexContent = StrUtils.replaceAll(indexContent, "[[SIDEBAR]]", getSidebarHtmlWithHugo());

				StringBuilder weeklyHtmlStr = new StringBuilder();

				List<Date> weekDays = DateUtils.getWeekForDate(today);

				List<Task> tasks = taskCtrl.getAllTaskInstancesAsTasks();

				boolean onlyGetDone = false;
				tasks = taskCtrl.addExternalTaskInstances(tasks, weekDays.get(0), weekDays.get(6), onlyGetDone);

				List<Task> baseTasksForSchedule = taskCtrl.getHugoAndMariTasks();

				for (Date day : weekDays) {
					boolean isToday = DateUtils.isSameDay(actualToday, day);
					weeklyHtmlStr.append("<div class='weekly_day");
					if (isToday) {
						weeklyHtmlStr.append(" today");
					}
					weeklyHtmlStr.append("'>");
					weeklyHtmlStr.append("<div style='text-align: center;' class='h'>");
					weeklyHtmlStr.append(DateUtils.serializeDate(day));
					weeklyHtmlStr.append("</div>");
					weeklyHtmlStr.append("<div style='text-align: center; ");
					weeklyHtmlStr.append("padding-bottom: 10pt;' class='h'>");
					weeklyHtmlStr.append(DateUtils.getDayOfWeekNameEN(day));
					weeklyHtmlStr.append("</div>");

					// location part
					appendLocationForDayToHtml(day, weeklyHtmlStr, locationDB);

					List<Task> tasksToday = new ArrayList<>();

					// check for all task instances if they apply today
					for (Task task : tasks) {
						if (task.appliesToDay(day, actualToday)) {
							tasksToday.add(task);
						}
					}

					// for days in the future, also add ghost tasks (non-instances) - but no in the past,
					// as there we would expect real instances to have been created instead!
					if (day.after(actualToday)) {
						Calendar dayCal = Calendar.getInstance();
						dayCal.setTime(day);

						for (Task task : baseTasksForSchedule) {
							if (task.getShowAsScheduled() && task.isScheduledOn(dayCal)) {
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
						task.appendHtmlTo(weeklyHtmlStr, historicalView, reducedView, onShortlist, day, standalone, SHOW_BUTTONS, "");
					}

					weeklyHtmlStr.append("</div>");
				}

				appendLocationScriptToHtml(weeklyHtmlStr);

				indexContent = StrUtils.replaceAll(indexContent, "[[WEEKLY_PLAN]]", weeklyHtmlStr.toString());

				indexContent = StrUtils.replaceAll(indexContent, "[[CALENDAR_WEEK]]", ""+DateUtils.getWeek(today));

				locEquiv = "_" + locEquiv;
				TextFile indexFile = new TextFile(webRoot, locEquiv);
				indexFile.saveContent(indexContent);
			}


			// answering a request for the monthly view
			if (locEquiv.equals("monthly.htm")) {

				System.out.println("Answering monthly view request...");

				TextFile indexBaseFile = new TextFile(webRoot, locEquiv);
				String indexContent = indexBaseFile.getContent();

				boolean emptyView = false;

				// the actual today that it really is... today
				Date actualToday = DateUtils.now();
				// the "today" for which the chart is generated, by default the actual today
				Date today = actualToday;
				for (String arg : arguments) {
					if (arg.contains("=")) {
						String key = arg.substring(0, arg.indexOf("="));
						String value = arg.substring(arg.indexOf("=") + 1);
						if ("date".equals(key)) {
							today = DateUtils.parseDate(value);
						}
						if ("empty".equals(key)) {
							emptyView = "true".equals(value.toLowerCase());
						}
					}
					if ("empty".equals(arg)) {
						emptyView = true;
					}
				}

				int month = DateUtils.getMonth(today);
				int year = DateUtils.getYear(today);

				List<Task> tasks = new ArrayList<>();
				List<Task> baseTasksForSchedule = new ArrayList<>();

				if (!emptyView) {
					tasks = taskCtrl.getAllTaskInstancesAsTasks();
					baseTasksForSchedule = taskCtrl.getHugoAndMariTasks();

					// add external tasks for the first month (as other months are not included for non-empty / task view),
					// but have 9 days before and 9 days after so that there is enough buffer for days to be shown before
					// and after the core month
					boolean onlyGetDone = false;
					tasks = taskCtrl.addExternalTaskInstances(tasks, DateUtils.parseDateNumbers(18, month - 1, year), DateUtils.parseDateNumbers(9, month + 1, year), onlyGetDone);
				}

				indexContent = StrUtils.replaceAll(indexContent, "[[PREV_DATE_YEAR]]", DateUtils.serializeDate(DateUtils.parseDateNumbers(1, month, year - 1)));
				indexContent = StrUtils.replaceAll(indexContent, "[[PREV_DATE_MONTH]]", DateUtils.serializeDate(DateUtils.parseDateNumbers(1, month - 1, year)));
				indexContent = StrUtils.replaceAll(indexContent, "[[NEXT_DATE_MONTH]]", DateUtils.serializeDate(DateUtils.parseDateNumbers(1, month + 1, year)));
				indexContent = StrUtils.replaceAll(indexContent, "[[NEXT_DATE_YEAR]]", DateUtils.serializeDate(DateUtils.parseDateNumbers(1, month, year + 1)));

				indexContent = StrUtils.replaceAll(indexContent, "[[CURDATE]]", DateUtils.serializeDate(actualToday));

				indexContent = StrUtils.replaceAll(indexContent, "[[MINI_CALENDAR]]", getMiniCalendarHtml());

				indexContent = StrUtils.replaceAll(indexContent, "[[SIDEBAR]]", getSidebarHtmlWithHugo());

				indexContent = StrUtils.replaceAll(indexContent, "[[EMPTY_VIEW]]", ""+emptyView);

				StringBuilder monthlyHtmlStr = new StringBuilder();

				appendMonthToHtml(actualToday, month, year, locationDB, tasks, baseTasksForSchedule, emptyView, monthlyHtmlStr);

				// on empty / location-only view, show four months (while the regular monthly page only
				// shows one)
				if (emptyView) {
					appendMonthToHtml(actualToday, month+1, year, locationDB, tasks, baseTasksForSchedule, emptyView, monthlyHtmlStr);
					appendMonthToHtml(actualToday, month+2, year, locationDB, tasks, baseTasksForSchedule, emptyView, monthlyHtmlStr);
					appendMonthToHtml(actualToday, month+3, year, locationDB, tasks, baseTasksForSchedule, emptyView, monthlyHtmlStr);
				}

				ServerRequestHandler.appendLocationScriptToHtml(monthlyHtmlStr);

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

				indexContent = StrUtils.replaceAll(indexContent, "[[SIDEBAR]]", getSidebarHtmlWithHugo());

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

				indexContent = StrUtils.replaceAll(indexContent, "[[SIDEBAR]]", getSidebarHtmlWithHugo());

				locEquiv = "_" + locEquiv;
				TextFile indexFile = new TextFile(webRoot, locEquiv);
				indexFile.saveContent(indexContent);
			}


			// System.out.println("debug 19");

			// actually get the file
			return webRoot.getFile(locEquiv);
		}

		// System.out.println("debug null");

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
		String machineSeparator = "<div class='machine_separator'>*</div>";

		mcHtml += companyStart;
		mcHtml += machineStart + "ASS Chaotic Joy MOYA-XV<br>" + AssSecretary.getLocalInfoShort() + machineEnd;
		mcHtml += machineStart + "asofterspace<br>.com: " + webInfo.get("assEn") + "<br>.de: " + webInfo.get("assDe") + machineEnd;
		mcHtml += machineStart + "Hera Tasks<br>" + webInfo.get("heraTasks") + machineEnd;
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
		mcHtml += machineStart + "feministischerstreik.org<br>" + webInfo.get("femOrg") + machineEnd;
		mcHtml += machineStart + "afghangirlssuccessgate.org<br>" + webInfo.get("agsgOrg") + machineEnd;
		mcHtml += machineStart + "WoodWatchers<br>front: " + webInfo.get("wwFrontend") + "<br>" +
			"back: " + webInfo.get("wwBackend") + machineEnd;
		mcHtml += machineStart + "SB WegWeiserTool<br>" + webInfo.get("sbWW") + machineEnd;
		mcHtml += machineStart + "QZT InstaPostCreator<br>" + webInfo.get("qztIPC") + machineEnd;

		mcHtml += "<img class='logo' src='projectlogos/da/logo.png' />";
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
		boolean beforeToday = true;
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
						html += " background: rgba(150, 200, 250, 0.3);";
						beforeToday = false;
					}
					if (beforeToday) {
						html += " opacity: 0.55;";
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
			result = newTask;

		} else {
			// edit existing task
			Task task = taskCtrl.getTaskById(editingId);
			if (task != null) {
				setRepeatingTaskValues(task, json);
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
		task.setScheduledOnXDayOfMonth(json.getInteger("xDayOfMonth"));

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
				int aCurPrio = a.getCurrentPriority(today, historicalView);
				int bCurPrio = b.getCurrentPriority(today, historicalView);
				if (aCurPrio != bCurPrio) {
					return aCurPrio - bCurPrio;
				}
				return a.getTitle().toLowerCase().compareTo(b.getTitle().toLowerCase());
			}
		});
	}

	private List<Task> getDoneTaskInstancesSortedByDoneDateTime() {

		List<Task> tasks = taskCtrl.getDoneTaskInstancesAsTasks();

		boolean onlyGetDone = true;
		tasks = taskCtrl.addExternalTaskInstances(tasks, null, null, onlyGetDone);

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

	private String getSidebarHtmlWithHugo() {
		String result = SideBarCtrl.getSidebarHtmlStr();
		// do not actually open links to Hugo in a new tab while looking at Hugo (e.g. when in the inbox)
		result = StrUtils.replaceAll(result, "href=\"http://localhost:3012/\" target=\"_blank\"",
			"href=\"http://localhost:3012/\"");
		return result;
	}

	public static void appendLocationForDayToHtml(Date day, StringBuilder html, LocationDatabase locationDB) {
		html.append("<div style='padding-bottom: 10pt;'>");
		html.append("<div class='locationholder'>");
		html.append("<span>");
		html.append(LocationUtils.serializeDay(locationDB.getFromTo(day)));
		html.append("</span>");
		html.append("</div>");
		html.append("</div>");
	}

	public static void appendLocationScriptToHtml(StringBuilder html) {
		html.append("\n<script>\n");
		html.append("var locationHolderHeightFun = function() {\n");
		html.append("  var locHolders = document.getElementsByClassName('locationholder');\n");
		html.append("  var maxHeight = 42;\n");
		html.append("  for (var i = 0; i < locHolders.length; i++) {\n");
		html.append("    if (maxHeight < locHolders[i].clientHeight) {\n");
		html.append("      maxHeight = locHolders[i].clientHeight;\n");
		html.append("    }\n");
		html.append("  }\n");
		html.append("  for (var i = 0; i < locHolders.length; i++) {\n");
		html.append("    locHolders[i].style.height = maxHeight + 'px';\n");
		html.append("  }\n");
		html.append("};\n");
		html.append("window.setTimeout(locationHolderHeightFun, 100);\n");
		html.append("window.setTimeout(locationHolderHeightFun, 250);\n");
		html.append("window.setTimeout(locationHolderHeightFun, 1000);\n");
		html.append("window.setTimeout(locationHolderHeightFun, 2000);\n");
		html.append("</script>\n");
	}

	public static void appendMonthToHtml(Date actualToday, int month, int year, LocationDatabase locationDB,
		List<Task> tasks, List<Task> baseTasksForSchedule, boolean emptyView, StringBuilder monthlyHtmlStr) {

		int dayNum = 1;

		while (month > 12) {
			year++;
			month -= 12;
		}

		Date today = DateUtils.parseDateNumbers(dayNum, month, year);

		monthlyHtmlStr.append("<div style='font-size:250%;font-weight:bold;text-align:center;'>");
		monthlyHtmlStr.append(DateUtils.monthNumToName(month - 1) + " " + year);
		monthlyHtmlStr.append("</div>");

		List<Date> weekDays = DateUtils.getWeekForDate(today);

		boolean stayInLoop = true;

		while (stayInLoop) {

			StringBuilder weeklyHtmlStr = new StringBuilder();

			if (emptyView) {
				weeklyHtmlStr.append("<div style='padding: 0 0 0 12pt; position: relative;'>");
				weeklyHtmlStr.append("<div class='kw_on_month_view'>KW: " + DateUtils.getWeek(today) + "</div>");
			}

			for (Date day : weekDays) {
				String serializedDate = DateUtils.serializeDate(day);
				weeklyHtmlStr.append("<div id='day-" + DateUtils.getMonth(day) + "-" + serializedDate + "' class='weekly_day");
				if (emptyView) {
					weeklyHtmlStr.append(" full_border");
				}
				String styleStr = "";
				boolean isToday = DateUtils.isSameDay(actualToday, day);
				if (isToday) {
					weeklyHtmlStr.append(" today");
				} else {
					// set days to transparent which are before today
					if (emptyView && day.before(actualToday)) {
						styleStr += "opacity: 0.4;";
					}
				}
				weeklyHtmlStr.append("'");
				// set days to invisible which do not actually belong to the current month
				if (DateUtils.getMonth(day) != month) {
					if (emptyView) {
						styleStr += "visibility: hidden;";
					} else {
						styleStr += "opacity: 0.4;";
					}
				}
				if (!"".equals(styleStr)) {
					weeklyHtmlStr.append(" style='" + styleStr + "'");
				}
				weeklyHtmlStr.append(">");
				weeklyHtmlStr.append("<div style='text-align: center;' class='h'>");
				weeklyHtmlStr.append(serializedDate);
				weeklyHtmlStr.append("</div>");
				weeklyHtmlStr.append("<div style='text-align: center; ");
				weeklyHtmlStr.append("padding-bottom: 10pt;' class='h'>");
				weeklyHtmlStr.append(DateUtils.getDayOfWeekNameEN(day));
				weeklyHtmlStr.append("</div>");

				// location part
				appendLocationForDayToHtml(day, weeklyHtmlStr, locationDB);

				if (!emptyView) {
					List<Task> tasksToday = new ArrayList<>();

					// check for all task instances if they apply today
					for (Task task : tasks) {
						if (task.appliesToDay(day, actualToday)) {
							tasksToday.add(task);
						}
					}

					// for days in the future, also add ghost tasks (non-instances) - but no in the past,
					// as there we would expect real instances to have been created instead!
					if (day.after(actualToday)) {
						Calendar dayCal = Calendar.getInstance();
						dayCal.setTime(day);

						for (Task task : baseTasksForSchedule) {
							if (task.getShowAsScheduled() && task.isScheduledOn(dayCal)) {
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
						task.appendHtmlTo(weeklyHtmlStr, historicalView, reducedView, onShortlist, day, standalone, SHOW_BUTTONS, "");
					}
				}

				weeklyHtmlStr.append("</div>");
			}

			if (!emptyView) {
				weeklyHtmlStr.append("<div>");
			}
			weeklyHtmlStr.append("</div>");

			monthlyHtmlStr.append(weeklyHtmlStr);

			today = DateUtils.addDays(today, 7);

			weekDays = DateUtils.getWeekForDate(today);

			stayInLoop = DateUtils.getMonth(weekDays.get(0)) == month;
		}

		monthlyHtmlStr.append("<div style='padding-bottom: 25pt;'>");
		monthlyHtmlStr.append("</div>");
	}

}
