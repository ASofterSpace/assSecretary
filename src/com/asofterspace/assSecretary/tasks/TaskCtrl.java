/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.tasks;

import com.asofterspace.toolbox.calendar.GenericTask;
import com.asofterspace.toolbox.calendar.TaskCtrlBase;
import com.asofterspace.toolbox.utils.DateUtils;
import com.asofterspace.toolbox.utils.Record;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


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

	// a list of ids of tasks that are on the shortlist tomorrow
	private final static String TASK_SHORTLIST_TOMORROW = "taskShortlistTomorrow";

	// the origin for tasks coming from Mari
	public final static String FINANCE_ORIGIN = "finances";

	// a list of ids of the tasks on the shortlist
	private List<String> shortlistIds = new ArrayList<>();

	// a list of ids of the tasks that go onto the shortlist tomorrow
	private List<String> shortlistIdsTomorrow = new ArrayList<>();

	private TaskDatabase database;


	public TaskCtrl(TaskDatabase database) {

		this.database = database;

		Record root = database.getLoadedRoot();

		loadFromRoot(root);

		// generate instances, but do not save them yet (so that upon seeing this, someone could edit
		// the underlying json and then restart hugo to regenerate, if needed)
		generateNewInstances(DateUtils.now());
	}

	@Override
	protected void loadFromRoot(Record root) {

		super.loadFromRoot(root);

		if (root == null) {
			return;
		}

		shortlistIds = root.getArrayAsStringList(TASK_SHORTLIST);
		shortlistIdsTomorrow = root.getArrayAsStringList(TASK_SHORTLIST_TOMORROW);
	}

	@Override
	protected GenericTask createTask(String title, Integer scheduledOnDay, List<String> scheduledOnDaysOfWeek,
		List<Integer> scheduledInMonths, List<Integer> scheduledInYears, List<String> details, List<String> onDone) {

		return new Task(title, scheduledOnDay, scheduledOnDaysOfWeek, scheduledInMonths,
			scheduledInYears, details, onDone);
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

		task.setTitle(recordTask.getString("title"));
		String origin = recordTask.getString("origin");
		if (origin == null) {
			task.setOrigin("private");
		} else if (origin.startsWith("recoded")) {
			task.setOrigin("recoded");
		} else {
			task.setOrigin(origin);
		}

		Date date = recordTask.getDate("date");
		task.setReleasedDate(date);
		task.setDoneDate(date);
		task.setDone(true);
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
		List<String> shortlistIdTomorrowCopy = new ArrayList<>(shortlistIdsTomorrow);

		List<GenericTask> result = super.getUpcomingTaskInstances(upcomingDays);

		shortlistIds = shortlistIdCopy;
		shortlistIdsTomorrow = shortlistIdTomorrowCopy;

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

		for (String id : shortlistIdsTomorrow) {
			addTaskToShortListById(id);
		}
		shortlistIdsTomorrow = new ArrayList<>();
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
		Integer priorityEscalationAfterDays, String duration) {

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

		// look through task instances...
		List<GenericTask> genericTasks = taskInstances;
		for (GenericTask genericTask : genericTasks) {
			if (genericTask instanceof Task) {
				Task task = (Task) genericTask;
				for (String id : shortlistIds) {
					if (task.hasId(id)) {
						result.add(task);
					}
				}
			}
		}

		// ... and through scheduled base tasks as well!
		genericTasks = tasks;
		for (GenericTask genericTask : genericTasks) {
			if (genericTask instanceof Task) {
				Task task = (Task) genericTask;
				for (String id : shortlistIds) {
					if (task.hasId(id)) {
						result.add(task);
					}
				}
			}
		}

		return result;
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
		if (!shortlistIdsTomorrow.contains(id)) {
			shortlistIdsTomorrow.add(id);
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

	public void save() {
		saveIntoRecord(database.getLoadedRoot());
		database.save();
	}

	@Override
	public void saveIntoRecord(Record root) {
		super.saveIntoRecord(root);
		root.set(TASK_SHORTLIST, shortlistIds);
		root.set(TASK_SHORTLIST_TOMORROW, shortlistIdsTomorrow);
	}

}
