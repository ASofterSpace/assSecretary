window.secretary = {

	// id of the single task we are currently editing
	currentlyEditing: null,

	// id of the repeating task we are currently editing
	currentlyEditingRepeating: null,

	// id of the single or repeating task we are currently deleting
	currentlyDeleting: null,

	// id of the repeating parent task of the single task we are currently editing
	currentlyRepeatingParent: null,


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
			document.getElementById("singleTaskCurrentMode").innerHTML = "adding new entries";

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
			document.getElementById("repeatingTaskCurrentMode").innerHTML = "adding new entries";

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
					document.getElementById("singleTaskReleaseUntil").value = "";
					if (closeOnSubmit) {
						window.secretary.closeSingleTaskModal();
					}
				}
			}
		}

		var data = this.gatherDataForSingleTaskSubmit();

		request.send(JSON.stringify(data));
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
					if (closeOnSubmit) {
						window.secretary.closeRepeatingTaskModal();
					}
				}
			}
		}

		var data = this.gatherDataForRepeatingTaskSubmit();

		request.send(JSON.stringify(data));
	},

	gatherDataForRepeatingTaskSubmit: function() {

		return {
			editingId: window.secretary.currentlyEditingRepeating,
			title: document.getElementById("repeatingTaskTitle").value,
			details: document.getElementById("repeatingTaskDetails").value,
			origin: document.getElementById("repeatingTaskOrigin").value,
			priority: document.getElementById("repeatingTaskPriority").value,
			priorityEscalationAfterDays: document.getElementById("repeatingTaskPriorityEscalationAfterDays").value,
			duration: document.getElementById("repeatingTaskDuration").value,
			day: document.getElementById("repeatingTaskScheduleDay").value,
			weekdays: document.getElementById("repeatingTaskScheduleWeekdays").value,
			months: document.getElementById("repeatingTaskScheduleMonths").value,
			years: document.getElementById("repeatingTaskScheduleYears").value,
		};
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
							window.secretary.currentlyEditing = result.newId;
							document.getElementById("singleTaskReleaseDate").value = result.newReleaseDate;
							document.getElementById("singleTaskDoneDate").value = "";
							document.getElementById("singleTaskCurrentMode").innerHTML = "editing one entry";
						}
					} else {
						window.location.reload(false);
					}
				}
			}
		}

		var data = this.gatherDataForSingleTaskSubmit();

		data.copyAfterwards = copyAfterwards;

		request.send(JSON.stringify(data));
	},

	closeSingleTaskModal: function() {
		var modal = document.getElementById("addSingleTaskModal");
		if (modal) {
			modal.style.display = "none";
		}

		// reload, as data might have changed while the modal was open...
		window.location.reload(false);
	},

	closeRepeatingTaskModal: function() {
		var modal = document.getElementById("addRepeatingTaskModal");
		if (modal) {
			modal.style.display = "none";
		}

		// reload, as data might have changed while the modal was open...
		window.location.reload(false);
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

		document.getElementById("singleTaskTitle").value = "";
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
	},

	resetRepeatingTaskModal: function() {

		document.getElementById("repeatingTaskTitle").value = "";
		document.getElementById("repeatingTaskDetails").value = "";
		document.getElementById("repeatingTaskOrigin").value = "private";
		document.getElementById("repeatingTaskPriority").value = 500000;
		window.secretary.taskPriorityChange("repeating");
		document.getElementById("repeatingTaskPriorityEscalationAfterDays").value = "never";
		document.getElementById("repeatingTaskDuration").value = "00:00";

		document.getElementById("repeatingTaskScheduleDay").value = "";
		document.getElementById("repeatingTaskScheduleWeekdays").value = "";
		document.getElementById("repeatingTaskScheduleMonths").value = "";
		document.getElementById("repeatingTaskScheduleYears").value = "";
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
						document.getElementById("singleTaskCurrentMode").innerHTML = "editing one entry";

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
						document.getElementById("repeatingTaskCurrentMode").innerHTML = "editing one entry";

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
						document.getElementById("repeatingTaskScheduleDay").value = result.day;
						document.getElementById("repeatingTaskScheduleWeekdays").value = result.weekdays;
						document.getElementById("repeatingTaskScheduleMonths").value = result.months;
						document.getElementById("repeatingTaskScheduleYears").value = result.years;

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
					window.secretary.taskEdit(result.newId);
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

			this.currentlyDeleting = id;

			var html = "Do you really want to delete this single task instance?" +
				"<br><br>Title: " + title +
				"<br>Release date: " + releaseDate;

			if (releaseDate == null) {
				html = "Do you really want to delete this repeating task?" +
					"<br><br>Title: " + title;
			}

			document.getElementById("deleteTaskModalContent").innerHTML = html;

			document.getElementById("modalBackground").style.display = "block";
		}
	},

	closeDeleteTaskModal: function() {
		var modal = document.getElementById("deleteTaskModal");
		if (modal) {
			modal.style.display = "none";

			document.getElementById("modalBackground").style.display = "none";
		}
	},

	doDeleteTask: function(id) {

		this.closeDeleteTaskModal();

		var request = new XMLHttpRequest();
		request.open("POST", "taskDelete", true);
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
			id: window.secretary.currentlyDeleting,
		};

		request.send(JSON.stringify(data));
	},

	taskAddToShortList: function(id) {

		var request = new XMLHttpRequest();
		request.open("POST", "taskAddToShortList", true);
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

	taskRemoveFromShortList: function(id) {

		var request = new XMLHttpRequest();
		request.open("POST", "taskRemoveFromShortList", true);
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

	taskPutOnShortListTomorrow: function(id) {

		var request = new XMLHttpRequest();
		request.open("POST", "taskPutOnShortListTomorrow", true);
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
					this.showTasksWithOrigin("skyhook");
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
				var filteredTasks = document.getElementsByClassName("future-task");
				for (var i = 0; i < filteredTasks.length; i++) {
					filteredTasks[i].style.display = "none";
				}
			}
		}
	},

	showTasksWithOrigin: function(origin) {
		var filteredTasks = document.getElementsByClassName("task-with-origin-" + origin);
		for (var i = 0; i < filteredTasks.length; i++) {
			filteredTasks[i].style.display = "block";
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
			var sel = val.substring(start, end);
			var newTitle = sel;
			var newDetails = "";
			if (sel.indexOf("\n") >= 0) {
				newTitle = sel.substring(0, sel.indexOf("\n"));
				newDetails = sel.substring(sel.indexOf("\n") + 1);
			}
			document.getElementById("singleTaskTitle").value = newTitle;
			document.getElementById("singleTaskDetails").value = newDetails;
			inboxArea.value = val.substring(0, start) + val.substring(end);
		}

		window.dirtify();
	},

	insertDateTimeStampIntoTextarea: function(textareaEl) {
		var start = textareaEl.selectionStart;
		var end = textareaEl.selectionEnd;
		// ... add a date-time-stamp!
		var datetimestamp = toolbox.utils.DateUtils.getCurrentDateTimeStamp();
		textareaEl.value =
			textareaEl.value.substring(0, start) +
			datetimestamp +
			textareaEl.value.substring(end);
		textareaEl.selectionStart = start + datetimestamp.length;
		textareaEl.selectionEnd = start + datetimestamp.length;
	},

	openInNewTab: function(url) {
		window.open(url, '_blank');
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
	if ((event.keyCode > 111) && (event.keyCode < 124)) {
		if (event.keyCode == 111 + 6) {
			// if [F6] is pressed, and the repeatingTaskDetails textarea is visible...
			var repeatingTaskDetails = document.getElementById("repeatingTaskDetails");
			if (repeatingTaskDetails && window.repeatingTaskDetailsHasFocus) {
				// ... add a datetimestamp!
				window.secretary.insertDateTimeStampIntoTextarea(repeatingTaskDetails);
			} else {
				// same for the single task modal :)
				var singleTaskDetails = document.getElementById("singleTaskDetails");
				if (singleTaskDetails && window.singleTaskDetailsHasFocus) {
					window.secretary.insertDateTimeStampIntoTextarea(singleTaskDetails);
				}
			}
		}
		// prevent function keys
		event.preventDefault();
		return false;
	}
	if (event.keyCode == 27) {
		// prevent escape
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
	if (toolbox) {
		var DateUtils = toolbox.utils.DateUtils;
		var StrUtils = toolbox.utils.StrUtils;
		if (dateTimeEl && DateUtils && StrUtils) {
			var now = DateUtils.now();
			dateTimeEl.innerHTML = DateUtils.getDayOfWeekNameEN(now) + " the " +
				StrUtils.replaceAll(DateUtils.serializeDateTimeLong(now, "<span class='sup'>", "</span>"), ", ", " and it is ");
		}
	}
}, 30000);
