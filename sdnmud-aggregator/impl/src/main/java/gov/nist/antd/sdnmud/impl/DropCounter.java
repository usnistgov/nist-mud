package gov.nist.antd.sdnmud.impl;

public class DropCounter {
	
	 String manufacturer;
	 String model;
	 boolean localNetworks;
	 boolean quarantined;
	 int count;
	 
	 @Override
	 public  int hashCode() {
		return String.format("%s:%s:%b:%b", manufacturer, model, localNetworks, quarantined).hashCode(); 
	 }
}
