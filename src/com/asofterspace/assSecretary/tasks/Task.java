/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.tasks;

import com.asofterspace.toolbox.calendar.GenericTask;
import com.asofterspace.toolbox.io.HTML;
import com.asofterspace.toolbox.utils.DateUtils;
import com.asofterspace.toolbox.utils.StrUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;


public class Task extends GenericTask {

	// the origin of the task - can be a company customer we are working for, or "private"
	private String origin;

	// the priority of the task (independent of when it is scheduled and released), as
	// integer between 0 (the universe is going to end immediately if we don't do this RIGHT NOW!)
	// and 1000000 (meh, seriously whatever)
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

	// if this task was released based on an id
	private String releasedBasedOnId;


	public Task() {
		super(null, null, null, null, null, null, null);
	}

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
			this.releasedBasedOnId = otherTask.releasedBasedOnId;

			// never copy another entry's id, but instead, generate a new one!
			this.id = null;
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

	/**
	 * Get the current priority on a certain day
	 */
	public int getCurrentPriority(Date currentDay) {

		// set a useful default
		int result = 500000;

		// get the baseline priority set by the user
		if (priority != null) {
			result = priority;
		}

		// if this task is just a ghost of a scheduled task, then it will not have its priority escalated
		if (!isInstance()) {
			return result;
		}

		// adjust based on priority escalation value
		if ((priorityEscalationAfterDays != null) && (priorityEscalationAfterDays > 0)) {
			Date releaseDate = getReleaseDate();
			Integer difference = DateUtils.getDayDifference(releaseDate, currentDay);
			if (difference != null) {
				// here:     today - releaseDate >= priorityEscalationAfterDays
				// same as:                today >= releaseDate + priorityEscalationAfterDays
				if (difference >= priorityEscalationAfterDays) {
					// if priorityEscalationAfterDays have passed, then reduce priority by 10%,
					// and keep reducing a bit more each day
					result -= (100000 * difference) / priorityEscalationAfterDays;
				}
			}

		}
		return result;
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

	public String getDurationStr() {
		if (duration == null) {
			return "00:00";
		}
		int minutes = duration % 60;
		int hours = duration / 60;
		return StrUtils.leftPad0(hours, 2) + ":" + StrUtils.leftPad0(minutes, 2);
	}

	public void setDurationStr(String durationStr) {
		duration = durationStrToInt(durationStr);
	}

	public void setDuration(Integer duration) {
		this.duration = duration;
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

	public String getReleasedBasedOnId() {
		return releasedBasedOnId;
	}

	public void setReleasedBasedOnId(String releasedBasedOnId) {
		this.releasedBasedOnId = releasedBasedOnId;
	}

	public String toHtmlStr(boolean historicalView, boolean reducedView, boolean onShortlist, Date dateForWhichHtmlGetsDisplayed) {

		String id = getId();

		String html = "";

		String futureTaskStr = "";
		if (releasedInTheFuture()) {
			futureTaskStr = " future-task";
		}

		if (onShortlist) {
			// tasks on the shortlist are not affected by such mundane things as filtering etc.
			html += "<div class='line'>";
		} else {
			html += "<div class='line task task-with-origin-" + getOrigin() + futureTaskStr + "' id='task-" + id + "'>";
		}
		if (reducedView) {
			html += "<div>";
		}
		if (!reducedView) {
			html += "<span style='width: 8%;'>";
			if (historicalView) {
				html += DateUtils.serializeDate(getDoneDate());
			} else {
				html += getReleasedDateStr();
			}
			html += "</span>";
		}
		// by default, main Width is 60%
		float mainWidth = 60;
		html += "<span style='width: %[WIDTH];'";
		int prio = getCurrentPriority(dateForWhichHtmlGetsDisplayed);
		if (prio < 100000) {
			html += " class='error'";
		} else if (prio < 360000) {
			html += " class='warning'";
		}
		String escTitle = HTML.escapeHTMLstr(title);
		html += " title='" + escTitle + "'>";
		html += escTitle;
		html += "</span>";

		boolean hasDetails = false;

		String miniBtnStyle = "margin-left: 0.5%;";
		String btnStyle = "width: 5.5%; " + miniBtnStyle;

		if (reducedView) {
			html += "</div>";
			html += "<div style='text-align: center; white-space: nowrap;'>";
		} else {
			List<String> details = getDetails();
			hasDetails = (details != null) && (details.size() > 0);
			if (hasDetails) {
				if (details.size() == 1) {
					if ((details.get(0) == null) || (details.get(0).length() == 0)) {
						hasDetails = false;
					}
				}
			}
		}

		if (TaskCtrl.FINANCE_ORIGIN.equals(origin)) {

			html += "<span style='width: 8%;' class='button'>";
			html += "(from Mari)";
			html += "</span>";
			mainWidth += 18.5;

		// if this is not an actual instance, but just a ghost of a scheduled task, then of course it cannot
		// be edited in any way shape or form, so no point in showing any of the regular buttons :)
		} else if (!isInstance()) {
			// on the other hand, a non-instance CAN be prematurely released to achieve an instance which CAN be edited!

			html += "<span style='" + btnStyle + "' class='button' onclick='secretary.taskPreRelease(\"" + id + "\", " +
				"\"" + DateUtils.serializeDate(dateForWhichHtmlGetsDisplayed) + "\")'>";
			html += "Pre-Release";
			html += "</span>";
			html += "<span style='" + btnStyle + "' class='button' onclick='secretary.repeatingTaskEdit(\"" + id + "\")'>";
			html += "Edit";
			html += "</span>";
			html += "<span style='" + btnStyle + "' class='button' onclick='secretary.taskDelete(\"" + id + "\", ";
			html += "\"" + HTML.escapeHTMLstr(StrUtils.replaceAll(title, "\"", "")) + "\", null)'>";
			html += "Delete";
			html += "</span>";

		} else {

			if (!reducedView) {
				if (hasDetails) {
					String detailsId = id;
					if (onShortlist) {
						detailsId += "-shortlist";
					}
					html += "<span style='" + btnStyle + "' class='button' onclick='secretary.taskDetails(\"" + detailsId + "\")'>";
					html += "Details";
					html += "</span>";
				} else {
					// this is 1.5% width instead of 5.5%, so that the main width can be increased by 4 percent points
					mainWidth += 4;
					html += "<span style='width:1.5%;" + miniBtnStyle + " visibility: hidden;' class='button'>";
					html += "&nbsp;";
					html += "</span>";
				}
			}

			if (hasBeenDone()) {
				html += "<span style='" + btnStyle + "' class='button' onclick='secretary.taskUnDone(\"" + id + "\")'>";
				html += "Un-done";
				html += "</span>";
			} else {
				html += "<span style='" + btnStyle + "' class='button' onclick='secretary.taskDone(\"" + id + "\")'>";
				html += "Done";
				html += "</span>";
			}
			html += "<span style='" + btnStyle + "' class='button' onclick='secretary.taskEdit(\"" + id + "\")'>";
			html += "Edit";
			html += "</span>";
			html += "<span style='" + btnStyle + "' class='button' onclick='secretary.taskDelete(\"" + id + "\", ";
			html += "\"" + HTML.escapeHTMLstr(StrUtils.replaceAll(title, "\"", "")) + "\", \"" + getReleasedDateStr() + "\")'>";
			html += "Delete";
			html += "</span>";
			if ((!reducedView) && (!historicalView)) {
				if (onShortlist) {
					html += "<span style='" + miniBtnStyle + "' class='button' onclick='secretary.taskRemoveFromShortList(\"" + id + "\")'>";
					html += "&#9734;";
					html += "</span>";
					html += "<span style='" + miniBtnStyle + "' class='button' onclick='secretary.taskPutOnShortListTomorrow(\"" + id + "\")'>";
					html += "&#x2B6F;";
					html += "</span>";
					// we are showing two minibuttons instead of one, so the main width has to be shorter here
					mainWidth -= 3;
				} else {
					html += "<span style='" + miniBtnStyle + "' class='button' onclick='secretary.taskAddToShortList(\"" + id + "\")'>";
					html += "&#9733;";
					html += "</span>";
				}
			}
		}

		if (reducedView) {
			html += "</div>";
		}

		if (hasDetails) {
			html += "<div style='display: none' class='details' id='task-details-" + id;
			if (onShortlist) {
				html += "-shortlist";
			}
			html += "'>";
			List<String> safeDetails = new ArrayList<>();
			if (details != null) {
				for (String detail : details) {
					detail = StrUtils.replaceAll(detail, "<", "&lt;");
					detail = StrUtils.replaceAll(detail, ">", "&gt;");
					detail = addHtmlLinkToStringContent(detail, "http://");
					detail = addHtmlLinkToStringContent(detail, "https://");
					safeDetails.add(detail);
				}
			}
			html += StrUtils.join("<br>", safeDetails);
			html += "</div>";
		}
		html += "</div>";

		html = StrUtils.replaceAll(html, "%[WIDTH]", mainWidth + "%");

		return html;
	}

	public static String addHtmlLinkToStringContent(String contentStr, String prefix) {

		// replace http:// with actual links
		int start = contentStr.indexOf(prefix);
		while (start >= 0) {
			int end = StrUtils.getLinkEndFromPosition(start, contentStr);
			if (end < 0) {
				end = contentStr.length();
			}
			String midStr = contentStr.substring(start, end);

			// do we actually do anything with this midStr (so with this link that we encountered)?

			// by default, nope!
			boolean adjustMidStr = false;
			if (start > 0) {
				// if we do not see a closed XML tag before it (so if we are not inside of an XML tag), yea!
				if (contentStr.charAt(start - 1) != '>') {
					adjustMidStr = true;
				} else {
					// if we are "enclosed" by an XML tag (that is, there is one before us), but it is just
					// a <br>, then also yeah!
					if ((start > 3) && (contentStr.substring(start - 4, start).equals("<br>"))) {
						adjustMidStr = true;
					}
				}
			} else {
				// if the link is the very first thing in the string, yea!
				adjustMidStr = true;
			}

			if (adjustMidStr) {
				// ... but if we are not, happily add a link!
				midStr = "<a href='" + midStr + "' target='_blank'>" + midStr + "</a>";
			}
			contentStr =
				contentStr.substring(0, start) +
				midStr +
				contentStr.substring(end);
			start = contentStr.indexOf(prefix, start + midStr.length() + 1);
		}

		return contentStr;
	}

}
