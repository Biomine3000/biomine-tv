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
    public String name;
    public String client;
    public ClientReceiveMode receiveMode;
    public LegacySubscriptions subscriptions;
    public boolean constructDedicatedImplementationsForBusineses;
    public List<String> services;
    
    public ClientParameters(String name,
                            ClientReceiveMode receiveMode,
                            LegacySubscriptions subscriptions,
                            boolean constructDedicatedImplementationsForBusineses) {
        this(name,  null, receiveMode, subscriptions, constructDedicatedImplementationsForBusineses);
    }
    
    public ClientParameters(String name,
                            String client,
                            ClientReceiveMode receiveMode,
                            LegacySubscriptions subscriptions,
                            boolean constructDedicatedImplementationsForBusineses) {
        this.name = name;
        this.client = client;
        this.receiveMode = receiveMode;
        this.subscriptions = subscriptions;
        this.constructDedicatedImplementationsForBusineses = constructDedicatedImplementationsForBusineses;
        this.services = new ArrayList<String>();
    }
    
    /** Copy constructor */
    public ClientParameters(ClientParameters original) {
         this(original.name, original.client, original.receiveMode, original.subscriptions,
                 original.constructDedicatedImplementationsForBusineses);
    }
    
    public void addServices(Biomine3000ServiceName... services) {
        for (Biomine3000ServiceName s: services) {
            this.services.add(s.toString());
        }
    }
}
