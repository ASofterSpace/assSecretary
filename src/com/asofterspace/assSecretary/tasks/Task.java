/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.tasks;

import com.asofterspace.toolbox.calendar.GenericTask;
import com.asofterspace.toolbox.io.HTML;
import com.asofterspace.toolbox.utils.StrUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class Task extends GenericTask {

	// the origin of the task - can be a company customer we are working for, or "private"
	private String origin;

	// the priority of the task (independent of when it is scheduled and released), as
	// integer between 0 (the universe is going to end immediately if we don't do this RIGHT NOW!)
	// and 100 (meh, seriously whatever)
	private Integer priority;

	// if we do not manage to perform this task on the release date, how will the priority
	// escalate - that is, after how many days will the priority go up?
	// (null for priority never going up)
	private Integer priorityEscalationAfterDays;

	// the duration of this task in minutes - null and 0 count as "N/A"
	private Integer duration;

	// the id of this task... a task does not need to have an id, but when one is needed to identify
	// it by calling getId(), if none is assigned, one will be given and kept forever
	private String id;


	public Task(String title, Integer scheduledOnDay, List<String> scheduledOnDaysOfWeek,
		List<Integer> scheduledInMonths, List<Integer> scheduledInYears, List<String> details,
		List<String> onDone) {
		super(title, scheduledOnDay, scheduledOnDaysOfWeek, scheduledInMonths, scheduledInYears, details, onDone);
	}

	/**
	 * Constructs a new task based on an existing without keeping the task instance details,
	 * but instead just keeping the generic task details
	 */
	public Task(GenericTask other) {
		super(other);

		if (other instanceof Task) {
			Task otherTask = (Task) other;
			this.origin = otherTask.origin;
			this.priority = otherTask.priority;
			this.priorityEscalationAfterDays = otherTask.priorityEscalationAfterDays;
			this.duration = otherTask.duration;
			this.id = otherTask.id;
		}
	}

	@Override
	public Task getNewInstance() {
		return new Task(this);
	}

	public String getOrigin() {
		return origin;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}

	public Integer getPriority() {
		return priority;
	}

	public Integer getCurrentPriority() {
		// TODO :: adjust based on priority escalation value
		return priority;
	}

	public void setPriority(Integer priority) {
		this.priority = priority;
	}

	public Integer getPriorityEscalationAfterDays() {
		return priorityEscalationAfterDays;
	}

	public void setPriorityEscalationAfterDays(Integer priorityEscalationAfterDays) {
		this.priorityEscalationAfterDays = priorityEscalationAfterDays;
	}

	public Integer getDuration() {
		return duration;
	}

	public void setDuration(Integer duration) {
		this.duration = duration;
	}

	public boolean hasAnId() {
		return id != null;
	}

	public boolean hasId(String otherId) {
		if (id == null) {
			return false;
		}
		return id.equals(otherId);
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getId() {
		if (id == null) {
			id = "" + UUID.randomUUID();
		}
		return id;
	}

	public String toHtmlStr() {

		String id = getId();

		String html = "";

		String futureTaskStr = "";
		if (releasedInTheFuture()) {
			futureTaskStr = " future-task";
		}

		html += "<div class='line task task-with-origin-" + getOrigin() + futureTaskStr + "' id='task-" + id + "'>";
		html += "<span style='width: 10%;'>";
		html += getReleasedDateStr();
		html += "</span>";
		html += "<span style='width: 60%;'>";
		html += HTML.escapeHTMLstr(title);
		html += "</span>";

		List<String> details = getDetails();
		boolean hasDetails = (details != null) && (details.size() > 0);
		if (hasDetails) {
			if (details.size() == 1) {
				if ((details.get(0) == null) || (details.get(0).length() == 0)) {
					hasDetails = false;
				}
			}
		}

		String btnStyle = "width: 5.5%; margin-left: 0.5%;";
		if (hasDetails) {
			html += "<span style='" + btnStyle + "' class='button' onclick='secretary.taskDetails(\"" + id + "\")'>";
			html += "Details";
			html += "</span>";
		} else {
			html += "<span style='" + btnStyle + " visibility: hidden;' class='button'>";
			html += "&nbsp;";
			html += "</span>";
		}

		html += "<span style='" + btnStyle + "' class='button' onclick='secretary.taskDone(\"" + id + "\")'>";
		html += "Done";
		html += "</span>";
		html += "<span style='" + btnStyle + "' class='button' onclick='secretary.taskEdit(\"" + id + "\")'>";
		html += "Edit";
		html += "</span>";
		html += "<span style='" + btnStyle + "' class='button' onclick='secretary.taskDelete(\"" + id + "\")'>";
		html += "Delete";
		html += "</span>";
		if (hasDetails) {
			html += "<div style='display: none' id='task-details-" + id + "'>";
			List<String> safeDetails = new ArrayList<>();
			if (details != null) {
				for (String detail : details) {
					safeDetails.add(HTML.escapeHTMLstr(detail));
				}
			}
			html += StrUtils.join("<br>", safeDetails);
			html += "</div>";
		}
		html += "</div>";
		return html;
	}

}
