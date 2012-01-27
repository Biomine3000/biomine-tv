package biomine3000.objects;

import java.io.IOException;

import util.CmdLineArgs2;

public class Biomine3000Args extends CmdLineArgs2 {
    
    /** Logging configured automatically by this constructor, based on args */
    public Biomine3000Args(String[] args) throws IllegalArgumentsException {
        super(args);
    }
    
    public Biomine3000Args(String[] args, boolean configureLogging) throws IllegalArgumentsException, IOException {
        super(args);
        Biomine3000Utils.configureLogging(this);                    
    }
    
    /** -channel */
    public String getChannel() {
        return get("channel");
    }
    
    /** -user, or USER */  
    public String getUser() {
        String user = get("user");
        if (user != null) {            
            return user;
        }
        else {
            return System.getenv("USER");
        }        
    }
    
    public String getHost() {
        return get("host");
    }
    
    public Integer getPort() {
        return getInt("port");
    }
    
    

}
