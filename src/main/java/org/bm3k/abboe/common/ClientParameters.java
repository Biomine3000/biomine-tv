package org.bm3k.abboe.common;

import org.bm3k.abboe.services.Biomine3000ServiceName;

import java.util.ArrayList;
import java.util.List;

/** Current parameters are as follows:
 * <ul>
 *   <li>name: name registered to server</li>
 *   <li>client: attached to all sent packets (by ABBOEConnection) as attribute "client". May be null,
 *       which should generally be interpreted as anonymous"</li> 
 *   <li>receive mode (affects what is sent to client by server; see {@link ClientReceiveMode})</li>
 *   <li>subscriptions (what content types are sent to client by server; see {@link LegacySubscriptions})</li>
 *   <li>construct dedicated implementations for business objects?</li> 
 * </ul> 
 */
public class ClientParameters {
    // public String name;    // more specific name than provided by field "client"; name may also contain identifying info such as username or host 
    private String clientName;  // name of the client program            //     
    private Subscriptions subscriptions;
    private boolean echo;
    public List<String> services; // services implemented by the client      
            
    public ClientParameters(String clientName,                                                  
                            Subscriptions subscriptions, 
                            boolean echo,
                            List<String> services) {        
        this.clientName = clientName;
        this.subscriptions = subscriptions;
        this.echo = echo;
        this.services = new ArrayList<String>(services);
    }
    
    public ClientParameters(String clientName,                                                  
            Subscriptions subscriptions, 
            boolean echo) {    
        this (clientName, subscriptions, echo, new ArrayList<String>());       
    }
        
    /** Copy constructor */
    public ClientParameters(ClientParameters src) {
         this(src.clientName, src.subscriptions, src.echo, src.services);
    }
    
    public void addServices(Biomine3000ServiceName... services) {
        for (Biomine3000ServiceName s: services) {
            this.services.add(s.toString());
        }
    }
    
    public String getClient() {
        return clientName;
    }

    public Subscriptions getySubscriptions() { 
        return subscriptions;
    }

    public boolean getEcho() { 
        return echo;
    }
}
