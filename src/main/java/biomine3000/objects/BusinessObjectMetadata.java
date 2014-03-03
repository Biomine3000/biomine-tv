package biomine3000.objects;

import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.List;

import com.google.common.net.MediaType;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import util.CollectionUtils;
import util.JSONUtils;


/**
 * In the initial implementation, mandatory fields are as follows:
 *   -"size" to specify length of payload in bytes.
 *   -"type" one of {@link biomine3000.objects.BusinessMediaType or @link MediaType}.
 *   
 * TBD:
 *   -Should we enforce the type of known fields, such as sender?
 *   -Should the metadata be aware of its businessobject?
 *   -How should correctness of field size be enforced, as clearly it should obey the size of the actual data to be sent,
 *    and not maybe be stored at all except at the time of parsing the business object; when writing, the stored size
 *    should not be used, as the data might have changed?
 *   -should metadata be immutable? 
 *   -should business objects be immutable?
 *   -How should information about standard mime types be utilized?
 *  
 */
public class BusinessObjectMetadata {
        
    private IBusinessObject obj;
    
    private JSONObject json;

    public void setObject(IBusinessObject obj) {
        this.obj = obj;
    }
    
    /** 
     * Construct from JSON represented as UTF-8 coded bytes. Note that behavior is undefined 
     * when the characters are not encoded as UTF-8.
     */
    public BusinessObjectMetadata (byte[] bytes) throws InvalidJSONException {

        try {            
            json = new JSONObject(new String(bytes, "UTF-8"));
        }
        catch (UnsupportedEncodingException e) {
            // the unthinkable has occurred; UTF-8 not supported by this very java instance
            throw new RuntimeException("Arkku has abandoned vim in favor of Eclipse");
        }                                         
        catch (JSONException e) {
            // failure due to callers folly of providing invalid JSON text
            throw new InvalidJSONException(e);
        }
    }

    public boolean hasPayload() {
        return getType() != null; 
    }
    
    /** 
     * Construct an initially empty metadata. Recall that it is allowed to have no contents
     * in a business object.
     */
    public BusinessObjectMetadata() {                          
        json = new JSONObject();                              
    }   
    
    private BusinessObjectMetadata(JSONObject json) {                          
        this.json = json;                              
    }          
    
    public void setType(String type) {
        put("type", type);
    }
    
    /** null if no subscriptions defined. */
    public Subscriptions getSubscriptions() throws InvalidJSONException {
        try {
            Object json = this.json.opt("subscriptions");
            if (json == null) {
                return null;
            }
            else {
                return Subscriptions.make(json);
            }
        }
        catch (JSONException e) {
            throw new InvalidJSONException(e);
        }
    }
    
    public void setSubsciptions(Subscriptions subscriptions) throws JSONException {             
        json.put("subscriptions", subscriptions.toJSON());
    }
    
    public void setType(MediaType type) {
        put("type", type.toString());
    }

    
    /**
     * Minimal metadata with only (mime)type and size of payload. Actually, even size might be null, if it is
     * not known at the time of creating the metadata...
     */
    public BusinessObjectMetadata(MediaType type) {
        json = new JSONObject();
        setType(type.toString());        
    }
    
    
    /**
     * Put a simple string value. For more complex values, use the wrapped json object directly
     * (reference obtainable via {@link #asJSON()})
     */ 
    public void put(String key, String value) {
        try {            
            json.put(key, value);            
        }
        catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Put a simple integer value. For more complex values, use the wrapped json object directly
     * (reference obtainable via {@link #asJSON()})
     */ 
    public void put(String key, int value) {
        try {            
            json.put(key, value);            
        }
        catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
    
    @SuppressWarnings("unchecked")
    public List<String> keys() {        
        return CollectionUtils.makeArrayList((Iterator<String>)json.keys());
    }
    
    /**
     * @return null if no such key.
     * @throws ClassCastException when the value is not a String.
     */
    public String getString(String key) throws ClassCastException {
        Object val = json.opt(key);
        if (val == null) {
            return null;
        }
        else if (val instanceof String){
            return (String)val;
        }
        else {
            throw new ClassCastException("Value for key "+key+" has class "+val.getClass()+", which is not a String, as supposed by the foolish caller");
        }
        
    }
    
    /**
     * @return null if no such key.
     * @throws ClassCastException when the value is not an Integer.
     */
    public Integer getInteger(String key) throws ClassCastException {
        Object val = json.opt(key);
        if (val == null) {
            return null;
        }
        else if (val instanceof Integer) {
            return (Integer)val;
        }
        else {
            throw new ClassCastException("Value for key "+key+" has class "+val.getClass()+", which is not an Integer, as supposed by the foolish caller");
        }
        
    }
    
    public void putStringList(String key, List<String> values) {
        JSONArray arr = new JSONArray();
        for (String s: values) {
            arr.put(s);
        }
        try {
            json.put(key, arr);
        }
        catch (JSONException e) {
            // should not be possible
            throw new RuntimeException(e);
        }
    }
    
    /** Return single strings as a singleton list */ 
    public List<String> getList(String key) {
        Object o = json.opt(key);
        if (o == null) {
            return null;
        }
        else if (o instanceof String) {
            return Collections.singletonList((String)o);
        }
        else if (o instanceof JSONArray) {
            JSONArray arr = (JSONArray)o;            
            ArrayList<String> list = new ArrayList<String>(arr.length());
            for (int i=0; i<arr.length(); i++) {
                Object obj = arr.opt(i);
                if (!(obj instanceof String)) {
                    throw new ClassCastException("Not a string: "+obj.getClass() +"(while retrieving key "+key+")");                
                }
                else {
                    list.add((String)obj);
                }
            }
            return Collections.unmodifiableList(list);
        }
        else {
            throw new ClassCastException("Not a string or jsonarray: "+o.getClass() +"(while retrieving key "+key+")");
        }
        
    }
    
    /** 
     * @return one of following: Boolean, Double, Integer, List<String>, Map, Long, String, or null 
     * in case of no such object.
     * 
     * Map currently unsupported, BusinessObjectException shall await 
     * anyone foolish enough to try such conjurings.
     */
    public Object get(String key) {        
        Object o = json.opt(key);
        if (o == null) {
            return null;            
        }
        else if (o instanceof Boolean || o instanceof Double || o instanceof Integer 
                || o instanceof Long|| o instanceof String) {
            return o;
        }
        else if (o instanceof JSONArray) {
            return getList(key);
        }
        else {
            throw new RuntimeException("THEN HE'S GONE (read the javadoc, pal!)");
        }
       
    }
    
    /** 
     * Return the JSONObject instance which defines this § 
     * The returned object is a reference to the JSONObject wrapped by this 
     * BusinessObjectMetadata (and not a copy), and is to be used when 
     * updates more complex than setting a simple string value are to 
     * be performed. 
     */
    public JSONObject asJSON() {
        return json;
    }
    
    public void setEvent(String event) {
        put("event", event);
    }
    
    public void setEvent(BusinessObjectEventType et) {
        put("event", et.toString());
    }
    
    public String getEvent() {
        return getString("event");
    }
    
    /** Return null if no event, or event is not one of BusinessObjectEventType.XXX */
    public BusinessObjectEventType getKnownEvent() {
        String event = getEvent();
        if (event == null) {
            return null;
        }        
        return BusinessObjectEventType.getType(event);
        
        
    }
            
    public String getName() {
        return getString("name");                
    }      
    
    public void setName(String name) {
        put("name", name);                
    }
    
    public void setBoolean(String key, boolean value) {
        try {
            json.put(key, value);
        }
        catch (Exception e) {
            throw new RuntimeException("Inconveivable");
        }
    }
        
    
    /** @throws InvalidJSONException if the value is not booleanizable */
    public Boolean getBoolean(String key) throws InvalidJSONException {
        try {
            return json.getBoolean(key);
        }
        catch (JSONException e) {
            throw new InvalidJSONException(e);
        }
    }
  
    public String getUser() {
        return getString("user");                
    }
    
    public void setUser(String user) {
        put("user", user);                
    }
    
    /** 
     * See {@link biomine3000.objects.BusinessMediaType} for known types. Note that payload is not
     * mandatory, in which case this method returns null!
     * 
     * @see #getOfficialType()
     *
     * TODO: get rid of String-typing, use MediaType all over.
     */
    public String getType() {
        return getString("type");                
    }
    
    /**
     * Get a official type (which might have a dedicated implementation class) 
     *
     * @return null, when the type in question is not officially supported by the java reference implementation,
     * of when there is no payload.
     * @see #getType()
     */
    public MediaType getOfficialType() {
        String typeName = getType();
        if (typeName == null) {
            return null;
        }
        return MediaType.parse(typeName);
    }
       
        
    /**
     * Get size of payload. In the current implementation, this max size is limited to 
     * Integer.MAX_VALUE. If there is an business object, return the size from the object.
     * Otherwise, return field "size", if it exists; if it does not exist, return null.
     */
    public Integer getSize() {
        if (obj != null) {
            return obj.getPayload().length;
        }
        else {             
            return getInteger("size");                       
        }
    }
    
    /** 
     * The sender field is as of 2+11-12-12 estimated to be optional.
     * @return null if no sender
     */
    public String getSender() {
        return getString("sender");        
    }
    
    /** 
     * The sender field is as of 2011-12-12 estimated to be optional.
     * @return null if no sender
     */
    public void setSender(String sender) {
        put("sender", sender);        
    }
    
    /** 
     * @return null if no channel.
     */
    public String getChannel() {
        return getString("channel");        
    }          
    
    public BusinessObjectMetadata clone() {
        JSONObject jsonClone = JSONUtils.clone(this.json);
        BusinessObjectMetadata clone = new BusinessObjectMetadata(jsonClone);
        return clone;        
    }
    
    /** Return JSONObject with field "size" derived from the business object */ 
    private JSONObject jsonObjectWithSize() {        
        if (hasPayload()) {            
            JSONObject json = JSONUtils.clone(this.json);
            try {
                json.put("size", obj.getPayload().length);
            }
            catch (JSONException e) {
                // should not be possible
                throw new RuntimeException("JSON Implementation meltdown", e);
            }               
            return json;
        }
        else {            
            // return as is
            return this.json;
        }
    }
    
    /** 
     * Return a compact json representation of the business object. Use {@link toString(int)}
     * for a pretty-printing version. 
     * 
     * Note that we are here diverging from normal leronen policy of keeping toString reserved
     * for purely debug purposes.  
     */
    @Override
    public String toString() {
        JSONObject json = jsonObjectWithSize();                       
        return json.toString();        
    }
    
    
    public String toString(int indentFactor) {        
        JSONObject json = jsonObjectWithSize();
        try {
            return json.toString(indentFactor);
        }
        catch (JSONException e) {
            // should not be possible
            throw new RuntimeException("JSON implementation meltdown");
        }  
                
    }

    public String formatWithoutPayload() {
        JSONObject jsonClone = JSONUtils.clone(this.json);
        jsonClone.remove("size");
        jsonClone.remove("type");
        BusinessObjectMetadata clone = new BusinessObjectMetadata(jsonClone);
        return clone.toString();
        
    }
    
    public boolean isEvent() {
        return json.has("event");
    }
    
    
}