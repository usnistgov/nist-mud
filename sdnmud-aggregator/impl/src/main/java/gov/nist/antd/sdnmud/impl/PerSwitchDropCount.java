package gov.nist.antd.sdnmud.impl;

import java.util.HashMap;
import java.util.Map;

class PerSwitchDropCount {
	private HashMap<String, Integer> blockedFromDropCount = new HashMap<>();
	private HashMap<String, Integer> blockedToDropCount = new HashMap<>();
	private HashMap<String, Integer> controllerDropCount = new HashMap<>();
    private int localNetworksDropCount = 0;

	private void incrementDropCount(HashMap<String, Integer> dropCountTable, String mudUrl) {
		Integer dc = dropCountTable.get(mudUrl);
		if (dc == null) {
			dc = Integer.valueOf(0);
			dropCountTable.put(mudUrl, dc);
		}
		dc = Integer.valueOf(dc.intValue() + 1);
		dropCountTable.put(mudUrl, dc);
	}


	void incrementLocalNetworksDropCount(String mudUrl) {
		this.localNetworksDropCount ++;
	}

	void incrementBlockedFromDropCount(String mudUrl) {
		incrementDropCount(blockedFromDropCount, mudUrl);
	}
	
	void incrementBlockedToDropCount(String mudUrl) {
		incrementDropCount(blockedToDropCount, mudUrl);
	}
	
	void incrementControllerDropCount(String controllerUrl) {
		incrementDropCount(controllerDropCount, controllerUrl);
	}
	
	
	int getLocalNetworksDropCount() {
		return this.localNetworksDropCount;
	}
	
	Map<String,Integer> getBlockedFromDropCount() {
		return this.blockedFromDropCount;
	}
	
	Map<String,Integer> getBlockedToDropCount() {
		return this.blockedFromDropCount;
	}
}

