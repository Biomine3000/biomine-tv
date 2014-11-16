package org.bm3k.abboe.common;

import static org.bm3k.abboe.common.Biomine3000Constants.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

import org.bm3k.abboe.objects.BusinessObject;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.net.MediaType;

import util.CmdLineArgs2;
import util.CmdLineArgs2.IllegalArgumentsException;
import util.IOUtils;
import util.RandUtils;

public class Biomine3000Utils {
    private static final Logger log = LoggerFactory.getLogger(Biomine3000Utils.class);
    private static final Random random = new Random("Hoff".hashCode()+System.nanoTime());
    public static final byte[] NULL_BYTE_ARRAY;
    
    static {
        NULL_BYTE_ARRAY = new byte[1];
        NULL_BYTE_ARRAY[0] = '\0';
    }
    
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
    
//    /** assume bo has natures "irc" and "message" */
//    public static String formatIrcMessage(BusinessObject bo) {
//    	String sender = bo.getMetadata().getString("sender");
//        String channel = bo.getMetadata().getString("channel");
//        DateUtils.format(sender+"@"+channel+": ")
//        channel = channel.replace("MESKW", "");
//    }
    
    public static boolean hasPlainTextPayload(BusinessObject o) {
        return o.getMetadata().hasPlainTextPayload();       
    }
    
    public static String plainTextPayload(BusinessObject o) {
        if (!(o.getMetadata().hasPlainTextPayload())) {
            throw new RuntimeException("No plain text payload: "+o);
        }               
    
        byte[] payload = o.getPayload();
        MediaType type = o.getMetadata().getOfficialType();            
        Optional<Charset> charset = type.charset();
        try {                               
            if (charset.isPresent()) {
                return new String(payload, charset.get());    
            }
            else {
                return new String(payload, "UTF-8");
            }
        }
        catch (UnsupportedEncodingException e) {
            if (charset.isPresent()) {
                throw new RuntimeException("unsupported charset: "+charset);
            }
            else {                                              
               // the unthinkable has occurred; UTF-8 not supported by this very java virtual machine instance
                throw new RuntimeException("leronen has joined facebook");
            }
        }    
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
     * Read host info from $ABBOE_SERVERS_FILE and use that to tell what server info to use on this host (host name defined by getHostName()).
     * If no such file, or server on no such host, return null. For multiple matches, return the first one.
     * 
     * @param port optional; if null, any localhost server info will do.
     */
    public static ServerAddress conjureJavaABBOEAddress(Integer port) throws IOException {
    	String host = getHostName();
    	if (host == null) {
    		return null;
    	}
    	
    	log.info("Conjuring localhost server address");     	
    	List<ServerAddress> servers =  Biomine3000Utils.readServersFromConfigFile();
    	for (ServerAddress server: servers) {
    		if  (server.getHost().startsWith(host) && server.getImpl().equals("java")) {
    		    if (port == null || port.equals(server.getPort())) {
    		        return server;
    		    }
    		}
    	}
    	
    	return null;
    }
    
    public static String generateUID() {
        return generateUID(getHostName());
    }
    
    /*
     * JWZ Algorithm (from http://www.jwz.org/doc/mid.html)
     * - Append "<".     
     * - Get the current (wall-clock) time in the highest resolution to which you have access (most systems can give it to you in milliseconds, but seconds will do);
     * - Generate 64 bits of randomness from a good, well-seeded random number generator;
     * - Convert these two numbers to base 36 (0-9 and A-Z) and append the first number, a ".", the second number, and an "@". This makes the left hand side of the message ID be only about 21 characters long.
     * - Append the FQDN of the local host, or the host name in the user's return address.
     * - Append ">".
     */
    public static String generateUID(String hostname) {
                      
        long millis = System.currentTimeMillis();
        long rand = random.nextLong();
        
        StringBuffer id = new StringBuffer();
        id.append('<');
        id.append(Long.toString(millis, 36));
        id.append('.');
        id.append(Long.toString(rand, 36));
        id.append('@');
        id.append(hostname);
        id.append('>');

        return id.toString();
    }
    
    
    private static Socket connect(InetSocketAddress addr) throws IOException {
        Socket socket = new Socket();
        log.info("Trying to connect to server at addr " + addr + " (timeout in "+DEFAULT_SERVER_CONNECT_TIMEOUT_MILLIS/1000 + " seconds)");
        try {
            socket.connect(addr, DEFAULT_SERVER_CONNECT_TIMEOUT_MILLIS);
        }
        catch (SocketTimeoutException e) {
            log.info("Connecting to server at "+addr + "timed out");
            throw e;
        }        
        catch (IOException e) {
            log.info("Connecting to server at "+addr + "threw IOException: "+e);
            throw e;
        }
                
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
