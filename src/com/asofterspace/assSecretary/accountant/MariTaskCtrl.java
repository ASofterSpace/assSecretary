/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.accountant;

import com.asofterspace.toolbox.calendar.GenericTask;
import com.asofterspace.toolbox.calendar.TaskCtrlBase;
import com.asofterspace.toolbox.utils.DateUtils;
import com.asofterspace.toolbox.utils.Record;

import java.util.ArrayList;
import java.util.List;


public class MariTaskCtrl extends TaskCtrlBase {

	private List<GenericTask> currentTaskInstances;


	public MariTaskCtrl(MariDatabase database) {

		loadFromDatabase(database);

		// generate instances, but do not save them!
		generateNewInstances(DateUtils.now());
	}

	private void loadFromDatabase(MariDatabase database) {

		Record root = database.getLoadedRoot();

		loadFromRoot(root);
	}

	public List<GenericTask> getCurrentTaskInstances() {

		currentTaskInstances = new ArrayList<>();

		List<GenericTask> tasks = getTaskInstances();

		for (GenericTask task : tasks) {
			if ((task.hasBeenDone() == null) || !task.hasBeenDone()) {
				currentTaskInstances.add(task);
			}
		}

		return currentTaskInstances;
	}

}
