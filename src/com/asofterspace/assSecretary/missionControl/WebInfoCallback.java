/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.missionControl;

import com.asofterspace.toolbox.utils.DateUtils;
import com.asofterspace.toolbox.web.WebAccessedCallback;


public class WebInfoCallback implements WebAccessedCallback {

	private WebInfo webInfo;

	private String origin;

	private String which;

	private MissionControlDatabase missionControlDatabase;


	public WebInfoCallback(WebInfo webInfo, String origin, String which,
		MissionControlDatabase missionControlDatabase) {

		this.webInfo = webInfo;
		this.origin = origin;
		this.which = which;
		this.missionControlDatabase = missionControlDatabase;
	}

	/**
	 * An error occurred during retrieval
	 */
	public void gotError() {
		// do nothing!
	}

	/**
	 * The requested content has been retrieved
	 * @param content  The content that was requested
	 */
	public void gotContent(String content) {
		// do nothing!
	}

	/**
	 * We got a response code from the web resource
	 */
	public void gotResponseCode(Integer code) {

		missionControlDatabase.addWebpageDatapoint(DateUtils.now(), this.origin, this.which, code);

		String result = "";
		if (code == null) {
			result += "<span class='error'>Responded with nonsense!</span>";
		} else {
			if ((code == 200) || (code == 204)) {
				result += "<span class='awesome'>";
			} else {
				if ((code >= 200) && (code < 300)) {
					result += "<span>";
				} else {
					if ((code >= 300) && (code < 400)) {
						result += "<span class='warning'>";
					} else {
						result += "<span class='error'>";
					}
				}
			}
			result += "HTTP " + code + " response";
			result += "</span>";
		}

		webInfo.set(which, result);
	}
}
