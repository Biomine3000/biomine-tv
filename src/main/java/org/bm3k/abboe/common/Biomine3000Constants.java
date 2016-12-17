package org.bm3k.abboe.common;

public class Biomine3000Constants {
    	
	/** magic number */        
    public static final int DEFAULT_ABBOE_PORT = 61910; 
    public static final String DEFAULT_ABBOE_HOST = "localhost";
    
    public static final int DEFAULT_SERVER_CONNECT_TIMEOUT_SEC = 3;
    public static final int DEFAULT_SERVER_CONNECT_TIMEOUT_MILLIS = 1000 * DEFAULT_SERVER_CONNECT_TIMEOUT_SEC;             
    
    public static final String ENV_VAR_ABBOE_SERVERS_FILE = "ABBOE_SERVERS_FILE";
    public static final String ENV_VAR_ABBOE_HOST = "ABBOE_HOST";
    public static final String ENV_VAR_ABBOE_PORT = "ABBOE_PORT";
    
    /** name of environment variable controlling peer connect retry interval in seconds */
    public static final String ENV_VAR_ABBOE_PEER_CONNECT_RETRY_INTERVAL = "ABBOE_PEER_CONNECT_RETRY_INTERVAL";
    
    /** name of environment variable controlling peer connect timeout in seconds */
    public static final String ENV_VAR_ABBOE_PEER_CONNECT_TIMEOUT = "ABBOE_PEER_CONNECT_TIMEOUT";
    
    /** default peer connect timeout in seconds */
    public static final int DEFAULT_PEER_CONNECT_TIMEOUT = 3;
    
    /** default peer connect retry interval in seconds */
    public static final int DEFAULT_PEER_CONNECT_RETRY_INTERVAL = 60;
}


