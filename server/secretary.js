window.secretary = {

	// id of the single task we are currently editing
	currentlyEditing: null,

	// id of the repeating task we are currently editing
	currentlyEditingRepeating: null,

	// id of the single or repeating task we are currently deleting
	currentlyDeleting: null,

	// id of the repeating parent task of the single task we are currently editing
	currentlyRepeatingParent: null,

	// array of ids of selected tasks
	selection: [],

	// cache of how far up / down a task has been moved
	taskUpDownCache: {},


	onResize: function() {

		var retry = false;

		var body = document.getElementById("body");
		if (body) {
			body.style.height = window.innerHeight + "px";
		} else {
			retry = true;
		}

		var mainContent = document.getElementById("mainContent");
		if (mainContent) {
			mainContent.style.height = (window.innerHeight - 31) + "px";
		} else {
			retry = true;
		}

		if (window.expectingAvatarAndTabList) {
			var tabList = document.getElementById("tabList");
			var hugoAvatar = document.getElementById("hugoAvatar");
			if (tabList && hugoAvatar) {
				var topPx = hugoAvatar.clientHeight + 25;
				tabList.style.top = topPx + "px";
				tabList.style.height = (window.innerHeight - (topPx + 25)) + "px";
				if (hugoAvatar.clientHeight < 1) {
					retry = true;
				}
			} else {
				retry = true;
			}
		}

		if (retry) {
			// if we could not fully resize now, then let's do it later...
			window.setTimeout(function() {
				window.secretary.onResize();
			}, 100);
		}
	},

	showAddSingleTaskModal: function() {
		var modal = document.getElementById("addSingleTaskModal");
		if (modal) {
			modal.style.display = "block";
			document.getElementById("singleTaskCurrentMode").innerHTML = "adding new single entry";

			this.currentlyEditing = null;

			document.getElementById("modalBackground").style.display = "block";

			document.getElementById("singleTaskReleaseUntil").value = "";

			document.getElementById("singleTaskBasedOnRepeating").style.display = "none";

			window.secretary.taskPriorityChange("single");
		}
	},

	showAddRepeatingTaskModal: function() {
		var modal = document.getElementById("addRepeatingTaskModal");
		if (modal) {
			modal.style.display = "block";
			document.getElementById("repeatingTaskCurrentMode").innerHTML = "adding new repeating tasks";

			this.currentlyEditingRepeating = null;

			document.getElementById("modalBackground").style.display = "block";

			window.secretary.taskPriorityChange("repeating");
		}
	},

	editRepeatingParentTask: function() {
		// hide single task modal
		// (but do refresh, which will be done anyway after closing the repeating task modal)
		var modal = document.getElementById("addSingleTaskModal");
		if (modal) {
			modal.style.display = "none";
		}

		// then open the repeating task modal
		this.repeatingTaskEdit(this.currentlyRepeatingParent);
	},

	submitAndCloseSingleTaskModal: function() {
		this.submitSingleTaskModal(true);
	},

	submitAndCloseRepeatingTaskModal: function() {
		this.submitRepeatingTaskModal(true);
	},

	submitSingleTaskModal: function(closeOnSubmit) {

		var request = new XMLHttpRequest();
		request.open("POST", "addSingleTask", true);
		request.setRequestHeader("Content-Type", "application/json");

		request.onreadystatechange = function() {
			if (request.readyState == 4 && request.status == 200) {
				var result = JSON.parse(request.response);
				// show some sort of confirmation
				if (result.success) {
					var singleTaskSavedLabel = document.getElementById("singleTaskSavedLabel");
					if (singleTaskSavedLabel) {
						singleTaskSavedLabel.style.display = "block";
						window.setTimeout(function () {
							singleTaskSavedLabel.style.display = "none";
						}, 3000);
					}

					// if we were editing before, then we continue editing - but now we are editing
					// the newly saved task
					// if we were NOT editing before, but just saving... then we are still not in
					// editing mode!
					if (window.secretary.currentlyEditing) {
						window.secretary.currentlyEditing = result.id;
					}

					document.getElementById("singleTaskReleaseUntil").value = "";
					if (closeOnSubmit) {
						window.location.reload(false);
					}
				}
			}
		}

		var data = this.gatherDataForSingleTaskSubmit();

		request.send(JSON.stringify(data));

		if (closeOnSubmit) {
			window.secretary.precloseSingleTaskModal();
		}
	},

	gatherDataForSingleTaskSubmit: function() {

		return {
			editingId: window.secretary.currentlyEditing,
			title: document.getElementById("singleTaskTitle").value,
			details: document.getElementById("singleTaskDetails").value,
			releaseDate: document.getElementById("singleTaskReleaseDate").value,
			doneDate: document.getElementById("singleTaskDoneDate").value,
			origin: document.getElementById("singleTaskOrigin").value,
			priority: document.getElementById("singleTaskPriority").value,
			priorityEscalationAfterDays: document.getElementById("singleTaskPriorityEscalationAfterDays").value,
			duration: document.getElementById("singleTaskDuration").value,
			releaseUntil: document.getElementById("singleTaskReleaseUntil").value,
			showAsScheduled: document.getElementById("singleTaskShowAsScheduled").checked,
			autoCleanTask: document.getElementById("singleTaskAutoCleanTask").checked,
		};
	},

	submitRepeatingTaskModal: function(closeOnSubmit) {

		var request = new XMLHttpRequest();
		request.open("POST", "addRepeatingTask", true);
		request.setRequestHeader("Content-Type", "application/json");

		request.onreadystatechange = function() {
			if (request.readyState == 4 && request.status == 200) {
				var result = JSON.parse(request.response);
				// show some sort of confirmation
				if (result.success) {
					var repeatingTaskSavedLabel = document.getElementById("repeatingTaskSavedLabel");
					if (repeatingTaskSavedLabel) {
						repeatingTaskSavedLabel.style.display = "block";
						window.setTimeout(function () {
							repeatingTaskSavedLabel.style.display = "none";
						}, 3000);
					}

					// if we were editing before, then we continue editing - but now we are editing
					// the newly saved task
					// if we were NOT editing before, but just saving... then we are still not in
					// editing mode!
					if (window.secretary.currentlyEditingRepeating) {
						window.secretary.currentlyEditingRepeating = result.id;
					}

					if (closeOnSubmit) {
						window.location.reload(false);
					}
				}
			}
		}

		var data = this.gatherDataForRepeatingTaskSubmit();

		request.send(JSON.stringify(data));

		if (closeOnSubmit) {
			window.secretary.precloseRepeatingTaskModal();
		}
	},

	gatherDataForRepeatingTaskSubmit: function() {

		var result = {
			editingId: window.secretary.currentlyEditingRepeating,
			title: document.getElementById("repeatingTaskTitle").value,
			details: document.getElementById("repeatingTaskDetails").value,
			origin: document.getElementById("repeatingTaskOrigin").value,
			priority: document.getElementById("repeatingTaskPriority").value,
			priorityEscalationAfterDays: document.getElementById("repeatingTaskPriorityEscalationAfterDays").value,
			duration: document.getElementById("repeatingTaskDuration").value,
			xDayOfMonth: document.getElementById("repeatingTaskScheduleXDayOfMonth").value,
			day: document.getElementById("repeatingTaskScheduleDay").value,
			months: document.getElementById("repeatingTaskScheduleMonths").value,
			years: document.getElementById("repeatingTaskScheduleYears").value,
			showAsScheduled: document.getElementById("repeatingTaskShowAsScheduled").checked,
			autoCleanTask: document.getElementById("repeatingTaskAutoCleanTask").checked,
			biweeklyEven: document.getElementById("repeatingTaskBiweeklyEven").checked,
			biweeklyOdd: document.getElementById("repeatingTaskBiweeklyOdd").checked,
		};

		if (result.biweeklyEven && result.biweeklyOdd) {
			alert("Both biweekly even and biweekly odd selected - please do decide for one of them, or neither. ;)")
		}

		return result;
	},

	doneSingleTaskModal: function(copyAfterwards) {

		var request = new XMLHttpRequest();
		request.open("POST", "doneSingleTask", true);
		request.setRequestHeader("Content-Type", "application/json");

		request.onreadystatechange = function() {
			if (request.readyState == 4 && request.status == 200) {
				var result = JSON.parse(request.response);
				// show some sort of confirmation
				if (result.success) {
					if (copyAfterwards) {
						document.getElementById("singleTaskReleaseUntil").value = "";
						var singleTaskSavedLabel = document.getElementById("singleTaskSavedLabel");
						if (singleTaskSavedLabel) {
							singleTaskSavedLabel.style.display = "block";
							window.setTimeout(function () {
								singleTaskSavedLabel.style.display = "none";
							}, 3000);
							window.secretary.currentlyEditing = result.id;
							document.getElementById("singleTaskReleaseDate").value = result.newReleaseDate;
							document.getElementById("singleTaskDoneDate").value = "";
							document.getElementById("singleTaskCurrentMode").innerHTML = "editing one single entry";
						}
					} else {
						window.secretary.removeTaskFromDOM(window.secretary.currentlyEditing);
						window.secretary.closeSingleTaskModal();
					}
				}
			}
		}

		var data = this.gatherDataForSingleTaskSubmit();

		data.copyAfterwards = copyAfterwards;

		request.send(JSON.stringify(data));

		if (!copyAfterwards) {
			window.secretary.precloseSingleTaskModal();
		}
	},

	precloseSingleTaskModal: function() {
		var modal = document.getElementById("addSingleTaskModal");
		if (modal) {
			modal.style.display = "none";
		}
	},

	closeSingleTaskModal: function() {
		this.precloseSingleTaskModal();

		/*
		even though data may have changed, do not reload for faster operations
		// if we are on the inbox page, and the inbox is dirty, then save it before refreshing the page!
		if (window.saveDirtyInbox && window.inboxDirty) {
			window.saveDirtyInbox();

			// reload after saving, as data might have changed while the modal was open...
			window.setInterval(function() {
				window.location.reload(false);
			}, 1000);
		} else {
			// reload, as data might have changed while the modal was open...
			window.location.reload(false);
		}
		*/

		document.getElementById("modalBackground").style.display = "none";
	},

	precloseRepeatingTaskModal: function() {
		var modal = document.getElementById("addRepeatingTaskModal");
		if (modal) {
			modal.style.display = "none";
		}
	},

	closeRepeatingTaskModal: function() {
		this.precloseRepeatingTaskModal();

		/*
		even though data may have changed, do not reload for faster operations
		window.location.reload(false);
		*/

		document.getElementById("modalBackground").style.display = "none";
	},

	taskDetails: function(id) {
		var taskDetailsDiv = document.getElementById("task-details-" + id);
		if (taskDetailsDiv) {
			if (taskDetailsDiv.style.display == "none") {
				taskDetailsDiv.style.display = "block";
			} else {
				taskDetailsDiv.style.display = "none";
			}
		}
	},

	taskDone: function(id) {

		var request = new XMLHttpRequest();
		request.open("POST", "taskDone", true);
		request.setRequestHeader("Content-Type", "application/json");

		request.onreadystatechange = function() {
			if (request.readyState == 4 && request.status == 200) {
				var result = JSON.parse(request.response);
				if (!result.success) {
					alert("The task could not be set to done! Maybe refresh the page?");
				}
			}
		}

		var data = {
			id: id,
		};

		request.send(JSON.stringify(data));
		this.removeTaskFromDOM(id);
	},

	taskUnDone: function(id) {

		var request = new XMLHttpRequest();
		request.open("POST", "taskUnDone", true);
		request.setRequestHeader("Content-Type", "application/json");

		request.onreadystatechange = function() {
			if (request.readyState == 4 && request.status == 200) {
				var result = JSON.parse(request.response);
				if (result.success) {
					window.location.reload(false);
				}
			}
		}

		var data = {
			id: id,
		};

		request.send(JSON.stringify(data));
	},

	resetSingleTaskModal: function() {

		document.getElementById("singleTaskTitle").value = "00:00 ";
		document.getElementById("singleTaskDetails").value = "";
		var DateUtils = toolbox.utils.DateUtils;
		document.getElementById("singleTaskReleaseDate").value = DateUtils.serializeDate(DateUtils.now());
		document.getElementById("singleTaskDoneDate").value = "";
		document.getElementById("singleTaskOrigin").value = "private";
		document.getElementById("singleTaskPriority").value = 500000;
		window.secretary.taskPriorityChange("single");
		document.getElementById("singleTaskPriorityEscalationAfterDays").value = "never";
		document.getElementById("singleTaskDuration").value = "00:00";
		document.getElementById("singleTaskReleaseUntil").value = "";
		document.getElementById("singleTaskShowAsScheduled").checked = true;
		document.getElementById("singleTaskAutoCleanTask").checked = false;
	},

	resetRepeatingTaskModal: function() {

		document.getElementById("repeatingTaskTitle").value = "00:00 ";
		document.getElementById("repeatingTaskDetails").value = "";
		document.getElementById("repeatingTaskOrigin").value = "private";
		document.getElementById("repeatingTaskPriority").value = 500000;
		window.secretary.taskPriorityChange("repeating");
		document.getElementById("repeatingTaskPriorityEscalationAfterDays").value = "never";
		document.getElementById("repeatingTaskDuration").value = "00:00";
		document.getElementById("repeatingTaskScheduleXDayOfMonth").value = "";
		document.getElementById("repeatingTaskScheduleDay").value = "";
		document.getElementById("repeatingTaskScheduleMonths").value = "";
		document.getElementById("repeatingTaskScheduleYears").value = "";
		document.getElementById("repeatingTaskShowAsScheduled").checked = true;
		document.getElementById("repeatingTaskAutoCleanTask").checked = false;
		document.getElementById("repeatingTaskBiweeklyEven").checked = false;
		document.getElementById("repeatingTaskBiweeklyOdd").checked = false;
	},

	taskEdit: function(id) {

		var request = new XMLHttpRequest();
		request.open("GET", "task?id=" + id, true);
		request.setRequestHeader("Content-Type", "application/json");

		request.onreadystatechange = function() {
			if (request.readyState == 4 && request.status == 200) {
				var result = JSON.parse(request.response);
				if (result.success) {
					var modal = document.getElementById("addSingleTaskModal");
					if (modal) {
						modal.style.display = "block";
						document.getElementById("singleTaskCurrentMode").innerHTML = "editing one single entry";

						document.getElementById("modalBackground").style.display = "block";

						document.getElementById("singleTaskTitle").value = result.title;
						document.getElementById("singleTaskDetails").value = result.details;
						document.getElementById("singleTaskReleaseDate").value = result.releaseDate;
						document.getElementById("singleTaskDoneDate").value = result.doneDate;
						document.getElementById("singleTaskOrigin").value = result.origin;
						document.getElementById("singleTaskPriority").value = result.priority;
						window.secretary.taskPriorityChange("single");
						if (result.priorityEscalationAfterDays == null) {
							document.getElementById("singleTaskPriorityEscalationAfterDays").value = "never";
						} else {
							document.getElementById("singleTaskPriorityEscalationAfterDays").value = result.priorityEscalationAfterDays;
						}
						document.getElementById("singleTaskDuration").value = result.duration;
						document.getElementById("singleTaskReleaseUntil").value = "";

						if (result.releasedBasedOnId) {
							window.secretary.currentlyRepeatingParent = result.releasedBasedOnId;
							document.getElementById("singleTaskBasedOnRepeating").style.display="block";
						} else {
							document.getElementById("singleTaskBasedOnRepeating").style.display="none";
						}
						if (result.showAsScheduled == null) {
							result.showAsScheduled = true;
						}
						document.getElementById("singleTaskShowAsScheduled").checked = result.showAsScheduled;
						if (result.autoCleanTask == null) {
							result.autoCleanTask = false;
						}
						document.getElementById("singleTaskAutoCleanTask").checked = result.autoCleanTask;

						window.secretary.currentlyEditing = id;
					}
				}
			}
		}

		request.send();
	},

	repeatingTaskEdit: function(id) {
		var request = new XMLHttpRequest();
		request.open("GET", "repeatingTask?id=" + id, true);
		request.setRequestHeader("Content-Type", "application/json");

		request.onreadystatechange = function() {
			if (request.readyState == 4 && request.status == 200) {
				var result = JSON.parse(request.response);
				if (result.success) {
					var modal = document.getElementById("addRepeatingTaskModal");
					if (modal) {
						modal.style.display = "block";
						document.getElementById("repeatingTaskCurrentMode").innerHTML = "editing a repeating task";

						document.getElementById("modalBackground").style.display = "block";

						document.getElementById("repeatingTaskTitle").value = result.title;
						document.getElementById("repeatingTaskDetails").value = result.details;
						document.getElementById("repeatingTaskOrigin").value = result.origin;
						document.getElementById("repeatingTaskPriority").value = result.priority;
						window.secretary.taskPriorityChange("repeating");
						if (result.priorityEscalationAfterDays == null) {
							document.getElementById("repeatingTaskPriorityEscalationAfterDays").value = "never";
						} else {
							document.getElementById("repeatingTaskPriorityEscalationAfterDays").value = result.priorityEscalationAfterDays;
						}
						document.getElementById("repeatingTaskDuration").value = result.duration;
						document.getElementById("repeatingTaskScheduleXDayOfMonth").value = result.xDayOfMonth;
						document.getElementById("repeatingTaskScheduleDay").value = result.day;
						document.getElementById("repeatingTaskScheduleMonths").value = result.months;
						document.getElementById("repeatingTaskScheduleYears").value = result.years;
						if (result.showAsScheduled == null) {
							result.showAsScheduled = true;
						}
						document.getElementById("repeatingTaskShowAsScheduled").checked = result.showAsScheduled;
						if (result.autoCleanTask == null) {
							result.autoCleanTask = false;
						}
						document.getElementById("repeatingTaskAutoCleanTask").checked = result.autoCleanTask;
						if (result.biweeklyEven == null) {
							result.biweeklyEven = false;
						}
						document.getElementById("repeatingTaskBiweeklyEven").checked = result.biweeklyEven;
						if (result.biweeklyOdd == null) {
							result.biweeklyOdd = false;
						}
						document.getElementById("repeatingTaskBiweeklyOdd").checked = result.biweeklyOdd;

						window.secretary.currentlyEditingRepeating = id;
					}
				}
			}
		}

		request.send();
	},

	taskPreRelease: function(id, preReleaseForDate) {

		var request = new XMLHttpRequest();
		request.open("POST", "taskPreRelease", true);
		request.setRequestHeader("Content-Type", "application/json");

		request.onreadystatechange = function() {
			if (request.readyState == 4 && request.status == 200) {
				var result = JSON.parse(request.response);
				if (result.success) {
					window.secretary.taskEdit(result.id);
				}
			}
		}

		var data = {
			id: id,
			date: preReleaseForDate,
		};

		request.send(JSON.stringify(data));
	},

	taskDelete: function(id, title, releaseDate) {
		var modal = document.getElementById("deleteTaskModal");
		if (modal) {
			modal.style.display = "block";

			this.currentlyDeleting = [id];

			var html = "Do you really want to delete this single task instance?" +
				"<br><br>Title: " + title +
				"<br>Release date: " + releaseDate;

			document.getElementById("deleteTaskModalDeleteButton").innerHTML = "Delete";

			if (releaseDate == null) {
				html = "Do you really want to delete this repeating task?" +
					"<br><br>Title: " + title + "<br><br>Again, and I cannot stress this enough:<br><br>" +
					"This is a REPEATING PARENT task, not just one instance! Okay?<br>" +
					"So if you just want to delete a single task instance... then by all means, do cancel! ;)";
				document.getElementById("deleteTaskModalDeleteButton").innerHTML = "Delete Parent";
			}

			document.getElementById("deleteTaskModalContent").innerHTML = html;

			document.getElementById("modalBackground").style.display = "block";
		}
	},

	showMultiDeleteModal: function(id, title, releaseDate) {
		var modal = document.getElementById("deleteTaskModal");
		if (modal) {
			modal.style.display = "block";

			this.currentlyDeleting = this.selection;

			var html = "Do you really want to delete ALL the " + this.currentlyDeleting.length + " selected tasks?";

			document.getElementById("deleteTaskModalDeleteButton").innerHTML = "Delete All";

			document.getElementById("deleteTaskModalContent").innerHTML = html;

			document.getElementById("modalBackground").style.display = "block";
		}
	},

	precloseDeleteTaskModal: function() {
		var modal = document.getElementById("deleteTaskModal");
		if (modal) {
			modal.style.display = "none";
		}
	},

	closeDeleteTaskModal: function() {
		this.precloseDeleteTaskModal();

		document.getElementById("modalBackground").style.display = "none";
	},

	doDeleteTask: function() {

		this.precloseDeleteTaskModal();

		var request = new XMLHttpRequest();
		request.open("POST", "taskDelete", true);
		request.setRequestHeader("Content-Type", "application/json");

		request.onreadystatechange = function() {
			if (request.readyState == 4 && request.status == 200) {
				var result = JSON.parse(request.response);
				if (!result.success) {
					alert("Deleting was unsuccessful, maybe refresh the page!");
				}
			}
		}

		var data = {
			id: secretary.currentlyDeleting,
		};

		request.send(JSON.stringify(data));

		var delIds = secretary.currentlyDeleting;

		window.setTimeout(function() {
			if (Array.isArray(delIds)) {
				for (var id of delIds) {
					secretary.removeTaskFromDOM(id);
				}
			} else {
				secretary.removeTaskFromDOM(id);
			}
			secretary.closeDeleteTaskModal();
		}, 100);

		secretary.currentlyDeleting = [];
		secretary.selection = [];
	},

	removeTaskFromDOM: function(id) {

		var el = document.getElementById("task-" + id);
		if (el) {
			el.parentNode.removeChild(el);
		}
		var el = document.getElementById("task-" + id + "-on-shortlist");
		if (el) {
			el.parentNode.removeChild(el);
		}
		var el = document.getElementById("task-" + id + "-x");
		if (el) {
			el.parentNode.removeChild(el);
		}
	},

	taskAddToShortList: function(id) {

		var request = new XMLHttpRequest();
		request.open("POST", "taskAddToShortList", true);
		request.setRequestHeader("Content-Type", "application/json");

		request.onreadystatechange = function() {
			if (request.readyState == 4 && request.status == 200) {
				var result = JSON.parse(request.response);
				if (result.success) {
					// just moving the task div up is actually perfectly enough, a full refresh is not necessary
					var newParent = document.getElementById('shortlist')
					var taskDiv = document.getElementById("task-" + id);
					if (newParent && taskDiv) {
						newParent.appendChild(taskDiv);
					} else {
						window.location.reload(false);
					}
				}
			}
		}

		var data = {
			id: id,
		};

		request.send(JSON.stringify(data));
	},

	taskRemoveFromShortList: function(id) {

		var request = new XMLHttpRequest();
		request.open("POST", "taskRemoveFromShortList", true);
		request.setRequestHeader("Content-Type", "application/json");

		request.onreadystatechange = function() {
			if (request.readyState == 4 && request.status == 200) {
				var result = JSON.parse(request.response);
				if (result.success) {
					// just moving the task div down is actually perfectly enough, a full refresh is not necessary
					var taskDiv = document.getElementById("task-" + id + "-on-shortlist");
					if (taskDiv) {
						var newParent = document.getElementById('taskContainer')
						if (newParent) {
							newParent.appendChild(taskDiv);
						} else {
							taskDiv.style.display = 'none';
						}
						window.shortlistAmount--;
						window.reevaluateShortlistAmount();
					} else {
						window.location.reload(false);
					}
				}
			}
		}

		var data = {
			id: id,
		};

		request.send(JSON.stringify(data));
	},

	taskPutOnShortListTomorrow: function(id) {

		this.tasksPutOnShortListTomorrow([id]);
	},

	tasksPutOnShortListTomorrow: function(ids) {

		var request = new XMLHttpRequest();
		request.open("POST", "tasksPutOnShortListTomorrow", true);
		request.setRequestHeader("Content-Type", "application/json");

		request.onreadystatechange = function() {
			if (request.readyState == 4 && request.status == 200) {
				var result = JSON.parse(request.response);
				if (result.success) {
					// just hiding the task div is actually perfectly enough, a full refresh is not necessary
					for (var i = 0; i < ids.length; i++) {
						var taskDiv = document.getElementById("task-" + ids[i] + "-on-shortlist");
						if (taskDiv) {
							taskDiv.style.display = 'none';
							window.shortlistAmount--;
							window.reevaluateShortlistAmount();
						} else {
							window.location.reload(false);
						}
					}
				}
			}
		}

		var data = {
			ids: ids,
		};

		request.send(JSON.stringify(data));
	},

	arrayAdd: function(arr, toAdd) {
		if (arr.indexOf(toAdd) < 0) {
			arr.push(toAdd);
		}
		return arr;
	},

	filterTasks: function() {

		// hide all tasks
		var tasks = document.getElementsByClassName("task");
		for (var i = 0; i < tasks.length; i++) {
			tasks[i].style.display = "none";
		}

		// show tasks that are filtered for by origin
		var filterTaskOrigin = document.getElementById("filterTaskOrigin");
		if (filterTaskOrigin) {
			var originFilter = filterTaskOrigin.value;
			if (originFilter == "every") {
				for (var i = 0; i < tasks.length; i++) {
					tasks[i].style.display = "block";
				}
			} else {
				if (originFilter == "work") {
					// LABEL :: TO ADD ORIGIN, LOOK HERE (for work-related ones)
					this.showTasksWithOrigin("skyhook");
					this.showTasksWithOrigin("maibornwolff");
					this.showTasksWithOrigin("egscc");
					this.showTasksWithOrigin("recoded");
					this.showTasksWithOrigin("supervisionearth");
					this.showTasksWithOrigin("gsmccc");
				} else {
					this.showTasksWithOrigin(originFilter);
				}
			}
		}

		// hide future tasks if future is not selected
		var filterTaskFuture = document.getElementById("filterTaskFuture");
		if (filterTaskFuture) {
			if (filterTaskFuture.className == "button unchecked") {
				this.hideTasksWithClassName("future-task");
			}
		}
	},

	hideTasksWithClassName: function(className) {
		var filteredTasks = document.getElementsByClassName(className);
		for (var i = 0; i < filteredTasks.length; i++) {
			filteredTasks[i].style.display = "none";
		}
	},

	showTasksWithOrigin: function(origin) {
		var filteredTasks = document.getElementsByClassName("task-with-origin-" + origin);
		for (var i = 0; i < filteredTasks.length; i++) {
			filteredTasks[i].style.display = "block";
		}
	},

	toggleTaskSorting: function() {
		var sortTasks = document.getElementById("sortTasks");
		if (sortTasks) {
			if (sortTasks.innerHTML.indexOf("Date") >= 0) {
				window.location = "/";
			} else {
				window.location = "/?sortby=date";
			}
		}
	},

	toggleTaskFutureView: function() {
		var filterTaskFuture = document.getElementById("filterTaskFuture");
		if (filterTaskFuture) {
			if (filterTaskFuture.className == "button unchecked") {
				filterTaskFuture.className = "button checked";
			} else {
				filterTaskFuture.className = "button unchecked";
			}
		}
		this.filterTasks();
	},

	taskPriorityChange: function(which) {
		var prio = document.getElementById(which + "TaskPriority").value;

		if (prio < 100000) {
			document.getElementById(which + "TaskPriorityMaxLabel").className = "error";
			document.getElementById(which + "TaskPriorityNoneLabel").className = "error";
		} else if (prio < 360000) {
			document.getElementById(which + "TaskPriorityMaxLabel").className = "warning";
			document.getElementById(which + "TaskPriorityNoneLabel").className = "warning";
		} else {
			document.getElementById(which + "TaskPriorityMaxLabel").className = "";
			document.getElementById(which + "TaskPriorityNoneLabel").className = "";
		}
	},

	removeLinebreaksInInbox: function() {

		var inboxArea = document.getElementById("inboxArea");

		if (inboxArea) {
			var start = inboxArea.selectionStart;
			var end = inboxArea.selectionEnd;
			var val = inboxArea.value;
			var sel = val.substring(start, end);
			var sels = sel.split("\n");
			var last = null;
			var newSels = [];
			for (var i = 0; i < sels.length; i++) {
				var cur = sels[i].trim();
				if (last === null) {
					last = cur;
				} else {
					if ((cur.indexOf("* ") == 0) || (cur.indexOf("O ") == 0) || (cur.indexOf("- ") == 0) || (cur.indexOf(">") == 0)) {
						newSels.push(last);
						last = cur;
					} else {
						last += " " + cur;
					}
				}
			}
			if (last !== null) {
				newSels.push(last);
			}
			sel = newSels.join("\n");
			var presel = "";
			var postsel = "";
			while (sel[0] === ' ') {
				sel = sel.substring(1);
				presel += "\n";
			}
			while (sel[sel.length - 1] === ' ') {
				sel = sel.substring(0, sel.length - 1);
				postsel += "\n";
			}
			inboxArea.value = val.substring(0, start) + presel + sel + postsel + val.substring(end);
		}

		window.dirtify();
	},

	convertSelectionIntoTask: function() {
		this.showAddSingleTaskModal();

		var inboxArea = document.getElementById("inboxArea");

		if (inboxArea) {
			var start = inboxArea.selectionStart;
			var end = inboxArea.selectionEnd;
			var val = inboxArea.value;
			var sel = val.substring(start, end).trim();
			var newTitle = sel;
			var newDetails = "";
			if (sel.indexOf("\n") >= 0) {
				newTitle = sel.substring(0, sel.indexOf("\n"));
				newDetails = sel.substring(sel.indexOf("\n") + 1);
			}
			newTitle = newTitle.trim();
			while (newTitle.indexOf("*") == 0) {
				newTitle = newTitle.substring(1);
			}
			newTitle = newTitle.trim();
			document.getElementById("singleTaskTitle").value = newTitle;
			document.getElementById("singleTaskDetails").value = newDetails;
			inboxArea.value = val.substring(0, start) + val.substring(end);
		}

		window.dirtify();
	},

	// select a task on the shortlist
	taskSelect: function(id, overrideToSelect, overrideToUnSelect, clickEvent) {
		if (clickEvent) {
			if (clickEvent.ctrlKey || clickEvent.shiftKey) {
				if (clickEvent.ctrlKey) {
					overrideToUnSelect = true;
				}
				if (clickEvent.shiftKey) {
					overrideToSelect = true;
				}
				if (this.lastClickedTaskId != null) {
					var lastEl = document.getElementById("select-task-" + this.lastClickedTaskId + "-on-shortlist");
					var allTaskDivs = lastEl.parentElement.parentElement.childNodes;
					var started = false;
					var breakafter = false;
					for (var i = 0; i < allTaskDivs.length; i++) {
						if (!allTaskDivs[i].id) {
							continue;
						}
						if ((allTaskDivs[i].id == "task-" + id + "-on-shortlist") ||
							(allTaskDivs[i].id == "task-" + this.lastClickedTaskId + "-on-shortlist")) {
							if (started) {
								breakafter = true;
							}
							started = true;
						}
						if (started) {
							if (allTaskDivs[i].id.startsWith("task-") && allTaskDivs[i].id.endsWith("-on-shortlist")) {
								var curId = allTaskDivs[i].id.substring(5);
								curId = curId.substring(0, curId.length - 13);
								this.taskSelect(curId, overrideToSelect, overrideToUnSelect, null);
							}
						}
						if (breakafter) {
							break;
						}
					}
				}
			}
			this.lastClickedTaskId = id;
		}

		var doSelectVisually = true;
		var index = this.selection.indexOf(id);
		if (index < 0) {
			// if we override to always unselect, and this is already selected, just do nothing!
			if (!overrideToUnSelect) {
				this.selection.push(id);
				doSelectVisually = true;
			} else {
				doSelectVisually = false;
			}
		} else {
			// if we override to always select, and this is already selected, just do nothing!
			if (!overrideToSelect) {
				this.selection.splice(index, 1);
				doSelectVisually = false;
			} else {
				doSelectVisually = true;
			}
		}

		var el = document.getElementById("select-task-" + id + "-on-shortlist");
		if (el) {
			if (doSelectVisually) {
				el.innerHTML = "[X]";
				el.parentElement.className = "line highlight";
			} else {
				el.innerHTML = "[&nbsp;&nbsp;]";
				el.parentElement.className = "line";
			}
		}
	},

	// move a done task up in the task log
	taskUp: function(id) {
		this.taskUpDown(id, 1);
	},

	// move a done task down in the task log
	taskDown: function(id) {
		this.taskUpDown(id, -1);
	},

	taskUpDown: function(id, direction) {

		var request = new XMLHttpRequest();
		request.open("POST", "taskUpDown", true);
		request.setRequestHeader("Content-Type", "application/json");

		var outer = this;
		request.onreadystatechange = function() {
			if (request.readyState == 4 && request.status == 200) {
				var result = JSON.parse(request.response);
				if (result.success) {
					if (!outer.taskUpDownCache[id]) {
						outer.taskUpDownCache[id] = 0;
					}
					outer.taskUpDownCache[id] = outer.taskUpDownCache[id] + direction;

					if (outer.taskUpDownCache[id] > 0) {
						document.getElementById("task-up-" + id).innerHTML = "" + outer.taskUpDownCache[id];
					} else {
						document.getElementById("task-up-" + id).innerHTML = "/\\";
					}

					if (outer.taskUpDownCache[id] < 0) {
						document.getElementById("task-down-" + id).innerHTML = "" + (-outer.taskUpDownCache[id]);
					} else {
						document.getElementById("task-down-" + id).innerHTML = "\\/";
					}
				}
			}
		}

		var data = {
			id: id,
			direction: direction,
		};

		request.send(JSON.stringify(data));
	},

	openInNewTab: function(url) {
		window.open(url, '_blank');
	},

	copyToClipboard: function(content) {
		var clipboardHelper = document.getElementById("clipboardHelper");
		clipboardHelper.style.display = 'inline';
		clipboardHelper.value = content;
		clipboardHelper.select();
		clipboardHelper.setSelectionRange(0, 99999);
		navigator.clipboard.writeText(clipboardHelper.value);
		clipboardHelper.style.display = 'none';
	},

	copyShortlistText: function() {
		var text = "";
		var sep = "";
		var tasks = document.getElementById("shortlist").childNodes;
		for (var i = 0; i < tasks.length; i++) {
			var task = tasks[i];
			if ((task.id) && (task.id.indexOf("task-") == 0)) {
				text += sep;
				sep = "\n";
				text += task.childNodes[2].innerText + " " + task.childNodes[3].innerText;
			}
		}
		this.copyToClipboard(text);
	},

	setSiDur: function(newVal) {
		document.getElementById('singleTaskDuration').value = newVal;
	},

	setRepDur: function(newVal) {
		document.getElementById('repeatingTaskDuration').value = newVal;
	},

	selectDuplicateTasks: function() {
		var lastText = "";
		var lastId = "";
		var tasks = document.getElementById("shortlist").childNodes;
		for (var i = 0; i < tasks.length; i++) {
			var task = tasks[i];
			if ((task.id) && (task.id.indexOf("task-") == 0)) {
				var curText = task.childNodes[3].innerText;
				if (lastText === curText) {
					var actualId = lastId.replaceAll("task-", "").replaceAll("-on-shortlist", "");
					var overrideToSelect = true;
					var overrideToUnSelect = false;
					var event = null;
					this.taskSelect(actualId, overrideToSelect, overrideToUnSelect, event);
				}
				lastId = task.id;
				lastText = curText;
			}
		}
	},

}



// disable any hotkeys while the secretary is open, so that the user cannot
// accidentally refresh the page or something silly like that
window.onhelp = function() {
	// prevent F1 function key
	return false;
};
window.onkeydown = function(event) {
	// [Ctrl]+[S]
	if ((event.metaKey || event.ctrlKey) && event.keyCode == 83) {
		var addSingleTaskModal = document.getElementById("addSingleTaskModal");
		if (addSingleTaskModal && (addSingleTaskModal.style.display === "block")) {
			window.secretary.submitSingleTaskModal(false);
		} else {
			var addRepeatingTaskModal = document.getElementById("addRepeatingTaskModal");
			if (addRepeatingTaskModal && (addRepeatingTaskModal.style.display === "block")) {
				window.secretary.submitRepeatingTaskModal(false);
			} else {
				var inboxArea = document.getElementById("inboxArea");
				if (inboxArea) {
					window.saveDirtyInbox();
				}
			}
		}
		// prevent [Ctrl]+[S]
		event.preventDefault();
		return false;
	}

	// [Ctrl]+[D] for leave editing mode after saving
	if ((event.metaKey || event.ctrlKey) && event.keyCode == 68) {
		var addSingleTaskModal = document.getElementById("addSingleTaskModal");
		if (addSingleTaskModal && (addSingleTaskModal.style.display === "block")) {
			window.secretary.closeSingleTaskModal();
		} else {
			var addRepeatingTaskModal = document.getElementById("addRepeatingTaskModal");
			if (addRepeatingTaskModal && (addRepeatingTaskModal.style.display === "block")) {
				window.secretary.closeRepeatingTaskModal();
			} else {
				var inboxArea = document.getElementById("inboxArea");
				if (inboxArea) {
					// if we are on the inbox and want to "close" it we basically just return to main view
					window.location = "/";
				}
			}
		}
		event.preventDefault();
		return false;
	}

	// [Entf] / [Del] key to delete multiple entries
	if (event.keyCode == 46) {
		if (window.secretary.selection.length > 0) {
			window.secretary.showMultiDeleteModal();
		}
	}

	if (event.keyCode == 27) {
		// prevent escape
		event.preventDefault();
		return false;
	}

	var textarea = document.activeElement;
	if (textarea == null) {
		return;
	}

	if ((textarea.tagName.toUpperCase() != "TEXTAREA") &&
		(textarea.tagName.toUpperCase() != "INPUT")) {
		return;
	}

	// function keys in general
	if ((event.keyCode > 111) && (event.keyCode < 124)) {

		// [F1] to add „“
		if (event.keyCode == 111 + 1) {
			toolbox.utils.StrUtils.insertText(textarea, "„“", event);
		}

		// [F2] to add “”
		if (event.keyCode == 111 + 2) {
			toolbox.utils.StrUtils.insertText(textarea, "“”", event);
		}

		// [F3] to add ‚‘
		if (event.keyCode == 111 + 3) {
			toolbox.utils.StrUtils.insertText(textarea, "‚‘", event);
		}

		// [F4] to add ‘’
		if (event.keyCode == 111 + 4) {
			toolbox.utils.StrUtils.insertText(textarea, "‘’", event);
		}

		// [F5] to add ’ (as that is useful more often than ‘’)
		if (event.keyCode == 111 + 5) {
			toolbox.utils.StrUtils.insertText(textarea, "’", event);
		}

		// [F6] to add a date-time-stamp
		if (event.keyCode == 111 + 6) {
			toolbox.utils.StrUtils.addDateTimeStamp(textarea, event);
		}

		// prevent function keys
		event.preventDefault();
		return false;
	}

	// [Tab] to indent or unindent selection
	if (event.keyCode == 9) {
		toolbox.utils.StrUtils.indentOrUnindent(textarea, event);
		event.preventDefault();
		return false;
	}

	// allow other keys
	return true;
};



window.addEventListener("resize", window.secretary.onResize);


window.secretary.onResize();

window.secretary.filterTasks();


// every 30 seconds, update the clock time (including the date, as it might have changed!)
window.setInterval(function() {
	var dateTimeEl = document.getElementById("curdatetime");
	var tomorrowEl = document.getElementById("tomorrowdate");
	var sleepStrEl = document.getElementById("cursleepstr");
	if (toolbox) {
		var DateUtils = toolbox.utils.DateUtils;
		var StrUtils = toolbox.utils.StrUtils;
		if (DateUtils && StrUtils) {
			var now = DateUtils.now();
			if (dateTimeEl) {
				dateTimeEl.innerHTML = DateUtils.getDayOfWeekNameEN(now) + " the " +
					StrUtils.replaceAll(DateUtils.serializeDateTimeLong(now, "<span class='sup'>", "</span>"), ", ", " and it is ");
			}
			if (tomorrowEl && StrUtils) {
				var tomorrow = DateUtils.addDays(now, 1);
				tomorrowEl.innerHTML = DateUtils.getDayOfWeekNameEN(tomorrow) + " the " +
					DateUtils.serializeDateLong(tomorrow, "<span class='sup'>", "</span>");
			}
			if (sleepStrEl) {
				var hour = DateUtils.getHour(now);
				var sleepStr = "";
				if ((hour >= 3) && (hour < 7)) {
					sleepStr = "Time to sleep! ";
				}
				sleepStrEl.innerHTML = sleepStr;
			}
		}
	}
}, 30000);

/* this ended up not being helpful, as the string serialization of e is utterly meaningless... xD
window.addEventListener("error", function(e) {
	alert("Encountered an error: " + e);
}, true);
*/
