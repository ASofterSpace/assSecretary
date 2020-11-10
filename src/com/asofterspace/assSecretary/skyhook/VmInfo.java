/**
 * Unlicensed code created by A Softer Space, 2020
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.skyhook;


/**
 * Some information about the VMs which we are monitoring for skyhook
 */
public class VmInfo {

	private String dfDb;
	private String dfF1;
	private String dfF2;


	public VmInfo() {
		String defaultVal = "<span class='warning'>Has not yet responded</span>";
		dfDb = defaultVal;
		dfF1 = defaultVal;
		dfF2 = defaultVal;
	}

	public String getDfDb() {
		return dfDb;
	}

	public void setDfDb(String dfDb) {
		this.dfDb = dfDb;
	}

	public String getDfF1() {
		return dfF1;
	}

	public void setDfF1(String dfF1) {
		this.dfF1 = dfF1;
	}

	public String getDfF2() {
		return dfF2;
	}

	public void setDfF2(String dfF2) {
		this.dfF2 = dfF2;
	}

}
