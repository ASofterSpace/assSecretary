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

	// a list of ids of tasks that are on the shortlist
	private final static String TASK_SHORTLIST = "taskShortlist";

	// a list of ids of the tasks on the shortlist
	private List<String> shortlistIds = new ArrayList<>();

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

		return result;
	}

	@Override
	protected Record taskToRecord(GenericTask task) {
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
		}
		return taskRecord;
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
		if (!shortlistIds.contains(id)) {
			shortlistIds.add(id);
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
	}

}
