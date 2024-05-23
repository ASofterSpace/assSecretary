/**
 * Unlicensed code created by A Softer Space, 2024
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.eventList;

import com.asofterspace.assSecretary.tasks.Task;
import com.asofterspace.toolbox.utils.DateHolder;
import com.asofterspace.toolbox.utils.DateUtils;
import com.asofterspace.toolbox.utils.Record;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class Event {

	final static String TITLE = "title";
	final static String LOCATION = "location";
	final static String TYPE = "type";
	final static String START_DATE = "startDate";
	final static String END_DATE = "endDate";
	final static String COMMENT = "comment";
	final static String ID = "id";

	private String title;
	private String location;
	private String type;
	private DateHolder startDate;
	private DateHolder endDate;
	private String comment;


	public Event(Record rec) {
		this.title = rec.getString(TITLE);
		this.location = rec.getString(LOCATION);
		this.type = rec.getString(TYPE);
		this.startDate = rec.getDateHolder(START_DATE);
		this.endDate = rec.getDateHolder(END_DATE);
		this.comment = rec.getString(COMMENT);
	}

	public List<Task> getTaskInstances() {
		// System.out.println(startDate + " - " + endDate);
		List<Task> result = new ArrayList<>();
		if ((startDate == null) || startDate.getIsNull()) {
			return result;
		}
		List<Date> days = new ArrayList<>();
		if ((endDate == null) || endDate.getIsNull()) {
			days.add(startDate.getDate());
		} else {
			days = DateUtils.listDaysFromTo(startDate.getDate(), endDate.getDate());
			days.add(endDate.getDate());
		}
		for (Date day : days) {
			Task task = new Task();
			task.setTitle(this.title + " in " + this.location);
			StringBuilder details = new StringBuilder();
			details.append("Type: ");
			details.append(this.type);
			if (endDate == null) {
				details.append("\nDate: ");
				details.append(this.startDate);
			} else {
				details.append("\nDates: ");
				details.append(this.startDate);
				details.append(" .. ");
				details.append(this.endDate);
			}
			if (this.comment != null) {
				details.append("\n\nComment:\n");
				details.append(this.comment);
			}
			task.setDetailsStr(details.toString());
			task.setReleasedDate(day);
			task.setDone(false);
			task.setExternalSource("EVE");
			task.setOrigin("EVE");
			result.add(task);
		}
		return result;
	}

}
