/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.accountant;

import com.asofterspace.toolbox.calendar.TaskCtrlBase;
import com.asofterspace.toolbox.utils.DateUtils;
import com.asofterspace.toolbox.utils.Record;


public class MariTaskCtrl extends TaskCtrlBase {


	public MariTaskCtrl(MariDatabase database) {

		loadFromDatabase(database);

		// generate instances, but do not save them!
		generateNewInstances(DateUtils.now());
	}

	private void loadFromDatabase(MariDatabase database) {

		Record root = database.getLoadedRoot();

		loadFromRoot(root);
	}

}
