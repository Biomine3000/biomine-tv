package biomine3000.objects;

import java.net.InetAddress;
import java.net.UnknownHostException;

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
        if (host != null && host.equals("lakka.kapsi.fi")) {
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
            return Biomine3000Constants.DEFAULT_PORT;
        }
        else {
            return null;
        }
    }
    
    public static void main(String[] args) {
        System.out.println("at host: "+getHostName());
    }
}
