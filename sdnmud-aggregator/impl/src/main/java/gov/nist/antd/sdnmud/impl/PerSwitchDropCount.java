package gov.nist.antd.sdnmud.impl;

import java.util.HashMap;

class PerSwitchDropCount {
	private HashMap<String, Integer> modelDropCount = new HashMap<>();
	private HashMap<String, Integer> localNetworksDropCount = new HashMap<>();
	private HashMap<String, Integer> blockedDropCount = new HashMap<>();

	private void incrementDropCount(HashMap<String, Integer> dropCountTable, String mudUrl) {
		Integer dc = dropCountTable.get(mudUrl);
		if (dc == null) {
			dc = Integer.valueOf(0);
			dropCountTable.put(mudUrl, dc);
		}
		dc = Integer.valueOf(dc.intValue() + 1);
		dropCountTable.put(mudUrl, dc);
	}

	void incrementModelDropCount(String mudUrl) {
		incrementDropCount(modelDropCount, mudUrl);
	}

	void incrementLocalNetworksDropCount(String mudUrl) {
		incrementDropCount(localNetworksDropCount, mudUrl);
	}

	void incrementBlockedDropCount(String mudUrl) {
		incrementDropCount(blockedDropCount, mudUrl);
	}
}

