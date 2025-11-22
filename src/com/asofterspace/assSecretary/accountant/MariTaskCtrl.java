/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.accountant;

import com.asofterspace.toolbox.calendar.TaskCtrlBase;
import com.asofterspace.toolbox.utils.DateUtils;
import com.asofterspace.toolbox.utils.Record;


public class MariTaskCtrl extends TaskCtrlBase {

	private Record rootToLoad;


	public MariTaskCtrl(MariDatabase database) {

		this.rootToLoad = database.getLoadedRoot();
	}

	// necessary to call after the constructor ugh .-.
	public void init() {

		loadFromRoot(rootToLoad);

		rootToLoad = null;

		// generate instances, but do not save them!
		generateNewInstances(DateUtils.now());
	}

}
