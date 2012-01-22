package biomine3000.objects;

import java.util.HashMap;
import java.util.Map;

public enum BusinessObjectEventType {
    ERROR("error"),
    /** foo? */
    SERVICE_STATE_CHANGED("services/state-changed"),    
    SERVICE_REGISTER("services/register"),
    SERVICE_UNREGISTER("services/unregister"),
    SERVICES_LIST("services/list"),
    /** set a (client-specific) property at the server*/
    SET_PROPERTY("set-property"),
    /** 
     * <pre>
     * Mandatory fields: 
     *   "name" - name of the client.
     * Optional fields:
     *   "echo" - boolean
     *   "receive" - boolean; false if client does not want to receive anything (true by default)
     * <pre> 
     */
    CLIENT_REGISTER("client/register");
    
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
