package org.bm3k.abboe.common;


import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.CollectionUtils;
import util.StringUtils;

public class ServerAddress implements IServerAddress {
    
	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(ServerAddress.class);
	
	public static final ServerAddress DEFAULT_SERVER = 
		new ServerAddress("localhost", Biomine3000Constants.DEFAULT_ABBOE_PORT, "localhost-abboe", null);	

	private final String name; 
    private final String host;
    private final int port;
    private final String impl;
    private transient String shortName;         
    
    public int getPort() {
        return port;
    }
    
    /** Can never be empty empty or null. */
    public String getHost() {    	
    	return host;    	
    }
    
    /** May or may not be used as routing id by the server. Can never be empty or null. */
    public String getName() {
    	return name;
    }
        
    public String getImpl() {
    	return impl;
    }
    
    @Override
    public String toString() {
        return getName()+" ("+getHost()+":"+getPort()+")";
    }        
    
    /** @throws RuntimeException if host or name is null or empty */    
    public ServerAddress(String host,int port, String name, String impl) {
    	if (host== null || host.length() == 0) {
    		throw new RuntimeException("Empty host name for a server address");
    	}
    	
    	if (name == null || name.length() == 0) {
    		throw new RuntimeException("Empty name for a server address");
    	}
    	
        this.host = host;
        this.port = port;
        this.name = name;
        this.impl = impl;
        this.shortName = name;               
    }
    
    public ServerAddress(JSONObject data) {
        this(data.getString("host"), data.getInt("port"), data.getString("name"), data.optString("impl", null));    	    	
    }
    
    public JSONObject toJSON() {
    	JSONObject json = new JSONObject();
    	json.put("host",  host);
    	json.put("port",  port);    	
    	json.put("name",  name);
    	if (impl != null) {
    		json.put("impl", impl);
    	}
    	return json;
    }

    public String getShortName() {
    	return shortName;
    }
    
    @Override
    public int hashCode() {        
        return HashCodeBuilder.reflectionHashCode(this);
    }        
    
    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }
        
    /**
     * Init short names for a set of peers by removing the common prefix and suffix from all names. Note that this can only be done when the
     * set of all addresses is known.
     */
    public static void initShortNames(Collection<ServerAddress> addresses) {
    	List<String> names = addresses
    		.stream()
        	.map((p) -> p.getName())
        	.collect(Collectors.toList());
        	
    	List<String> shortNameList = StringUtils.removeLongestCommonPrefix(names);
    	shortNameList = StringUtils.removeLongestCommonSuffix(shortNameList);
    	Map<ServerAddress, String> shortNames = CollectionUtils.makeMap(addresses, shortNameList);
    	// log.debug("Initialized short names: " + StringUtils.mapToString(shortNames, " => ", "; "));
    	for (ServerAddress address: addresses) {
    		address.shortName = shortNames.get(address);
    	}
    }
}
