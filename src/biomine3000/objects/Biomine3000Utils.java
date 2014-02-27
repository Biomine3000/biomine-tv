package biomine3000.objects;

import static biomine3000.objects.Biomine3000Constants.*;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Calendar;

import org.json.JSONException;

import util.CmdLineArgs2;
import util.CmdLineArgs2.IllegalArgumentsException;
import util.RandUtils;
import util.dbg.ILogger;
import util.dbg.Logger;

public class Biomine3000Utils {
    
    /** Return null if host name for some reason could not be resolved */
    public static String getHostName()  {
        try {
            return InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException e) {
            return null;
        }
    }
    
    /** Format a business object in an IRC-like fashion */
    public static String formatBusinessObject(BusinessObject bo) {
        String sender = bo.getMetaData().getSender();            
        String channel = bo.getMetaData().getChannel();
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
    
    /**
     * Give a sensible port for a server, based on the host name.
     * Does not do any managering of ports, just assume there is one specific reserved port
     * on some hosts. If no such port is defined for the present host, return null.    
     */
    public static Integer conjurePortByHostName() {
        if (atLakka()) {
            return Biomine3000Constants.LERONEN_KAPSI_PORT_1;
        }
        else if (atVoodoomasiina()) {
            return Biomine3000Constants.LERONEN_HIMA_PORT;
        }
        else {
            return null;
        }
    }
    
    private static Socket connect(InetSocketAddress addr) throws IOException {
        Socket socket = new Socket();                  
        socket.connect(addr, DEFAULT_SERVER_CONNECT_TIMEOUT_MILLIS);        
        return socket;              
    }
    
    /** 
     * On failure, just throw an IOException corresponding to the failure to 
     * connect to the last of the tried addresses.
     */
    public static Socket connectToBestAvailableServer(ILogger log) throws IOException {
        IOException lastEx = null;
        for (ServerAddress addr: ServerAddress.values()) {
            try {
                log.info("Connecting to server: "+addr);
                Socket socket = connectToServer(addr.getHost(), addr.getPort(), log);
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
            Logger.setLogLevel(loglevel);
        }        
        
        String logfile = args.get("logfile");
        if (logfile != null) {
            Logger.info("Writing log to file: "+logfile);
            Logger.addStream(logfile, Logger.LOGLEVEL_INFO);
        }
        
        String warningfile = args.get("warningfile");
        if (warningfile != null) {
            Logger.info("Writing warnings to file: "+warningfile);
            Logger.addStream(warningfile, Logger.LOGLEVEL_WARNING);
        }
    }
    
    /**
     * Socket and host may be null, in which case we shall try all known server locations. 
     * Connection timeout shall be somewhat smaller than the default one.
     * 
     * Always return a non-null connection when no exception is thrown.
     * 
     * Use default logger (util.dbg.Logger.ILoggerAdapter)
     * 
     */
    public static Socket connectToServer(String host, Integer port) throws IOException {
        return connectToServer(host, port, null); 
    }
    
    /**
     * Socket and host may be null, in which case we shall try all known server locations. 
     * Connection timeout shall be somewhat smaller than the default one.
     * 
     * Always return a non-null connection when no exception is thrown.
     */
    public static Socket connectToServer(String host, Integer port, ILogger log) throws IOException {
        if (log == null) {
            log = new Logger.ILoggerAdapter();
        }
        
        if (host != null && port != null) {
            // connect to a well-defined server
            return connect(new InetSocketAddress(host, port));
        }
        else if (host == null && port == null) {
            // no port or host, try default servers.
            return connectToBestAvailableServer(log);
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
    }
    
    public static BusinessObject makeRegisterPacket(String clientName,
                                                    ClientReceiveMode receiveMode) {
        return makeRegisterPacket(clientName, receiveMode, null);
        
    }
    
    public static BusinessObject makeRegisterPacket(ClientParameters clientParams) {
        return makeRegisterPacket(clientParams.name, clientParams.receiveMode, clientParams.subscriptions);
    }
    
    
    /**
     * Make a register packet to be sent to the server,
     * e.g.: <pre>
     *   "event": "client/register",
     *   "receive: "only_events"
     * </pre>
     * @param clientName
     * @param receiveMode 
     */
    public static BusinessObject makeRegisterPacket(String clientName,
                                                    ClientReceiveMode receiveMode,
                                                    Subscriptions subscriptions) {
        BusinessObjectMetadata meta = new BusinessObjectMetadata();        
        meta.setEvent(BusinessObjectEventType.CLIENTS_REGISTER);        
        meta.put("name", clientName);
        meta.put(ClientReceiveMode.KEY, receiveMode.toString());
        if (subscriptions != null) {
            try {
                meta.setSubsciptions(subscriptions);
            }
            catch (JSONException e) {
                throw new RuntimeException("Should not be possible");
            }
        }
        String user = getUser();
        if (user != null) {
            meta.setUser(user);
        }
        
        return new BusinessObject(meta);
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
