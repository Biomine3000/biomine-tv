package org.bm3k.abboe.common;


import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.json.JSONObject;

public class ServerAddress implements IServerAddress {
        
	public static final ServerAddress DEFAULT_SERVER = 
			new ServerAddress("localhost", Biomine3000Constants.DEFAULT_ABBOE_PORT, "localhost-abboe", null);	

	private final String name; 
    private final String host;
    private final int port;
    private final String impl;    
    
    public int getPort() {
        return port;
    }
    
    public String getHost() {    	
    	return host;    	
    }
    
    /** just a name; no function */
    public String getName() {
    	return name;
    }
    
    /** Why not? */
    public String getImpl() {
    	return impl;
    }
    
    @Override
    public String toString() {
        return getName()+" ("+getHost()+":"+getPort()+")";
    }
        
    
    public ServerAddress(String host,int port, String name, String impl) {
        this.host = host;
        this.port = port;
        this.name = name;
        this.impl = impl;
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
    
    @Override
    public int hashCode() {        
        return HashCodeBuilder.reflectionHashCode(this);
    }        
    
    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }
        
    
}
