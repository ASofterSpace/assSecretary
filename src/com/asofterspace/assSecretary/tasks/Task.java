/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.tasks;

import com.asofterspace.assSecretary.AssSecretary;
import com.asofterspace.toolbox.calendar.GenericTask;
import com.asofterspace.toolbox.io.HTML;
import com.asofterspace.toolbox.utils.DateUtils;
import com.asofterspace.toolbox.utils.StrUtils;

import java.util.Date;
import java.util.List;
import java.util.UUID;


public class Task extends GenericTask {

	private final static int HUNDREDTH_PRIORITY = 10000;
	private final static int TENTH_PRIORITY = 10 * HUNDREDTH_PRIORITY;
	private final static int HALF_PRIORITY = 5 * TENTH_PRIORITY;
	private final static int MAX_PRIORITY = 10 * TENTH_PRIORITY;
	private final static int ERROR_CUTOFF = 10 * HUNDREDTH_PRIORITY;
	private final static int WARNING_CUTOFF = 36 * HUNDREDTH_PRIORITY;

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

	// if this task originated in the assWorkbench, this is the link to it (if null, this task did not
	// originate in the assWorkbench)
	private String workbenchLink;

	// for a generic external source (like the LTC), this contains the display name of that source
	private String externalSource;

	// should we show this one as scheduled in future days?
	private Boolean showAsScheduled;

	// is this task cleaned up automatically after a week?
	private Boolean autoCleanTask;


	public Task() {
		super(null, null, null, null, null, null, null, null, null);
	}

	public Task(String title, Integer scheduledOnDay, List<String> scheduledOnDaysOfWeek,
		List<Integer> scheduledInMonths, List<Integer> scheduledInYears, List<String> details,
		List<String> onDone, Boolean biweeklyEven, Boolean biweeklyOdd) {
		super(title, scheduledOnDay, scheduledOnDaysOfWeek, scheduledInMonths, scheduledInYears, details, onDone,
			biweeklyEven, biweeklyOdd);
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
			this.workbenchLink = otherTask.workbenchLink;
			this.externalSource = otherTask.externalSource;
			this.showAsScheduled = otherTask.showAsScheduled;
			this.autoCleanTask = otherTask.autoCleanTask;

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

	/**
	 * get three letter abbreviation of the origin
	 */
	public String getOriginTLA() {
		String tla = getOrigin();
		if (tla == null) {
			return "N/A";
		}
		switch (tla) {
			case "private":
				tla = AssSecretary.getDatabase().getUsername();
				break;
			case "asofterspace":
				return "ASS";
			case "ppcc":
				return "PPCC";
			case "supervisionearth":
				return "SVE";
			case "firefighting":
				return "FF";
			case "seebruecke":
				return "SB";
		}
		tla = tla.substring(0, 3).toUpperCase();
		return tla;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}

	public Integer getPriority() {
		return priority;
	}

	/**
	 * Get the current priority on a certain day, and if historicalView is true, order later entries
	 * to the top, while if it is false, order earlier entries to the top (like on the shortlist)
	 */
	public int getCurrentPriority(Date currentDay, boolean historicalView) {

		// if this task is "scheduled" for a particular time (so if its title starts with "HH:MM "
		// or "HH:MM.."), then reduce priority by A LOT, also based on the time, so that timed
		// entries are (nearly) always at the top of the list, sorted by their time
		if (title.length() > 5) {
			if ((title.charAt(2) == ':') && ((title.charAt(5) == ' ') || (title.charAt(5) == '.')) &&
				Character.isDigit(title.charAt(0)) && Character.isDigit(title.charAt(1)) &&
				Character.isDigit(title.charAt(3)) && Character.isDigit(title.charAt(4))) {

				int timeVal = StrUtils.strToInt(title.substring(0, 2) + title.substring(3, 5));
				if (historicalView) {
					return -(100 + timeVal);
				} else {
					return -(2500 - timeVal);
				}
			}
		}

		// timed entries start at priority -100, so ensure that untimed entries do not go below -50,
		// so as to not become mixed up among them
		int result = getCurrentPriorityIgnoringTime(currentDay);

		if (result < -50) {
			return -50;
		}

		return result;
	}

	public int getCurrentPriorityIgnoringTime(Date currentDay) {

		// set a useful default
		int result = HALF_PRIORITY;

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
					result -= (TENTH_PRIORITY * difference) / priorityEscalationAfterDays;
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
		// accept o and O as 0 in the duration field
		str = StrUtils.replaceAll(str, "o", "0");
		str = StrUtils.replaceAll(str, "O", "0");
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

	/**
	 * historicalView .. on task log?
	 * reducedView .. on weekly / monthly view?
	 * onShortlist .. on main page, but shortlist?
	 */
	public void appendHtmlTo(StringBuilder html, boolean historicalView, boolean reducedView,
		boolean onShortlist, Date dateForWhichHtmlGetsDisplayed, boolean standalone, boolean showButtons,
		String additionalClassName) {

		String id = getId();

		// by default, main Width is the full 100%
		float mainWidth = 100;

		if (releasedInTheFuture()) {
			additionalClassName += " future-task";
		}

		String miniBtnStyle = "margin-left: 0.5%; box-sizing: border-box; ";
		String btnStyle = "width: 6%; " + miniBtnStyle;

		if (onShortlist) {
			// tasks on the shortlist are not affected by such mundane things as filtering etc.
			html.append("<div class='line' id='task-");
			html.append(id);
			html.append("-on-shortlist'>");

			html.append("<span style='width: 2.5%; ");
			String leftButtonStyle = miniBtnStyle;
			leftButtonStyle = StrUtils.replaceAll(leftButtonStyle, "margin-left", "margin-right");
			html.append(leftButtonStyle);
			html.append("' class='button' id='select-task-");
			html.append(id);
			html.append("-on-shortlist' onclick='secretary.taskSelect(\"");
			html.append(id);
			html.append("\")'>");
			html.append("[ ]");
			html.append("</span>");
			mainWidth -= 3;

		} else {
			if (standalone) {
				// standalone tasks are not affected by such mundane things as filtering etc. either
				html.append("<div class='line'>");
			} else {
				html.append("<div class='line task task-with-origin-");
				html.append(getOrigin());
				html.append(additionalClassName);
				html.append("' id='task-");
				html.append(id);
				html.append("'>");
			}
		}
		if (reducedView) {
			html.append("<div>");
		}
		if (!reducedView) {
			if (isInstance()) {
				html.append("<span style='width: 7.5%;'>");
				if (historicalView) {
					Date doneDate = getDoneDate();
					if (doneDate == null) {
						// on upload calendar view, instead of having null everywhere,
						// just have the single task dot
						html.append("· ");
					} else {
						html.append(DateUtils.serializeDate(getDoneDate()));
					}
				} else {
					html.append(getReleasedDateStr());
				}
				html.append("</span>");
			} else {
				String schedDateStr = getScheduleDateStr();
				html.append("<span style='width: 7.5%;' title='");
				html.append(schedDateStr);
				html.append("'>");
				html.append(schedDateStr);
				html.append("</span>");
			}
			mainWidth -= 7.5;
		}

		html.append("<span class='tla'");
		if (reducedView) {
			html.append(" style='vertical-align: 1pt;'");
		}
		html.append(">");
		html.append(getOriginTLA());
		html.append("</span>");
		mainWidth -= 3;

		html.append("<span style='width: ");
		int charsBeforeWidth = html.length();
		html.append(";'");
		int prio = getCurrentPriorityIgnoringTime(dateForWhichHtmlGetsDisplayed);
		if (prio < ERROR_CUTOFF) {
			html.append(" class='error'");
		} else if (prio < WARNING_CUTOFF) {
			html.append(" class='warning'");
		}
		html.append(" title='");
		html.append(HTML.escapeHTMLstr(title));
		html.append("\n");
		boolean plainSingle = false;
		if (isInstance()) {
			if (getReleasedBasedOnId() == null) {
				html.append("(plain single task)");
				plainSingle = true;
			} else {
				html.append("(instance of a repeating task)");
			}
		} else {
			html.append("(abstract repeating task, not an instance at all)");
		}
		html.append("'>");
		if (reducedView) {
			html.append("&nbsp;");
		}
		// on main page...
		if ((!historicalView) && (!reducedView)) {
			// ... add a dot in front of single entries, a pipe in front of all others
			if (plainSingle) {
				html.append("· ");
			} else {
				html.append("| ");
			}
		}
		html.append(makePlainTextLineInteractive(title));
		html.append("</span>");

		boolean hasDetails = false;

		if (reducedView) {
			html.append("</div>");
			html.append("<div style='text-align: center; white-space: nowrap;'>");
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

		String detailsId = id;
		if (onShortlist) {
			detailsId += "-shortlist";
		}
		if ((additionalClassName != null) && (!"".equals(additionalClassName))) {
			detailsId += "-" + StrUtils.replaceAll(additionalClassName, " ", "");
		}

		if (showButtons) {

			if (!reducedView) {
				if (hasDetails) {
					html.append("<span style='");
					html.append(btnStyle);
					html.append("' class='button' onclick='secretary.taskDetails(\"");
					html.append(detailsId);
					html.append("\")'>");
					html.append("Details");
					html.append("</span>");
					mainWidth -= 6.5;
				}
			}

			if (externalSource != null) {

				html.append("<span style='width: 8%; " + miniBtnStyle + "' class='button'>");
				html.append("(from ");
				html.append(externalSource);
				html.append(")");
				html.append("</span>");
				mainWidth -= 8.5;

			} else if (workbenchLink != null) {

				html.append("<span style='width: 11%; " + miniBtnStyle + "' class='button' onclick='secretary.openInNewTab(\"");
				html.append(workbenchLink);
				html.append("\")'>");
				html.append("(from Workbench)");
				html.append("</span>");
				mainWidth -= 11.5;

			} else if (TaskCtrl.FINANCE_ORIGIN.equals(origin)) {

				html.append("<span style='width: 9%; " + miniBtnStyle + "' class='button'>");
				html.append("(from Mari)");
				html.append("</span>");
				mainWidth -= 9.5;

			// if this is not an actual instance, but just a ghost of a scheduled task, then of course it cannot
			// be edited in any way shape or form, so no point in showing any of the regular buttons :)
			} else if (!isInstance()) {
				// on the other hand, a non-instance CAN be prematurely released to achieve an instance which CAN be edited!

				html.append("<span style='");
				if (reducedView) {
					html.append(btnStyle);
					mainWidth -= 6.5;
				} else {
					html.append("width: 9.5%; ");
					html.append(miniBtnStyle);
					mainWidth -= 10;
				}
				html.append("' class='button' onclick='secretary.taskPreRelease(\"");
				html.append(id);
				html.append("\", ");
				html.append("\"");
				html.append(DateUtils.serializeDate(dateForWhichHtmlGetsDisplayed));
				html.append("\")'>");
				html.append("Pre-Release");
				html.append("</span>");

				html.append("<span style='");
				html.append(btnStyle);
				html.append("' class='button' onclick='secretary.repeatingTaskEdit(\"");
				html.append(id);
				html.append("\")'>");
				html.append("Edit");
				html.append("</span>");
				mainWidth -= 6.5;

				html.append("<span style='");
				html.append("width: 10.5%; ");
				html.append(miniBtnStyle);
				html.append("' class='button' onclick='secretary.taskDelete(\"");
				html.append(id);
				html.append("\", ");
				html.append("\"");
				html.append(HTML.escapeHTMLstr(StrUtils.replaceAll(title, "\"", "")));
				html.append("\", null)'>");
				if (reducedView) {
					// on the reduced view, there is not enough space for the full button caption
					html.append("Del.P.");
				} else {
					html.append("Delete Parent");
				}
				html.append("</span>");
				mainWidth -= 11;

			} else {

				if (hasBeenDone()) {
					if (historicalView) {
						html.append("<span style='");
						html.append("width: 2%; ");
						html.append(miniBtnStyle);
						html.append("' class='button' onclick='secretary.taskUp(\"");
						html.append(id);
						html.append("\")' id='task-up-" + id + "'>");
						html.append("/\\");
						html.append("</span>");
						mainWidth -= 2.5;

						html.append("<span style='");
						html.append("width: 2%; ");
						html.append(miniBtnStyle);
						html.append("' class='button' onclick='secretary.taskDown(\"");
						html.append(id);
						html.append("\")' id='task-down-" + id + "'>");
						html.append("\\/");
						html.append("</span>");
						mainWidth -= 2.5;
					}

					html.append("<span style='");
					html.append(btnStyle);
					html.append("' class='button' onclick='secretary.taskUnDone(\"");
					html.append(id);
					html.append("\")'>");
					html.append("Un-done");
					html.append("</span>");
					mainWidth -= 6.5;
				} else {
					html.append("<span style='");
					html.append(btnStyle);
					html.append("' class='button' onclick='secretary.taskDone(\"");
					html.append(id);
					html.append("\")'>");
					html.append("Done");
					html.append("</span>");
					mainWidth -= 6.5;
				}

				html.append("<span style='");
				html.append(btnStyle);
				html.append("' class='button' onclick='secretary.taskEdit(\"");
				html.append(id);
				html.append("\")'>");
				html.append("Edit");
				html.append("</span>");
				mainWidth -= 6.5;

				html.append("<span style='");
				html.append(btnStyle);
				html.append("' class='button' onclick='secretary.taskDelete(\"");
				html.append(id);
				html.append("\", ");
				html.append("\"");
				html.append(HTML.escapeHTMLstr(StrUtils.replaceAll(title, "\"", "")));
				html.append("\", \"");
				html.append(getReleasedDateStr());
				html.append("\")'>");
				html.append("Delete");
				html.append("</span>");
				mainWidth -= 6.5;

				if ((!reducedView) && (!historicalView) && (!standalone)) {
					if (onShortlist) {
						html.append("<span style='width: 2.5%; ");
						html.append(miniBtnStyle);
						html.append("' class='button' onclick='secretary.taskRemoveFromShortList(\"");
						html.append(id);
						html.append("\")'>");
						html.append("&#9734;");
						html.append("</span>");
						mainWidth -= 3;

						html.append("<span style='width: 2.5%; ");
						html.append(miniBtnStyle);
						html.append("' class='button' onclick='secretary.taskPutOnShortListTomorrow(\"");
						html.append(id);
						html.append("\")'>");
						html.append("&#x2B6F;");
						html.append("</span>");
						mainWidth -= 3;

						String tla = getOriginTLA();

						html.append("<span style='width: 2.5%; ");
						html.append(miniBtnStyle);
						html.append("' class='button' onclick='secretary.tasksPutOnShortListTomorrow(window.shortlistTLAs." + tla + ")'>");
						html.append("&#x21F6;");
						html.append("</span>");
						mainWidth -= 3;

						html.append("<script>\n");
						html.append("if (!window.shortlistTLAs) {\n");
						html.append("  window.shortlistTLAs = {};\n");
						html.append("}\n");
						html.append("if (!window.shortlistTLAs." + tla + ") {\n");
						html.append("  window.shortlistTLAs." + tla + " = [];\n");
						html.append("}\n");
						html.append("window.shortlistTLAs." + tla + ".push(\"" + id + "\");\n");
						html.append("</script>\n");
					} else {
						html.append("<span style='width: 2.5%; ");
						html.append(miniBtnStyle);
						html.append("' class='button' onclick='secretary.taskAddToShortList(\"");
						html.append(id);
						html.append("\")'>");
						html.append("&#9733;");
						html.append("</span>");
						mainWidth -= 3;
					}
				}
			}
		}

		if (reducedView) {
			html.append("</div>");
		}

		if (hasDetails) {
			html.append("<div style='display: none' class='details' id='task-details-");
			html.append(detailsId);
			html.append("'>");
			if (details != null) {
				int lastDetail = details.size() - 1;
				while (lastDetail >= 0) {
					if (!details.get(lastDetail).equals("")) {
						break;
					}
					lastDetail--;
				}
				for (int i = 0; i <= lastDetail; i++) {
					String detail = details.get(i);
					detail = makePlainTextLineInteractive(detail);
					html.append(detail);
					html.append("<br>");
				}
				html.append("&nbsp;");
			}
			html.append("</div>");
		}
		html.append("</div>");

		html.insert(charsBeforeWidth, mainWidth + "%");
	}

	public static String makePlainTextLineInteractive(String text) {

		text = HTML.escapeHTMLstr(text);

		text = addHtmlLinkToStringContent(text, "http://");
		text = addHtmlLinkToStringContent(text, "https://");

		return text;
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

	public String getWorkbenchLink() {
		return workbenchLink;
	}

	public void setWorkbenchLink(String workbenchLink) {
		this.workbenchLink = workbenchLink;
	}

	public String getExternalSource() {
		return externalSource;
	}

	public void setExternalSource(String externalSource) {
		this.externalSource = externalSource;
	}

	public boolean getShowAsScheduled() {
		if (showAsScheduled == null) {
			return true;
		}
		return showAsScheduled;
	}

	public void setShowAsScheduled(Boolean showAsScheduled) {
		this.showAsScheduled = showAsScheduled;
	}

	public boolean getAutoCleanTask() {
		if (autoCleanTask == null) {
			return false;
		}
		return autoCleanTask;
	}

	public void setAutoCleanTask(Boolean autoCleanTask) {
		this.autoCleanTask = autoCleanTask;
	}

}
