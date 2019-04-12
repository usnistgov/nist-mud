// Copyright (c) 1999-2004 Brian Wellington (bwelling@xbill.org)
package gov.nist.antd.sdnmud.impl.dns;

import java.io.*;

import gov.nist.antd.sdnmud.impl.dns.utils.base16;

/**
 * An EDNSOption with no internal structure.
 * 
 * @author Ming Zhou &lt;mizhou@bnivideo.com&gt;, Beaumaris Networks
 * @author Brian Wellington
 */
public class GenericEDNSOption extends EDNSOption {

private byte [] data;

GenericEDNSOption(int code) {
	super(code);
}

/**
 * Construct a generic EDNS option.
 * @param data The contents of the option.
 */
public 
GenericEDNSOption(int code, byte [] data) {
	super(code);
	this.data = Record.checkByteArrayLength("option data", data, 0xFFFF);
}

void 
optionFromWire(DNSInput in) throws IOException {
	data = in.readByteArray();
}

void 
optionToWire(DNSOutput out) {
	out.writeByteArray(data);
}

String 
optionToString() {
	return "<" + base16.toString(data) + ">";
}

}
