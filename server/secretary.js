window.secretary = {

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

		if (retry) {
			// if we could not fully resize now, then let's do it later...
			window.setTimeout(function() {
				window.secretary.onResize();
			}, 100);
		}
	},

}


window.addEventListener("resize", window.secretary.onResize);


window.secretary.onResize();


// every 30 seconds, update the clock time (including the date, as it might have changed!)
window.setInterval(function() {
	var dateTimeEl = document.getElementById("curdatetime");
	var DateUtils = toolbox.utils.DateUtils;
	var StrUtils = toolbox.utils.StrUtils;
	if (dateTimeEl && DateUtils && StrUtils) {
		var now = DateUtils.now();
		dateTimeEl.innerHTML = DateUtils.getDayOfWeekNameEN(now) + " the " +
			StrUtils.replaceAll(DateUtils.serializeDateTimeLong(now, "<span class='sup'>", "</span>"), ", ", " and it is ");
	}
}, 30000);
