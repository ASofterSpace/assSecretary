/**
 * Unlicensed code created by A Softer Space, 2021
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.facts;

import com.asofterspace.toolbox.utils.Record;


public class Fact {

	private String answer;

	private Boolean hugo;


	public Fact(Record rec) {
		this.answer = rec.getString("answer");
		this.hugo = rec.getBoolean("hugo");
	}

	public String getAnswer() {
		return answer;
	}

	public boolean getHugo() {
		if (hugo == null) {
			return false;
		}
		return hugo;
	}

}
