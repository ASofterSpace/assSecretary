/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.tasks;

import com.asofterspace.assSecretary.Database;
import com.asofterspace.assSecretary.ltc.LtcDatabase;
import com.asofterspace.toolbox.calendar.GenericTask;
import com.asofterspace.toolbox.calendar.TaskCtrlBase;
import com.asofterspace.toolbox.io.Directory;
import com.asofterspace.toolbox.io.IoUtils;
import com.asofterspace.toolbox.io.JSON;
import com.asofterspace.toolbox.io.JsonParseException;
import com.asofterspace.toolbox.io.TextFile;
import com.asofterspace.toolbox.utils.DateUtils;
import com.asofterspace.toolbox.utils.Record;
import com.asofterspace.toolbox.utils.StrUtils;
import com.asofterspace.toolbox.Utils;
import com.asofterspace.toolbox.web.WebAccessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;


/**
 * This is the controller for assSecretary-internal tasks
 */
public class TaskCtrl extends TaskCtrlBase {

	// the origin of the task - can be a company customer we are working for, or "private"
	private final static String ORIGIN = "origin";

	// the priority of the task (independent of when it is scheduled and released), as
	// integer between 0 (the universe is going to end immediately if we don't do this RIGHT NOW!)
	// and 100 (meh, seriously whatever)
	private final static String PRIORITY = "priority";

	// if we do not manage to perform this task on the release date, how will the priority
	// escalate - that is, after how many days will the priority go up?
	// (null for priority never going up)
	private final static String PRIORITY_ESCALATION_AFTER_DAYS = "priorityEscalationAfterDays";

	// the duration in hours
	private final static String DURATION = "duration";

	// an optional UUID (well, everything is optional, but this is explicitly also optional)
	private final static String ID = "id";

	// an optional UUID pointing to the scheduled base task from which this instance was released
	// (null for ad-hoc tasks that we released directly without being scheduled)
	private final static String RELEASED_BASED_ON_ID = "releasedBasedOnId";

	// a list of ids of tasks that are on the shortlist
	private final static String TASK_SHORTLIST = "taskShortlist";

	// a list of ids of tasks that are on the shortlist tomorrow, the day after, etc.
	private final static String FUTURE_TASK_SHORTLIST = "futureTaskShortlist";

	// the origin for tasks coming from Mari
	public final static String FINANCE_ORIGIN = "finances";

	// should we show this one as scheduled in future days?
	public final static String SHOW_AS_SCHEDULED = "showAsScheduled";

	// is this task cleaned up automatically after a week?
	public final static String AUTO_CLEAN_TASK = "autoCleanTask";

	// a list of ids of the tasks on the shortlist
	private List<String> shortlistIds = new ArrayList<>();

	// a list of ids of the tasks that go onto the shortlist tomorrow, or the day after, or the day after that,
	// etc. (so index 0 is tomorrow, index 1 is day after tomorrow, etc.)
	private List<List<String>> futureShortlistIds = new ArrayList<>();

	private Database database;

	private TaskDatabase taskDatabase;

	private Directory webRoot;

	private Directory uploadDir;

	private Long lastUploadRequestTime = null;


	public TaskCtrl(Database database, TaskDatabase taskDatabase, Directory webRoot, Directory uploadDir) {

		this.database = database;

		this.taskDatabase = taskDatabase;

		this.webRoot = webRoot;

		this.uploadDir = uploadDir;

		Record root = taskDatabase.getLoadedRoot();

		loadFromRoot(root);

		// generate instances, but do not save them yet (so that upon seeing this, someone could edit
		// the underlying json and then restart hugo to regenerate, if needed)
		generateNewInstances(DateUtils.now());

		// cleanup the shortlist once on startup, again without saving for now
		cleanupShortlist();

		// remove all autoclean tasks that are older than seven days
		removeAutoCleanTasks(DateUtils.addDays(DateUtils.now(), -7));

		database.setTaskCtrl(this);

		TaskCtrl taskCtrl = this;

		Thread t = new Thread(new Runnable() { public void run() {

			while (true) {

				// this does not need to be exactly synchronized, as the upload calendar just works on
				// a best-effort basis anyway; it will not always be 100% up-to-date
				if (taskCtrl.lastUploadRequestTime != null) {

					long curTime = System.currentTimeMillis();

					// System.out.println("Comparing upload request: " + (curTime - taskCtrl.lastUploadRequestTime));

					// if more than a minute ago an upload was requested, then do it now!
					if (curTime - taskCtrl.lastUploadRequestTime > 60*1000) {

						taskCtrl.lastUploadRequestTime = null;

						taskCtrl.uploadPreviewCalendar();
					}
				}

				try {
					// check every two seconds
					Thread.sleep(2000);

				} catch (InterruptedException e) {
					System.out.println("Interrupted upload thread, returning...");
					return;
				}
			}
		}});

		t.start();
	}

	@Override
	protected void loadFromRoot(Record root) {

		super.loadFromRoot(root);

		if (root == null) {
			return;
		}

		shortlistIds = root.getArrayAsStringList(TASK_SHORTLIST);
		List<Record> futureShortlistRec = root.getArray(FUTURE_TASK_SHORTLIST);
		futureShortlistIds = new ArrayList<>();
		for (Record rec : futureShortlistRec) {
			futureShortlistIds.add(rec.getStringValues());
		}
	}

	@Override
	protected GenericTask createTask(String title, Integer scheduledOnXDayOfMonth, Integer scheduledOnDay,
		List<String> scheduledOnDaysOfWeek, List<Integer> scheduledInMonths, List<Integer> scheduledInYears,
		List<String> details, List<String> onDone,
		Boolean biweeklyEven, Boolean biweeklyOdd) {

		return new Task(title, scheduledOnXDayOfMonth, scheduledOnDay, scheduledOnDaysOfWeek, scheduledInMonths,
			scheduledInYears, details, onDone, biweeklyEven, biweeklyOdd);
	}

	@Override
	protected Task taskFromRecord(Record recordTask) {

		GenericTask task = super.taskFromRecord(recordTask);

		Task result = new Task(task);

		result.setOrigin(recordTask.getString(ORIGIN));
		result.setPriority(recordTask.getInteger(PRIORITY));
		result.setPriorityEscalationAfterDays(recordTask.getInteger(PRIORITY_ESCALATION_AFTER_DAYS));
		result.setDuration(recordTask.getInteger(DURATION));
		result.setId(recordTask.getString(ID));
		result.setReleasedBasedOnId(recordTask.getString(RELEASED_BASED_ON_ID));
		result.setShowAsScheduled(recordTask.getBoolean(SHOW_AS_SCHEDULED));
		result.setAutoCleanTask(recordTask.getBoolean(AUTO_CLEAN_TASK));

		return result;
	}

	public Task taskFromMariRecord(Record recordTask) {

		GenericTask genericTask = null;

		if (recordTask.getInteger(TaskCtrlBase.RELEASED_ON_DAY) == null) {
			genericTask = super.taskFromRecord(recordTask);
		} else {
			genericTask = super.taskInstanceFromRecord(recordTask);
		}

		if (genericTask instanceof Task) {

			Task task = (Task) genericTask;
			task.setOrigin(FINANCE_ORIGIN);
			task.setPriority(300000);
			task.setPriorityEscalationAfterDays(null);
			task.setDuration(null);
			task.setId(null);
			task.setReleasedBasedOnId(null);

			return task;
		}

		return null;
	}

	public Task taskFromWorkbenchRecord(Record recordTask) {

		Task task = new Task();

		task.setTitle(recordTask.getString(TITLE));
		String origin = recordTask.getString(ORIGIN);

		// LABEL :: TO ADD ORIGIN, LOOK HERE (import of workbench entries - just use same origin string! ^^)
		if (origin == null) {
			task.setOrigin("private");
		} else if (origin.startsWith("recoded")) {
			task.setOrigin("recoded");
		} else {
			task.setOrigin(origin);
		}

		Date date = recordTask.getDate(DATE);
		task.setReleasedDate(date);
		task.setDoneDate(date);
		task.setDone(true);
		task.setSetToDoneDateTime(recordTask.getDateTime(SET_TO_DONE_DATE_TIME));
		task.setWorkbenchLink(recordTask.getString("link"));

		return task;
	}

	@Override
	public Record taskToRecord(GenericTask task) {
		Record taskRecord = super.taskToRecord(task);
		if (task instanceof Task) {
			Task ourTask = (Task) task;
			taskRecord.setOrRemove(ORIGIN, ourTask.getOrigin());
			taskRecord.setOrRemove(PRIORITY, ourTask.getPriority());
			taskRecord.setOrRemove(PRIORITY_ESCALATION_AFTER_DAYS, ourTask.getPriorityEscalationAfterDays());
			taskRecord.setOrRemove(DURATION, ourTask.getDuration());
			if (ourTask.hasAnId()) {
				taskRecord.set(ID, ourTask.getId());
			}
			taskRecord.setOrRemove(RELEASED_BASED_ON_ID, ourTask.getReleasedBasedOnId());

			if (ourTask.getShowAsScheduled()) {
				// true is default
				taskRecord.remove(SHOW_AS_SCHEDULED);
			} else {
				taskRecord.set(SHOW_AS_SCHEDULED, false);
			}

			if (ourTask.getAutoCleanTask()) {
				taskRecord.set(AUTO_CLEAN_TASK, true);
			} else {
				// false is default
				taskRecord.remove(AUTO_CLEAN_TASK);
			}
		}
		return taskRecord;
	}

	/**
	 * Releases a task by copying it as an instance and returning the new task instance
	 */
	@Override
	public GenericTask releaseTaskOn(GenericTask genericParentTask, Date day) {
		GenericTask genericTaskInstance = super.releaseTaskOn(genericParentTask, day);
		if (genericTaskInstance instanceof Task) {
			Task taskInstance = (Task) genericTaskInstance;
			if (genericParentTask instanceof Task) {
				Task parentTask = (Task) genericParentTask;
				taskInstance.setReleasedBasedOnId(parentTask.getId());
			}
		}
		return genericTaskInstance;
	}

	@Override
	public List<GenericTask> getUpcomingTaskInstances(int upcomingDays) {

		List<String> shortlistIdCopy = new ArrayList<>(shortlistIds);
		List<List<String>> futureShortlistIdsCopy = new ArrayList<>();
		for (List<String> list : futureShortlistIds) {
			futureShortlistIdsCopy.add(new ArrayList<String>(list));
		}

		List<GenericTask> result = super.getUpcomingTaskInstances(upcomingDays);

		shortlistIds = shortlistIdCopy;
		futureShortlistIds = futureShortlistIdsCopy;

		return result;
	}

	/**
	 * Generates task instances on a particular day and sets the instances relased on that day
	 * onto the shortlist (which includes the instances that have just been released, as well
	 * as the instances that have been released before but for a future date which has now been
	 * reached)- but does not save the taskCtrl as we want it to be called by generateNewInstances
	 * without saving!
	 */
	@Override
	protected void generateNewInstancesOnDay(Date day) {

		super.generateNewInstancesOnDay(day);

		for (GenericTask genericTask : taskInstances) {
			if (genericTask instanceof Task) {
				if (DateUtils.isSameDay(day, genericTask.getReleaseDate())) {
					addTaskToShortListById(((Task) genericTask).getId());
				}
			}
		}

		if (futureShortlistIds.size() > 0) {
			List<String> shortlistIdsTomorrow = futureShortlistIds.get(0);
			for (String id : shortlistIdsTomorrow) {
				addTaskToShortListById(id);
			}
			futureShortlistIds.remove(0);
		}
	}

	private void removeAutoCleanTasks(Date removeBeforeDate) {
		int removed = 0;
		for (int i = taskInstances.size() - 1; i >= 0; i--) {
			GenericTask genericTask = taskInstances.get(i);
			if (genericTask instanceof Task) {
				Task task = (Task) genericTask;
				if (!task.getAutoCleanTask()) {
					continue;
				}
				Date doneDate = task.getDoneDate();
				if (doneDate != null) {
					if (doneDate.before(removeBeforeDate)) {
						taskInstances.remove(i);
						removed++;
					}
				}
			}
		}
		System.out.println("Removed " + removed + " task instances that were set to auto-clean.");
	}

	public List<Task> getAllTaskInstancesAsTasks() {
		List<GenericTask> genericTasks = taskInstances;
		List<Task> result = new ArrayList<>();
		for (GenericTask genericTask : genericTasks) {
			if (genericTask instanceof Task) {
				result.add((Task) genericTask);
			}
		}
		return result;
	}

	public List<Task> getCurrentTaskInstancesAsTasks() {
		boolean ordered = false;
		List<GenericTask> genericTasks = getCurrentTaskInstances(ordered);
		List<Task> result = new ArrayList<>();
		for (GenericTask genericTask : genericTasks) {
			if (genericTask instanceof Task) {
				result.add((Task) genericTask);
			}
		}
		return result;
	}

	public List<Task> getDoneTaskInstancesAsTasks() {
		List<GenericTask> genericTasks = taskInstances;
		List<Task> result = new ArrayList<>();
		for (GenericTask genericTask : genericTasks) {
			if (genericTask instanceof Task) {
				if (genericTask.hasBeenDone()) {
					result.add((Task) genericTask);
				}
			}
		}
		return result;
	}

	public Task getTaskById(String id) {
		// look through task instances...
		List<GenericTask> genericTasks = taskInstances;
		for (GenericTask genericTask : genericTasks) {
			if (genericTask instanceof Task) {
				Task task = (Task) genericTask;
				if (task.hasId(id)) {
					return task;
				}
			}
		}
		// ... and through scheduled base tasks as well!
		genericTasks = tasks;
		for (GenericTask genericTask : genericTasks) {
			if (genericTask instanceof Task) {
				Task task = (Task) genericTask;
				if (task.hasId(id)) {
					return task;
				}
			}
		}
		return null;
	}

	public void deleteTasksByIds(List<String> ids) {
		for (String id : ids) {
			deleteTaskById(id);
		}
	}

	public boolean deleteTaskById(String id) {

		boolean result = false;

		// look through task instances...
		List<GenericTask> genericTasks = taskInstances;
		List<GenericTask> newTaskList = new ArrayList<>();
		for (GenericTask genericTask : genericTasks) {
			if (genericTask instanceof Task) {
				Task task = (Task) genericTask;
				if (task.hasId(id)) {
					result = true;
				} else {
					newTaskList.add(task);
				}
			}
		}
		taskInstances = newTaskList;

		// ... and through scheduled base tasks as well!
		genericTasks = tasks;
		newTaskList = new ArrayList<>();
		for (GenericTask genericTask : genericTasks) {
			if (genericTask instanceof Task) {
				Task task = (Task) genericTask;
				if (task.hasId(id)) {
					result = true;
				} else {
					newTaskList.add(task);
				}
			}
		}
		tasks = newTaskList;

		return result;
	}

	/**
	 * Returns a Task if it worked and an ad hoc task was created, and false otherwise
	 */
	public Task addAdHocTask(String title, String details, Date scheduleDate, String origin, Integer priority,
		Integer priorityEscalationAfterDays, String duration, Boolean showAsScheduled, Boolean autoCleanTask) {

		GenericTask addedTask = super.addAdHocTask(title, details, scheduleDate);

		if (addedTask == null) {
			return null;
		}

		if (addedTask instanceof Task) {
			Task ourTask = (Task) addedTask;
			ourTask.setOrigin(origin);
			ourTask.setPriority(priority);
			ourTask.setPriorityEscalationAfterDays(priorityEscalationAfterDays);
			ourTask.setDurationStr(duration);
			ourTask.setShowAsScheduled(showAsScheduled);
			ourTask.setAutoCleanTask(autoCleanTask);

			// add newly generated task to the shortlist if it is generated for today
			// or for an earlier date...
			// if it is generated for the future, then it will arrive on the shortlist
			// when the release date comes around
			if (DateUtils.isSameDay(DateUtils.now(), ourTask.getReleaseDate()) ||
				ourTask.getReleaseDate().before(DateUtils.now())) {
				shortlistIds.add(ourTask.getId());
			}

			save();

			return ourTask;

		} else {
			System.out.println("Something very weird happened, we encountered a task which is not a Task!");
		}

		return null;
	}

	public boolean setTaskToDone(String id) {

		removeTaskFromShortListById(id);

		List<GenericTask> genericTasks = taskInstances;
		for (GenericTask genericTask : genericTasks) {
			if (genericTask instanceof Task) {
				Task task = (Task) genericTask;
				if (task.hasId(id)) {
					task.setDone(true);
					task.setDoneDate(DateUtils.now());
					task.setDoneLog("");
					task.setSetToDoneDateTime(DateUtils.now());
					save();
					return true;
				}
			}
		}
		return false;
	}

	public boolean setTaskToNotDone(String id) {
		List<GenericTask> genericTasks = taskInstances;
		for (GenericTask genericTask : genericTasks) {
			if (genericTask instanceof Task) {
				Task task = (Task) genericTask;
				if (task.hasId(id)) {
					task.setDone(false);
					task.setDoneDate(null);
					task.setDoneLog(null);
					save();
					return true;
				}
			}
		}
		return false;
	}

	public List<Task> getTasksOnShortlist() {

		List<Task> result = new ArrayList<>();

		for (String id : shortlistIds) {
			Task task = getTaskById(id);
			if (task != null) {
				result.add(task);
			}
		}

		return result;
	}

	private void cleanupShortlist() {

		for (int i = shortlistIds.size() - 1; i >= 0; i--) {
			if (getTaskById(shortlistIds.get(i)) == null) {
				shortlistIds.remove(i);
			}
		}
	}

	public void addTaskToShortListById(String id) {

		if (id == null) {
			return;
		}

		Task task = getTaskById(id);

		if (task == null) {
			return;
		}

		if (task.hasBeenDone()) {
			return;
		}

		if (shortlistIds.contains(id)) {
			return;
		}

		shortlistIds.add(id);
	}

	public void addTaskToShortListTomorrowById(String id) {
		if (id == null) {
			return;
		}

		Task task = getTaskById(id);
		if (task == null) {
			return;
		}

		// by default, we want to put this entry into the shortlist for tomorrow
		int shortlistIndex = 0;

		// however, some entries want to be put into different shortlists, based on what day it is today
		Map<String, List<String>> shortlistAdvanceMap = database.getShortlistAdvances();
		List<String> shortlistAdvanceWeekdays = shortlistAdvanceMap.get(task.getOrigin());
		if (shortlistAdvanceWeekdays != null) {
			// we not have a list of weekday-names, and each of them is acceptable as next day...
			// so we check basically if tomorrow is one of these days, and if yes: shortlistIndex stays at 0
			// if not, we check if the day after tomorrow is one of these days, and if yes: shortlistIndex set to 1
			// if not, we check the day after the day after tomorrow, etc.

			Date today = DateUtils.now();

			List<String> weekdays = new ArrayList<>();
			for (String weekday : shortlistAdvanceWeekdays) {
				weekday = DateUtils.toDayOfWeekNameEN(weekday);
				weekdays.add(weekday);
			}

			int howManyDaysInTheFuture = 0;
			while (true) {
				howManyDaysInTheFuture++;
				Date futureDay = DateUtils.addDays(today, howManyDaysInTheFuture);
				String futureWeekday = DateUtils.getDayOfWeekNameEN(futureDay);
				if (weekdays.contains(futureWeekday)) {
					shortlistIndex = howManyDaysInTheFuture - 1;
					break;
				}
			}
		}

		// fill up with empty shortlist days until we reach the one we want to insert into
		while (futureShortlistIds.size() <= shortlistIndex) {
			futureShortlistIds.add(new ArrayList<String>());
		}

		// get the one we just added, or if it already exists, the pre-existing one
		List<String> shortlistIdsTomorrowOrLater = futureShortlistIds.get(shortlistIndex);

		// insert the new id into the shortlist of that day, unless it already exists
		if (!shortlistIdsTomorrowOrLater.contains(id)) {
			shortlistIdsTomorrowOrLater.add(id);
		}
	}

	public void removeTaskFromShortListById(String id) {
		if (id == null) {
			return;
		}
		while (shortlistIds.contains(id)) {
			shortlistIds.remove(id);
		}
	}

	/**
	 * Get the minutes slept last night based on the later of the task-ctrl-internal latest date,
	 * and the passed in latest date
	 */
	public Integer getMinutesSleptLastNight(Date awakeUntilAtLeast) {

		Date now = DateUtils.now();

		// if this is started at 04:00 or earlier, then it is unlikely that I actually got up this early;
		// instead, most likely I am just still awake and no sleep time should be reported at all
		if (DateUtils.getHour(now) < 4) {
			return null;
		}

		// if this is started at 13:00 or later, then it is unlikely that I actually slept this long;
		// instead, most likely Hugo had already been started earlier and has been restarted for some reason
		if (DateUtils.getHour(now) > 12) {
			return null;
		}

		Date latestTaskDoneTimeAtLoad = getLatestTaskDoneTimeAtLoad();

		if (awakeUntilAtLeast != null) {
			if ((latestTaskDoneTimeAtLoad == null) || awakeUntilAtLeast.after(latestTaskDoneTimeAtLoad)) {
				latestTaskDoneTimeAtLoad = awakeUntilAtLeast;
			}
		}

		// could be null if no tasks at all have been done, or if the computer clock was reset to before
		// any task has ever been done, so check it!
		if (latestTaskDoneTimeAtLoad == null) {
			return null;
		}

		Date yesterday_20_00 = DateUtils.setHour(DateUtils.addDays(now, -1), 20);
		if (latestTaskDoneTimeAtLoad.before(yesterday_20_00)) {
			return null;
		}

		Date today_08_00 = DateUtils.setHour(now, 8);
		if (latestTaskDoneTimeAtLoad.after(today_08_00)) {
			return null;
		}

		return DateUtils.getMinuteDifference(latestTaskDoneTimeAtLoad, now);
	}

	public void save() {
		saveIntoRecord(taskDatabase.getLoadedRoot());
		taskDatabase.save();

		// whenever the taskCtrl is saved, something really changed - so then upload the calendar!
		uploadPreviewCalendarSoon();
	}

	@Override
	public void saveIntoRecord(Record root) {
		super.saveIntoRecord(root);
		root.set(TASK_SHORTLIST, shortlistIds);
		root.set(FUTURE_TASK_SHORTLIST, futureShortlistIds);
	}

	public List<Task> getHugoAndMariTasks() {

		List<GenericTask> baseTasksForSchedule = getTasks();

		if (database.connectToMari()) {
			try {
				// get scheduled tasks from Mari
				JSON mariTasks = new JSON(WebAccessor.get("http://localhost:3011/tasks"));

				// copy the task list we are currently looking at
				baseTasksForSchedule = new ArrayList<>(baseTasksForSchedule);

				// generate tasks locally based on the data we got from Mari
				List<Record> mariRecs = mariTasks.getValues();
				for (Record mariRec : mariRecs) {
					Task localTask = taskFromMariRecord(mariRec);
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
	public List<Task> addExternalTaskInstances(List<Task> tasks, Date from, Date to, boolean onlyGetDone) {

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

		if (database.connectToMari()) {
			try {
				// get task instances from Mari
				JSON mariTasks = new JSON(WebAccessor.get("http://localhost:3011/taskInstances" + dateStr));

				// copy the task list we are currently looking at
				tasks = new ArrayList<>(tasks);

				// generate tasks locally based on the data we got from Mari
				List<Record> mariRecs = mariTasks.getValues();
				for (Record mariRec : mariRecs) {
					Task localTask = taskFromMariRecord(mariRec);
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

		if (database.connectToWorkbench()) {
			try {
				// get task instances from assWorkbench
				JSON workbenchTasks = new JSON(WebAccessor.get("http://localhost:3010/taskInstances" + dateStr));

				// copy the task list we are currently looking at
				tasks = new ArrayList<>(tasks);

				// generate tasks locally based on the data we got from Workbench
				List<Record> workbenchRecs = workbenchTasks.getValues();
				for (Record workbenchRec : workbenchRecs) {
					Task localTask = taskFromWorkbenchRecord(workbenchRec);
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

		if (database.connectToLtc()) {
			// ignore onlyGetDone, as all LTC tasks are done ;)
			List<Task> ltcTasks = LtcDatabase.getTaskInstances(from, to);
			tasks.addAll(ltcTasks);
		}

		return tasks;
	}

	public void uploadPreviewCalendarSoon() {
		System.out.println("Requesting to upload calendar...");

		this.lastUploadRequestTime = System.currentTimeMillis();
	}

	public void uploadPreviewCalendar() {

		System.out.println("Uploading calendar...");


		// create calendar file to be uploaded
		uploadDir.clear();

		TextFile baseFile = new TextFile(webRoot, "upload.htm");
		String baseContent = baseFile.getContent();

		// list out roughly four months - a nice round number: 128 days :)
		Date from = DateUtils.now();
		Date until = DateUtils.addDays(from, 128);

		List<Date> days = DateUtils.listDaysFromTo(from, until);

		List<Task> tasks = getAllTaskInstancesAsTasks();

		boolean onlyGetDone = false;
		tasks = addExternalTaskInstances(tasks, from, until, onlyGetDone);

		List<Task> baseTasksForSchedule = getHugoAndMariTasks();

		StringBuilder taskHtml = new StringBuilder();

		for (Date day : days) {
			taskHtml.append("<div class='separator_top'>&nbsp;</div>");
			taskHtml.append("<div class='separator_bottom'>&nbsp;</div>");
			appendDateToHtml(taskHtml, day);

			List<Task> tasksToday = new ArrayList<>();

			// add scheduled single task instances
			for (Task task : tasks) {
				if (task.appliesTo(day)) {
					tasksToday.add(task);
				}
			}

			// add scheduled base task instances
			for (Task task : baseTasksForSchedule) {
				if (task.isScheduledOn(day) && task.getShowAsScheduled()) {
					tasksToday.add(task);
				}
			}

			boolean historicalView = true;
			boolean reducedView = false;
			boolean onShortlist = false;
			boolean standalone = false;
			boolean showButtons = false;

			Collections.sort(tasksToday, new Comparator<Task>() {
				public int compare(Task a, Task b) {
					return a.getCurrentPriority(day, historicalView) - b.getCurrentPriority(day, historicalView);
				}
			});

			for (Task task : tasksToday) {
				task.appendHtmlTo(taskHtml, historicalView, reducedView, onShortlist, day, standalone, showButtons, "");
			}
		}

		baseContent = StrUtils.replaceAll(baseContent, "[[TASKS]]", taskHtml.toString());

		TextFile calendarFile = new TextFile(uploadDir, "calendar.htm");
		calendarFile.saveContent(baseContent);


		// upload the file
		IoUtils.execute("upload.bat");
	}

	public void appendDateToHtml(StringBuilder taskHtml, Date curDate) {
		taskHtml.append("<div style='text-align:center;'>" + DateUtils.getDayOfWeekNameEN(curDate) + " the " +
			DateUtils.serializeDateLong(curDate, "<span class=\"sup\">", "</span>") + "</div>");
	}

}
