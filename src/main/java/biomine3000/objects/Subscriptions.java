package biomine3000.objects;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.common.net.MediaType;
import org.json.JSONArray;
import org.json.JSONException;

import util.CollectionUtils;


public abstract class Subscriptions {
    
    public static final Subscriptions ALL = new All();
    public static final Subscriptions NONE = new None();
    public static final Subscriptions PLAINTEXT = make(BusinessMediaType.PLAINTEXT);
        
    
    public abstract boolean shouldSend(BusinessObject bo);
    public abstract Object toJSON();
    @Override
    public String toString() {            
        return toJSON().toString();           
    }
    
    public static Subscriptions make(String... types) throws JSONException {
        return make(CollectionUtils.makeArrayList(types));
    }
    
    public static Subscriptions make(MediaType... types) {
        IncludeList result = new IncludeList();
        result.addAll(Arrays.asList(types));
        return result;
    }
    
    public static Subscriptions make(Collection<String> types) throws JSONException {
        return new IncludeList(types);
    }
    
    public static Subscriptions make(String s)  {        
        if (s.equals("all")) {
            return new All();
        }
        else if (s.equals("none")) {
            return new None();
        }
        else {
            // single types
            return new IncludeList(Collections.singleton(s));
        }       
    }
    
    public static Subscriptions make(MediaType o) {
        return null;
    }
    
    public static Subscriptions make(Object o) throws JSONException {
        if (o instanceof String) {
            return make((String)o);
        }
        else if (o instanceof JSONArray) {
            return new IncludeList((JSONArray)o);
        }
        else if (o instanceof MediaType) {
            IncludeList result = new IncludeList();
            result.addAll(Collections.singletonList((MediaType)o));
            return result;
        }
        else {
            throw new JSONException("Unrecognized object type: "+o.getClass());
        }
        
    }
    
    private static class All extends Subscriptions {
        
        @Override
        public boolean shouldSend(BusinessObject bo) {
            return true;
        }
        
        @Override
        public Object toJSON() {
            return "all";
        }
        
        
    }
    
    private static class None extends Subscriptions {
        @Override
        public boolean shouldSend(BusinessObject bo) {
            // only send events
            return bo.isEvent(); 
        }
        
        @Override
        public Object toJSON() {
            return "none";
        }
                
    }
    
    private static class IncludeList extends Subscriptions {
        private Set<String> types;
        
        private IncludeList() {
            this.types = new LinkedHashSet<String>();            
        }
        
        private void addAll(Collection<MediaType> types) {
            for (MediaType type: types) {
                this.types.add(type.toString());
            }
        }
        
        private IncludeList(Collection<String> types) {
            this.types = new LinkedHashSet<String>(types);
        }
        
        private IncludeList(JSONArray types) throws JSONException {
            this.types = new LinkedHashSet<String>(types.length());
            for (int i=0; i<types.length(); i++) {
                String type = types.getString(i);
                this.types.add(type);
            }
            
        }
        
        @Override
        public boolean shouldSend(BusinessObject bo) {
            String type = bo.getMetaData().getType();
            return (type != null && types.contains(type));            
        }
                
        @Override
        public Object toJSON() {
            JSONArray arr = new JSONArray();
            for (String type: types) {
                arr.put(type);
            }
            return arr;
        }
    }
}
