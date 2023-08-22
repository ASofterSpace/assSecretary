/**
 * Unlicensed code created by A Softer Space, 2023
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.locations;

import com.asofterspace.toolbox.utils.DateHolder;
import com.asofterspace.toolbox.utils.Record;

import java.util.Date;


public class WhenWhere {

	private DateHolder date;

	private String time;

	private String where;

	private static final String DATE = "date";

	private static final String TIME = "time";

	private static final String WHERE = "where";


	public WhenWhere(Record rec) {
		this.date = rec.getDateHolder(DATE);
		this.time = rec.getString(TIME);
		this.where = rec.getString(WHERE);
	}

	public Record toRecord() {
		Record result = Record.emptyObject();
		result.set(DATE, this.date);
		result.set(TIME, this.time);
		result.set(WHERE, this.where);
		return result;
	}

	public String getWhere() {
		return where;
	}

	public Date getDate() {
		return date.getDate();
	}
}
