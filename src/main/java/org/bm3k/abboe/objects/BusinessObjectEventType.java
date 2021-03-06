package org.bm3k.abboe.objects;

import java.util.HashMap;
import java.util.Map;

public enum BusinessObjectEventType {
    ERROR("error"), 
    /** Sent to clients when ABBOE is about to shut down connection to client */
    ABBOE_CLOSE_NOTIFY("abboe/close/notify"),
    /** Sent to clients when ABBOE is about to shut down */
    ABBOE_SHUTDOWN_NOTIFY("abboe/shutdown/notify"),    
    SERVICES_REQUEST("services/request"),
    SERVICES_REPLY("services/reply"),
    SERVICES_REGISTER("services/register"),
    SERVICES_REGISTER_REPLY("services/register/reply"), // todo: is this really needed...
    SERVICES_UNREGISTER("services/unregister"),
    SERVICES_LIST("services/list"),
    SERVICES_STATE_CHANGED("services/state-change"),
    ROUTING_SUBSCRIPTION("routing/subscribe"),
    ROUTING_SUBSCRIBE_REPLY("routing/subscribe/reply"),
    ROUTING_SUBSCRIBE_NOTIFICATION("routing/subscribe/notification"),
    ROUTING_DISCONNECT("routing/disconnect"),
    /** set a (client-specific) property at the server*/
    SET_PROPERTY("set-property"),
    ///** "deprecated */
    // CLIENTS_REGISTER_REPLY("clients/register/reply"),
    // /** "deprecated */
    // CLIENTS_LIST_REPLY("clients/list/reply"),    
    PING("ping"),
    PONG("pong"),
    DUMMY("haba3000"); // for testing purposes
    
    private static Map<String, BusinessObjectEventType> typeByName;
    private String typeString;  
    
    static {
        typeByName = new HashMap<String, BusinessObjectEventType>();
        for (BusinessObjectEventType type: values()) {
            typeByName.put(type.typeString, type);
        }
    }
    
    private BusinessObjectEventType(String typeString) {
        this.typeString = typeString;        
    }
    
    public static BusinessObjectEventType getType(String name) {
        return typeByName.get(name);
    }
    
    public String getEventName() {
        return typeString;
    }
    
   
   /** 
    * Note that the actual names are accessed via {@link #toString()}, not via {@link #name()}, which     
    * is final in java's enum class, and returns the name of the actual java language enum constant object,
    * which naturally cannot be same as the actual mime type string.
    * 
    * We remind the reader that using toString for such a business-critical purpose is against normal leronen policies, but.
    */  
    public String toString() {
        return this.typeString;
    }
}
