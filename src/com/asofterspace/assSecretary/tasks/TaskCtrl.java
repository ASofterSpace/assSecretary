/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.tasks;

import com.asofterspace.toolbox.calendar.GenericTask;
import com.asofterspace.toolbox.calendar.TaskCtrlBase;
import com.asofterspace.toolbox.utils.DateUtils;
import com.asofterspace.toolbox.utils.Record;
import com.asofterspace.toolbox.utils.StrUtils;

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
			taskRecord.set(ORIGIN, ourTask.getOrigin());
			taskRecord.set(PRIORITY, ourTask.getPriority());
			taskRecord.set(PRIORITY_ESCALATION_AFTER_DAYS, ourTask.getPriorityEscalationAfterDays());
			taskRecord.set(DURATION, ourTask.getDuration());
			if (ourTask.hasAnId()) {
				taskRecord.set(ID, ourTask.getId());
			}
		}
		return taskRecord;
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

	/**
	 * Returns true if it worked and an ad hoc task was created, and false otherwise
	 */
	public boolean addAdHocTask(String title, String details, String dateStr, String origin, Integer priority,
		Integer priorityEscalationAfterDays, String duration) {

		Date scheduleDate = DateUtils.parseDate(dateStr);

		GenericTask addedTask = super.addAdHocTask(title, details, scheduleDate);

		if (addedTask == null) {
			return false;
		}

		if (addedTask instanceof Task) {
			Task ourTask = (Task) addedTask;
			ourTask.setOrigin(origin);
			ourTask.setPriority(priority);
			ourTask.setPriorityEscalationAfterDays(priorityEscalationAfterDays);
			ourTask.setDuration(durationStrToInt(duration));
		} else {
			System.out.println("Something very weird happened, we encountered a task which is not a Task!");
		}

		save();

		return true;
	}

	private Integer durationStrToInt(String str) {
		if (str == null) {
			return null;
		}
		if (str.contains(":")) {
			String[] strs = str.split(":");
			String hours = strs[0];
			String minutes = strs[1];
			Integer houri = StrUtils.strToInt(hours);
			Integer minuti = StrUtils.strToInt(minutes);
			if ((houri == null) || (minuti == null)) {
				return null;
			}
			return (houri * 60) + minuti;
		}
		String hours = str;
		Integer result = StrUtils.strToInt(hours);
		if (result == null) {
			return null;
		}
		return result * 60;
	}

	public boolean setTaskToDone(String id) {
		List<Task> tasks = getCurrentTaskInstancesAsTasks();
		for (Task task : tasks) {
			if (task.hasId(id)) {
				task.setDone(true);
				task.setDoneDate(DateUtils.now());
				task.setDoneLog("");
				save();
				return true;
			}
		}
		return false;
	}

	public void save() {
		saveIntoRecord(database.getLoadedRoot());
		database.save();
	}

}
