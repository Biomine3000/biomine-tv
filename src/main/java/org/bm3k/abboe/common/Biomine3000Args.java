package org.bm3k.abboe.common;

import java.io.IOException;

import util.CmdLineArgs2;

public class Biomine3000Args extends CmdLineArgs2 {
    
	private static String[] FLAGS = { "no-servers-file" }; 
	
    /** Logging configured automatically by this constructor, based on args! */
    public Biomine3000Args(String[] args) throws IllegalArgumentsException {
        super(args, FLAGS);
    }
    
    /** @param configureLogging configure logging automatically? */     
    public Biomine3000Args(String[] args, boolean configureLogging) throws IllegalArgumentsException, IOException {
        super(args, FLAGS);
        if (configureLogging) {
            Biomine3000Utils.configureLogging(this);
        }
    }
    
    /** -channel */
    public String getChannel() {
        return get("channel");
    }
    
    public boolean noServersFile() {
    	return hasFlag("no-servers-file");    			
    }
    
    /** opt -user, or env var "USER", or "anonymos"*/  
    public String getUser() {
        // try opt
        String user = get("user");
        if (user != null) {            
            return user;
        }
        
        // try env var
        user = System.getenv("USER");
        if (user != null) {            
            return user;
        }
        
        // fall back to "anonymous"
        return "anonymous";      
    }
    
    /** opt -host, or env var "ABBOE_HOST", or null */
    public String getHost() {
        String host = get("host");
        if (host == null) {
        	host = System.getenv(Biomine3000Constants.ENV_VAR_ABBOE_HOST); 
        }
        return host;
    }
    
    /** opt -port, or env var "ABBOE_PORT", or null */
    public Integer getPort() {
        Integer port = getInt("port");
        if (port == null) {
        	String portStr = System.getenv(Biomine3000Constants.ENV_VAR_ABBOE_PORT);
        	if (portStr != null) {
        		port = Integer.parseInt(portStr);
        	}       
        }
        return port;
    }
    
    /** @return null if host == || port == null */
    public ServerAddress getServerAddress() {
    	String host = getHost();
    	Integer port = getPort();
    	if (host != null && port != null) {
    		return new ServerAddress(host, port, host+":"+port, null);
    	}
    	else {
    		return null;
    	}
    			
    }
    

}

