package biomine3000.objects;

import java.io.IOException;

import util.CmdLineArgs2;

public class Biomine3000Args extends CmdLineArgs2 {
    
    /** Logging configured automatically by this constructor, based on args! */
    public Biomine3000Args(String[] args) throws IllegalArgumentsException {
        super(args);
    }
    
    /** @param configureLogging configure logging automatically? */     
    public Biomine3000Args(String[] args, boolean configureLogging) throws IllegalArgumentsException, IOException {
        super(args);
        if (configureLogging) {
            Biomine3000Utils.configureLogging(this);
        }
    }
    
    /** -channel */
    public String getChannel() {
        return get("channel");
    }
    
    /** opt -user, or env var "USER", or "anonymous"*/  
    public String getUser() {
        // try opt
        String user = get("user");
        if (user != null) {            
            return user;
        }
        
        // try env var
        user = System.getenv("USER");
        if (user != null) {            
            return user;
        }
        
        // fall back to "anonymous"
        return "anonymous";      
    }
    
    public String getHost() {
        return get("host");
    }
    
    public Integer getPort() {
        return getInt("port");
    }
    
    

}
