package org.bm3k.abboe.common;

import static org.bm3k.abboe.common.Biomine3000Constants.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.bm3k.abboe.objects.BusinessObject;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.CmdLineArgs2;
import util.CmdLineArgs2.IllegalArgumentsException;
import util.IOUtils;
import util.RandUtils;

public class Biomine3000Utils {
    private static final Logger log = LoggerFactory.getLogger(Biomine3000Utils.class);
    
    /** Return null if host name for some reason could not be resolved */
    public static String getHostName()  {
        try {
            return InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException e) {
            return null;
        }
    }

    /**
     * Read known servers from file pointed by env variable ABBOE_SERVERS_FILE. If no such env var, return empty list.
     * If var exist, but does not point to an readable file, throw IOException.
     */
    public static List<ServerAddress> readServersFromConfigFile() throws IOException {
    	List<ServerAddress> servers = new ArrayList<ServerAddress>();
    	String configFileName = System.getenv(Biomine3000Constants.ENV_VAR_ABBOE_SERVERS_FILE);
    	if (configFileName != null) {     		
    		FileInputStream is = new FileInputStream(configFileName);
    		String data = IOUtils.readStream(is);
    		JSONArray jsonArr = new JSONArray(data);
    		for (int i=0; i<jsonArr.length(); i++) {
    			ServerAddress address = new ServerAddress(jsonArr.getJSONObject(i));
    			servers.add(address);
    		}
    	}
    	return servers;
    }
    
    /**
     * Format a business object in an IRC-like fashion
     *
     * // TODO: support irc nature instead of hard-coded
     */
    public static String formatBusinessObject(BusinessObject bo) {
        String sender = bo.getMetadata().getString("sender");
        String channel = bo.getMetadata().getString("channel");
        if (channel != null) {
            channel = channel.replace("MESKW", "");
        }
        String prefix;
        
        if (sender == null && channel == null) {
            // no sender, no channel
            prefix = "<anonymous>";
        }
        else if (sender != null && channel == null) {
            // only sender
            prefix = "<"+sender+">";
        }
        else if (sender == null && channel != null) {
            // only channel
            prefix = "<"+channel+">";
        }
        
        else {
            // both channel and sender
            prefix = "<"+channel+"-"+sender+">";
        }
        
        return prefix+" "+bo;

    }
    
    public static boolean atLakka() {
        String host = getHostName();
        
        if (host != null && host.startsWith("lakka")) {
            return true;
        }
        else {
            return false;
        }
    }
    
    public static boolean atMelkki() {
        String host = getHostName();
        if (host != null && host.startsWith("melkki")) {
            return true;
        }
        else {
            return false;
        }
    }
    
    public static boolean isBMZTime() {
        Calendar cal = Calendar.getInstance();
        int BMZ_ZERO_HOUR1  = 23;
        int BMZ_ZERO_HOUR2  = -1;
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        if (Math.abs(hour - BMZ_ZERO_HOUR1) <= 2                
                || Math.abs(hour - BMZ_ZERO_HOUR2) <= 2) {
            return true;
        }        
        else {
            return false;
        }
    }
    
    public static boolean atWel120() {
        String host = getHostName();
        if (host != null && host.startsWith("wel-120")) {
            return true;
        }
        else {
            return false;
        }
    }
    
    public static boolean atBC() {
        String host = getHostName();
        if (host != null && host.startsWith("xl2")) {
            return true;
        }
        else {
            return false;
        }
    }
    
    public static boolean atVoodoomasiina() {
        String host = getHostName();
        if (host != null && host.equals("voodoomasiina")) { 
            return true;
        }       
        else {
            return false;
        }        
    }
    
    public static boolean atLeronenBubuntu() {
        String host = getHostName();
        if (host != null && host.equals("leronen-bubuntu")) { 
            return true;
        }       
        else {
            return false;
        }        
    }
        
    /**
     * Read host info from the ABBOE_SERVERS_FILE and use that to tell what port is used on which host.
     * If no such file, or server on no such host, or multiple servers defined for host, return null.
     */
    public static Integer conjurePortByHostName() throws IOException {
    	String host = getHostName();
    	if (host == null) {
    		return null;
    	}
    	log.info("Conjuring port by host name @: "+host);    	
    	List<ServerAddress> servers =  Biomine3000Utils.readServersFromConfigFile();
    	for (ServerAddress server: servers) {
    		if  (server.getHost().startsWith(host)) {
    			return server.getPort();
    		}
    	}
    	
    	return null;
    }
    
    private static Socket connect(InetSocketAddress addr) throws IOException {
        Socket socket = new Socket();                  
        socket.connect(addr, DEFAULT_SERVER_CONNECT_TIMEOUT_MILLIS);        
        return socket;              
    }
    
    /** 
     * Connect to first available server of servers read from a configuration file ABBOE_SERVERS_FILE.
     * 
     * If no such configuration file, connect to server on localhost using default ABBOE port 
     * 
     * If no server available, just throw an IOException corresponding to the failure to 
     * connect to the last of the tried addresses.
     */
    public static Socket connectToFirstAvailableServer() throws IOException {
    	List<ServerAddress> servers = readServersFromConfigFile();
    	if (servers.size() == 0) {
    		servers.add(ServerAddress.DEFAULT_SERVER);
    	}
    	
    	return connectToFirstAvailableServer(servers);
    	
    }
    
    /** 
     * If no server available, just throw an IOException corresponding to the failure to 
     * connect to the last of the tried addresses.
     */
    public static Socket connectToFirstAvailableServer(List<ServerAddress> servers) throws IOException {
        IOException lastEx = null;
        for (ServerAddress addr: servers) {
            try {
                log.info("Connecting to server: "+addr);
                Socket socket = connectToServer(addr.getHost(), addr.getPort());
                log.info("Successfully connected");
                return socket;                
            }
            catch (IOException e) {
                log.info("Failed connecting to server: "+addr);
                lastEx = e;
            }
        }
        
        throw lastEx;
    }
    
    /** Trivially get USER from env */
    public static String getUser() {
        return System.getenv("USER");
    }
    
    /** 
     * Parse connection params (socket and host) from cmdline args. 
     * If no such args, we shall try all known server locations.
     *
     * Always return a non-null connection when no exception is thrown.
     */
    public static Socket connectToServer(String[] pArgs) throws IOException, IllegalArgumentsException {
        Biomine3000Args args = new Biomine3000Args(pArgs, false); 
        String host = args.getHost();
        Integer port = args.getPort();
        return connectToServer(host, port);
    }
    
    public static Socket connectToServer(Biomine3000Args args) throws IOException, IllegalArgumentsException {        
        String host = args.getHost();
        Integer port = args.getPort();
        return connectToServer(host, port);
    }
    
    public static void configureLogging(String[] pArgs) throws IOException, IllegalArgumentsException {
        configureLogging(new CmdLineArgs2(pArgs));
    }
    
    /** Set log level, log file and warning file based on args */
    public static void configureLogging(CmdLineArgs2 args) throws IOException, IllegalArgumentsException {                        
        Integer loglevel = args.getInt("loglevel");
        if (loglevel != null) {
            // Logger.info("Setting log level to: "+loglevel);
            // Logger.setLogLevel(loglevel);
            // TODO: run-time configuration
            log.warn("Command-line parameters for logging not respected.");
        }        
        
        String logfile = args.get("logfile");
        if (logfile != null) {
            // Logger.info("Writing log to file: "+logfile);
            // Logger.addStream(logfile, Logger.LOGLEVEL_INFO);
            // TODO: run-time configuration
            log.warn("Command-line parameters for logging not respected.");
        }
        
        String warningfile = args.get("warningfile");
        if (warningfile != null) {
            // Logger.info("Writing warnings to file: "+warningfile);
            // Logger.addStream(warningfile, Logger.LOGLEVEL_WARNING)
            // TODO: run-time configuration
            log.warn("Command-line parameters for logging not respected.");
        }
    }
    
    /**
     * Socket and host may be null, in which case we shall try all known server locations. 
     * Connection timeout shall be somewhat smaller than the default one.
     * 
     * Always return a non-null connection when no exception is thrown.
     */
    public static Socket connectToServer(String host, Integer port) throws IOException {
        if (host != null && port != null) {
            // connect to a well-defined server
            return connect(new InetSocketAddress(host, port));
        }
        else if (host == null && port == null) {
            // no port or host, try default servers.
            return connectToFirstAvailableServer();
        }
        else if (host == null) {
            throw new IOException("Cannot connect: host is null");
        }
        else if (port == null) {
            throw new IOException("Cannot connect: port is null");
        }        
        else {
            throw new RuntimeException("Impossible.");
        }
                
    }
    
    /** TODO! */
    public static String generateMessageId() {
        // http://www.jwz.org/doc/mid.html
        return null;
    }
    
    public static void main(String[] args) {
        System.out.println("at host: "+getHostName());
        System.out.println("available servers: "+getHostName());
    }
    
    public static File randomFile(String dirName) throws IOException {
        File dir = new File(dirName);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files.length > 0) {
                return RandUtils.sample(Arrays.asList(files));
            }
            else {
                throw new IOException("No files in dir: "+dir);
            }            
        }
        else {
            throw new IOException("No such dir: "+dir);
        }
    }
}
