package gov.nist.antd.sdnmud.impl;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NameResolutionCache {

	private HashMap<InstanceIdentifier<FlowCapableNode>, ArrayList<LookupEntry>> lookupCache = new HashMap<InstanceIdentifier<FlowCapableNode>, ArrayList<LookupEntry>>();
	private HashMap<String, ArrayList<Ipv4Address>> controllerNameResolutions = new HashMap<String, ArrayList<Ipv4Address>>();

	static final Logger LOG = LoggerFactory.getLogger(NameResolutionCache.class);

	class LookupEntry {

		private String domainName;
		private Ipv4Address address;

		public LookupEntry() {
			address = null;
		}

		public LookupEntry(String name, Ipv4Address ipv4Address) {
			address = ipv4Address;
			this.domainName = name;
		}

		@Override
		public boolean equals(Object other) {
			if (other instanceof LookupEntry) {
				LookupEntry otherLookupEntry = (LookupEntry) other;
				return otherLookupEntry.domainName.equals(this.domainName)
						&& otherLookupEntry.address.getValue().equals(this.address.getValue());
			} else
				return false;
		}
	}

	private ArrayList<Ipv4Address> resolveDefaultDomain(String domainName) {

		try {
			InetAddress[] inetAddresses = InetAddress.getAllByName(domainName);
			if (this.controllerNameResolutions.get(domainName) == null) {
				ArrayList<Ipv4Address> addresses = new ArrayList<Ipv4Address>();
				controllerNameResolutions.put(domainName, addresses);
			}
			ArrayList<Ipv4Address> addresses = controllerNameResolutions.get(domainName);
			for (InetAddress inetAddress : inetAddresses) {
				String hostAddress = inetAddress.getHostAddress();
				LOG.info("domainName " + domainName + "inetAddress : " + hostAddress);
				addresses.add(new Ipv4Address(hostAddress));
			}
		} catch (UnknownHostException e) {
			LOG.error("Could not resolve " + domainName);
		}
		return controllerNameResolutions.get(domainName);

	}

	public void addCacheLookup(InstanceIdentifier<FlowCapableNode> node, String name, String address) {
		LOG.info("addCacheLookup " + name + " address " + address);
		Ipv4Address ipv4Address = new Ipv4Address(address);
		if (!lookupCache.containsKey(node)) {
			ArrayList<LookupEntry> entries = new ArrayList<LookupEntry>();
			entries.add(new LookupEntry(name, ipv4Address));
			lookupCache.put(node, entries);
		}
		ArrayList<LookupEntry> entries = lookupCache.get(node);
		LookupEntry entry = new LookupEntry(name, ipv4Address);

		if (!entries.contains(entry)) {
			entries.add(entry);
		}
	}

	public List<Ipv4Address> doNameLookup(InstanceIdentifier<FlowCapableNode> node, String hostName) {
		LOG.info("doNameLookup " + IdUtils.getNodeUri(node) + " hostName " + hostName);
		ArrayList<Ipv4Address> retval = new ArrayList<Ipv4Address>();
		if (lookupCache.get(node) != null) {
			for (LookupEntry entry : lookupCache.get(node)) {
				if (entry.domainName.equals(hostName)) {
					retval.add(entry.address);
				}
			}
		}
		if (retval.isEmpty()) {
			LOG.info("doNameLookup returning default domain");
			return resolveDefaultDomain(hostName);
		} else {
			LOG.info("doNameLookup returning " + retval);
			return retval;
		}
	}

	public void removeCacheLookup(InstanceIdentifier<FlowCapableNode> node) {
		this.lookupCache.remove(node);
	}

}
