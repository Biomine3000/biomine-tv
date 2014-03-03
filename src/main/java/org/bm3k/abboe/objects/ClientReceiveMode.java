package org.bm3k.abboe.objects;

import java.util.HashMap;
import java.util.Map;

/** Used to tell to the sender which stuff a client want to receive */
public enum ClientReceiveMode {
    /** All objects */
    ALL("all"),
    /** Only events */
    EVENTS_ONLY("events_only"),
    /** All but own objects */    
    NO_ECHO("no_echo"),
    /** Nothing. TBD: should server close socket's outputstream? */
    NONE("none");
    
    private static Map<String, ClientReceiveMode> modeByName;
    private String modeString;  
    // applicable only in the context of an "client/register" packet
    public static String KEY = "receive";
    
    static {
        modeByName = new HashMap<String, ClientReceiveMode>();
        for (ClientReceiveMode mode: values()) {
            modeByName.put(mode.modeString, mode);
        }
    }
    
    private ClientReceiveMode(String modeString) {
        this.modeString = modeString;        
    }
    
    public static ClientReceiveMode getMode(String name) {
        return modeByName.get(name);
    }
    
    /** 
     * Note that the actual names are accessed via {@link #toString()}, not via {@link #name()}, which     
     * is final in java's enum class, and returns the name of the actual java language enum constant object,
     * which naturally cannot be same as the actual mime type string.
     * 
     * We remind the reader that using toString for such a business-critical purpose is against normal leronen policies, but.
     */  
     public String toString() {
         return this.modeString;
     }

}
