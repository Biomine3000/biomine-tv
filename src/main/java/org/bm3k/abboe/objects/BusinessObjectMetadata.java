package org.bm3k.abboe.objects;

import java.io.UnsupportedEncodingException;
import java.util.*;

import com.google.common.net.MediaType;
import org.bm3k.abboe.common.InvalidBusinessObjectMetadataException;
import org.bm3k.abboe.common.LegacySubscriptions;
import org.bm3k.abboe.common.Subscriptions;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import util.CollectionUtils;
import util.JSONUtils;


/**
 * In the initial implementation, mandatory fields are as follows:
 *   -"size" to specify length of payload in toBytes.
 *   -"type" one of {@link org.bm3k.abboe.objects.BusinessMediaType or @link MediaType}.
 *   
 * TBD:
 *   -Should we enforce the type of known fields, such as client?
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
           
    
    private BusinessObject obj;
    
    private JSONObject json;

    public void setObject(BusinessObject obj) {
        this.obj = obj;
    }
    
    /** 
     * Construct from JSON represented as UTF-8 coded toBytes. Note that behavior is undefined
     * when the characters are not encoded as UTF-8.
     */
    public BusinessObjectMetadata (byte[] bytes) throws InvalidBusinessObjectMetadataException {

        try {            
            json = new JSONObject(new String(bytes, "UTF-8"));
        }
        catch (UnsupportedEncodingException e) {
            // the unthinkable has occurred; UTF-8 not supported by this very java instance
            throw new RuntimeException("Arkku has abandoned vim in favor of Eclipse");
        }                                         
        catch (JSONException e) {
            // failure due to callers folly of providing invalid JSON text
            throw new InvalidBusinessObjectMetadataException(e);
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
        setType(MediaType.parse(type));
    }

    public void setType(MediaType type) {
        put("type", type.toString());
    }
    
    public void setSubscriptions(Subscriptions subscriptions) {
        this.putStringArray("subscriptions", subscriptions.toStringList());
    }
    
    /** 
     *  stored as field "subscriptions". The fact that they are legacy is revealed by such a field
     * residing in a clients/register object (instead of the routing/subscribe object)
     * 
     * null if no subscriptions defined. 
     * @deprecated
     */    
    public LegacySubscriptions getLegacySubscriptions() throws InvalidBusinessObjectMetadataException {
        try {
            Object json = this.json.opt("subscriptions");
            if (json == null) {
                return null;
            }
            else {
                return LegacySubscriptions.make(json);
            }
        }
        catch (JSONException e) {
            throw new InvalidBusinessObjectMetadataException(e);
        }
    }
    
    /** @return empty list if no natures */
    public Set<String> getNatures() {
    	JSONArray arr = json.optJSONArray("nature");
    	if (arr == null) {
    		arr = json.optJSONArray("natures");
    	}
    	
    	if (arr != null) {
    	    // TODO: should maybe do some Set wrapper for JSONArray instead of duplicating data
    		LinkedHashSet<String> result = new LinkedHashSet<>(arr.length());
    		for (int i=0; i<arr.length(); i++) {
    			result.add(arr.getString(i));
    		}
    		return result;
    	}
    	else {
    		return Collections.emptySet();
    	}    		    	
    }
        
    public void setNatures(String... natures) {
        putStringArray("natures", Arrays.asList(natures));        
    }
    
    public void setNatures(List<String> natures) {        
        putStringArray("natures", natures);
    }
    
    public void addNatures(String... natures) {
        for (String nature: natures) {
            addNature(nature);
        }        
    }
    
    public void addNature(String nature) {
        JSONArray natures;
        if (hasKey("natures")) {
            natures = json.getJSONArray("natures");            
        }
        else {
            natures = new JSONArray();
            json.put("natures", natures);
        }
        
        natures.put(nature);
    }
    
    public void addWarning(String warning) {
        JSONArray warnings;
        if (hasKey("warnings")) {
            warnings = json.getJSONArray("warnings");            
        }
        else {
            warnings = new JSONArray();
            json.put("warnings", warnings);
        }
        
        warnings.put(warning);
    }
    
    /** stored as field "subscriptions". The fact that they are legacy is revealed by such a field
     * residing in a clients/register object (instead of the routing/subscribe object)
     * @param subscriptions
     * @throws JSONException
     * @deprecated
     */     
    public void setLegacySubsciptions(LegacySubscriptions subscriptions) throws JSONException {             
        json.put("subscriptions", subscriptions.toJSON());
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
        json.put(key, value);                    
    }
    
    /**
     * Put a simple string value. For more complex values, use the wrapped json object directly
     * (reference obtainable via {@link #asJSON()})
     */ 
    public void put(String key, JSONArray arr) {                    
        json.put(key, arr);                    
    }
    
    /**
     * Put a simple integer value. For more complex values, use the wrapped json object directly
     * (reference obtainable via {@link #asJSON()})
     */ 
    public void put(String key, int value) {        
        json.put(key, value);
    }
        
    @SuppressWarnings("unchecked")
    public List<String> keys() {        
        return CollectionUtils.makeArrayList((Iterator<String>)json.keys());
    }
    
    public boolean hasPlainTextPayload() {
        MediaType type = getOfficialType();
        if (type != null && type.is(MediaType.ANY_TEXT_TYPE)) {
            return true;
        }
        else {
            return false;
        }
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
        else if (val == JSONObject.NULL) {
            return null;
        }
        else if (val instanceof String){
            return (String)val;
        }
        else {
            throw new ClassCastException("Value for key "+key+" has class "+val.getClass()+", which is not a String, as supposed by the foolish caller. Complete json\n: "+json.toString(4));
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
    
    public void putStringArray(String key, List<String> values) {
        JSONArray arr = new JSONArray();
        for (String s: values) {
            arr.put(s);
        }
        
        json.put(key, arr);        
        
    }
    
    public void putStringArray(String key, String... values) {
        JSONArray arr = new JSONArray();
        for (String s: values) {
            arr.put(s);
        }
        
        json.put(key, arr);        
        
    }
    
    /**
     * 
     * Return a JSON array variable or single string variable as a list. 
     * Return single strings as a singleton list. Return null, is no such key.
     * The returned list is unmodifiable.  
     * */ 
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
            
    public void put(String key, boolean value) {
        try {
            json.put(key, value);
        }
        catch (Exception e) {
            throw new RuntimeException("Inconveivable");
        }
    }
        
    
    /** @throws InvalidBusinessObjectMetadataException if the value is not booleanizable */
    public Boolean getBoolean(String key) throws InvalidBusinessObjectMetadataException {
        if (json.has(key)) {                    
            try {
                return json.getBoolean(key);            
            }
            catch (JSONException e) {
                throw new InvalidBusinessObjectMetadataException(e);
            }
        }
        else {
            return null;
        }
    }
  
    /**
     * See {@link org.bm3k.abboe.objects.BusinessMediaType} for known types. Note that payload is not
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
     * Note that the clone will not have a reference to the businessobject, as supposedly
     * it is to be assigned to a different businessobject instance 
     */
    public BusinessObjectMetadata clone() {
        JSONObject jsonClone = JSONUtils.clone(this.json);
        BusinessObjectMetadata clone = new BusinessObjectMetadata(jsonClone);
        return clone;        
    }
    
    /** Return JSONObject with field "size" derived from the business object */ 
    private JSONObject jsonObjectWithSize() {        
        if (hasPayload()) {            
            JSONObject json = JSONUtils.clone(this.json);            
            json.put("size", obj.getPayload().length);                           
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
        return json.toString(indentFactor);                         
    }

    public String formatWithoutPayload() {
        JSONObject jsonClone = JSONUtils.clone(this.json);
        jsonClone.remove("size");
        jsonClone.remove("type");
        BusinessObjectMetadata clone = new BusinessObjectMetadata(jsonClone);
        return clone.toString();
        
    }
    
    public boolean hasKey(String key) {
        return json.has(key);
    }
    
    public boolean isEvent() {
        return json.has("event");
    }

    public boolean hasNature(String nature) {
        return getNatures().contains("nature");
    }
    
    
}