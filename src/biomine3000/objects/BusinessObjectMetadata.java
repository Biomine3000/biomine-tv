package biomine3000.objects;

import java.io.UnsupportedEncodingException;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import util.CollectionUtils;

import biomine3000.objects.BusinessObjectException.ExType;



/**
 * In the initial implementation, mandatory fields are as follows:
 *   -"size" to specify length of payload in bytes.
 *   -"type" one of {@link BiomineTVMimeType}.
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
    
    private JSONObject json;
              
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

    /** 
     * Construct from a JSON string.
     */
    public BusinessObjectMetadata(String p) throws JSONException {                          
        json = new JSONObject(p);                              
    }   
    
    /**
     * Minimal metadata with only (mime)type and size of payload. Actually, even size might be null, if it is
     * not known at the time of creating the metadata...
     */
    public BusinessObjectMetadata(String type, Integer size) {
        try {
            // oh, the nuisance: putting throws a CHECKED exception...
            json = new JSONObject();
            json.put("type", type);
            if (size != null) {
                json.put("size", size);
            }
        }
        catch (JSONException e) {
            throw new BusinessObjectException(ExType.JSON_IMPLEMENTATION_MELTDOWN);
        }
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
    
    public List<String> keys() {
        return CollectionUtils.makeArrayList(json.keys());
    }
    
    /**
     * @return null if no such key.
     * @throws ClassCastExcpetion when the value is not a String.
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
     * @throws ClassCastExcpetion when the value is not an Integer.
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
    
    /** 
     * @return one of following: Boolean, Double, Integer, List, Map, Long, String, of null 
     * in case of no such object.
     * 
     * List and Map currently unsupported, BusinessObjectException shall await 
     * anyone foolish enough to try such conjurings.
     */
    public Object get(String key) {        
        Object o = json.opt(key);
        if (o == null) {
            return null;            
        }
        if (o instanceof Boolean || o instanceof Double || o instanceof Integer 
                || o instanceof Long|| o instanceof String) {
            return o;
        }
        else {
            throw new BusinessObjectException(ExType.CEASE_CONJURING);
        }
       
    }
    
    /** 
     * Return the JSONObject instance which defines this BusinessObjectMetadata 
     * The returned object is a reference to the JSONObject wrapped by this 
     * BusinessObjectMetadata (and not a copy), and is to be used when 
     * updates more complex than setting a simple string value are to 
     * be performed. 
     */
    public JSONObject asJSON() {
        return asJSON();
    }
    
          
    /** 
     * Mandatory. See {@link BiomineTVMimeType} for known types.
     * 
     * @see #getOfficialType()
     */
    public String getType() throws BusinessObjectException {
        String type = getString("type");        
        if (type == null) {
            throw new BusinessObjectException(ExType.MISSING_TYPE);
        }
        return type;
    }
    
    /**
     * Get a official type (which might have a dedicated implementation class) 
     *
     * @return null, when the type in question is not officially supported by the java reference implementation.
     * @see #getType()
     */
    public BiomineTVMimeType getOfficialType() throws BusinessObjectException {
        String typeName = getType();
        return BiomineTVMimeType.getType(typeName);
    }
    
    /**
     * Set size of payload. Note that is is possible to create metadata without yet knowing the payload size,
     * hence the need for this method. 
     */
    public void setPayloadSize(int size) {
        put("size", size);        
    }
    
    /**
     * Get size of payload.
     */
    public long getSize() {
        Integer size = getInteger("size");
        if (size == null) {
            throw new BusinessObjectException(ExType.MISSING_SIZE);
        }
        return size;
    }
    
    /** 
     * The sender field is as of 2+11-12-12 estimated to be optional.
     * @return null if no sender
     */
    public String getSender() {
        return getString("sender");        
    }          
       
    /** 
     * Return a compact json representation of the business object. Use {@link toString(int)}
     * for a pretty-printing version. 
     * 
     * Note that we are here diverging from normal leronen policy of keeping toString reserved
     * for purely debug purposes.  
     */
    public String toString() {
        return json.toString();        
    }
    
    public String toString(int indentFactor) {
        try {
            return json.toString(indentFactor);
        }
        catch (JSONException e) {
            // should not be possible
            throw new BusinessObjectException(ExType.JSON_IMPLEMENTATION_MELTDOWN);
        }
    }
    
    
}