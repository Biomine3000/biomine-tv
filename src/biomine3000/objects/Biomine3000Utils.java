package biomine3000.objects;

import static biomine3000.objects.Biomine3000Constants.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import util.dbg.ILogger;

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
    
    public static boolean atLakka() {
        String host = getHostName();
        if (host != null && host.equals("lakka")) {
            return true;
        }
        else {
            return false;
        }
    }
    
    public static boolean atBC() {
        String host = getHostName();
        if (host != null && host.equals("xl2")) {
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
    
    /**
     * Socket and host may be null, in which case we shall try all known server locations. 
     * Connection timeout shall be somewhat smaller than the default one.
     */
    public static Socket connectToServer(String host, Integer port, ILogger log) throws IOException {
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
    
    public static void main(String[] args) {
        System.out.println("at host: "+getHostName());
    }
}
