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
    public String name;    // more specific name than provided by field "client"; name may also contain identifying info such as username or host 
    public String client;  // name of the client program
    public ClientReceiveMode receiveMode;
    private LegacySubscriptions legacySubscriptions;
    public boolean constructDedicatedImplementationsForBusineses;
    public List<String> services;
    public Subscriptions subscriptions;
    
    public ClientParameters(String name,
                            ClientReceiveMode receiveMode,
                            LegacySubscriptions legacySubscriptions,
                            boolean constructDedicatedImplementationsForBusineses,
                            Subscriptions subscriptions) {
        this(name,  null, receiveMode, legacySubscriptions, constructDedicatedImplementationsForBusineses, subscriptions);
    }
            
    public ClientParameters(String name,
                            String client,
                            ClientReceiveMode receiveMode,
                            LegacySubscriptions legacySubscriptions,
                            boolean constructDedicatedImplementationsForBusineses,
                            Subscriptions subscriptions) {
        this.name = name;
        this.client = client;
        this.receiveMode = receiveMode;
        this.legacySubscriptions = legacySubscriptions;
        this.constructDedicatedImplementationsForBusineses = constructDedicatedImplementationsForBusineses;
        this.services = new ArrayList<String>();
        this.subscriptions = subscriptions;
    }
    
    public LegacySubscriptions getLegacySubscriptions() {
        return legacySubscriptions;
    }
    
    /** Copy constructor */
    public ClientParameters(ClientParameters original) {
         this(original.name, original.client, original.receiveMode, original.legacySubscriptions,
                 original.constructDedicatedImplementationsForBusineses, original.subscriptions);
    }
    
    public void addServices(Biomine3000ServiceName... services) {
        for (Biomine3000ServiceName s: services) {
            this.services.add(s.toString());
        }
    }
}
