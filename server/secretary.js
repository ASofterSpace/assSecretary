window.secretary = {

	currentlyEditing: null,

	currentlyDeleting: null,


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

			document.getElementById("doneAndCopySingleTaskModalBtn").style.display = "none";

			this.currentlyEditing = null;

			document.getElementById("modalBackground").style.display = "block";

			document.getElementById("singleTaskReleaseUntil").value = "";
		}
	},

	showAddRepeatingTaskModal: function() {
		alert("Sorry, this is not yet implemented!");
	},

	submitAndCloseSingleTaskModal: function() {
		this.submitSingleTaskModal(true);
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
			origin: document.getElementById("singleTaskOrigin").value,
			priority: document.getElementById("singleTaskPriority").value,
			priorityEscalationAfterDays: document.getElementById("singleTaskPriorityEscalationAfterDays").value,
			duration: document.getElementById("singleTaskDuration").value,
			releaseUntil: document.getElementById("singleTaskReleaseUntil").value,
		};
	},

	doneAndCopySingleTaskModal: function() {

		var request = new XMLHttpRequest();
		request.open("POST", "doneAndCopySingleTask", true);
		request.setRequestHeader("Content-Type", "application/json");

		request.onreadystatechange = function() {
			if (request.readyState == 4 && request.status == 200) {
				var result = JSON.parse(request.response);
				// show some sort of confirmation
				if (result.success) {
					document.getElementById("singleTaskReleaseUntil").value = "";
					var singleTaskSavedLabel = document.getElementById("singleTaskSavedLabel");
					if (singleTaskSavedLabel) {
						singleTaskSavedLabel.style.display = "block";
						window.setTimeout(function () {
							singleTaskSavedLabel.style.display = "none";
						}, 3000);
						window.secretary.currentlyEditing = result.newId;
						document.getElementById("singleTaskReleaseDate").value = result.newReleaseDate;
					}
				}
			}
		}

		var data = this.gatherDataForSingleTaskSubmit();

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

						document.getElementById("modalBackground").style.display = "block";

						document.getElementById("singleTaskTitle").value = result.title;
						document.getElementById("singleTaskDetails").value = result.details;
						document.getElementById("singleTaskReleaseDate").value = result.releaseDate;
						document.getElementById("singleTaskOrigin").value = result.origin;
						document.getElementById("singleTaskPriority").value = result.priority;
						window.secretary.singleTaskPriorityChange();
						if (result.priorityEscalationAfterDays == null) {
							document.getElementById("singleTaskPriorityEscalationAfterDays").value = "never";
						} else {
							document.getElementById("singleTaskPriorityEscalationAfterDays").value = result.priorityEscalationAfterDays;
						}
						document.getElementById("singleTaskDuration").value = result.duration;
						document.getElementById("singleTaskReleaseUntil").value = "";

						document.getElementById("doneAndCopySingleTaskModalBtn").style.display = "inline";

						window.secretary.currentlyEditing = id;
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

	taskDelete: function(id, title) {
		var modal = document.getElementById("deleteTaskModal");
		if (modal) {
			modal.style.display = "block";

			this.currentlyDeleting = id;

			document.getElementById("deleteTaskModalContent").innerHTML = "Do you really want to delete this task?" +
				"<br><br>Title: " + title;

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

	singleTaskPriorityChange: function() {
		var prio = document.getElementById("singleTaskPriority").value;

		if (prio < 100000) {
			document.getElementById("singleTaskPriorityMaxLabel").className = "error";
			document.getElementById("singleTaskPriorityNoneLabel").className = "error";
		} else if (prio < 360000) {
			document.getElementById("singleTaskPriorityMaxLabel").className = "warning";
			document.getElementById("singleTaskPriorityNoneLabel").className = "warning";
		} else {
			document.getElementById("singleTaskPriorityMaxLabel").className = "";
			document.getElementById("singleTaskPriorityNoneLabel").className = "";
		}
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
	},

}


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
