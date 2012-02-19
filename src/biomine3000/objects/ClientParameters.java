package biomine3000.objects;

import java.util.ArrayList;
import java.util.List;

/** Current parameters are as follows:
 * <ul>
 *   <li>name: name registered to server</li>
 *   <li>sender: attached to all sent packets (by ABBOEConnection) as attribute "sender". May be null, 
 *       which should generally be interpreted as anonymous"</li> 
 *   <li>receive mode (affects what is sent to client by server; see {@link ClientReceiveMode})</li>
 *   <li>subscriptions (what content types are sent to client by server; see {@link Subscriptions})</li>
 *   <li>construct dedicated implementations for business objects?</li> 
 * </ul> 
 */
public class ClientParameters {
    public String name;
    public String sender;
    public ClientReceiveMode receiveMode;
    public Subscriptions subscriptions;
    public boolean constructDedicatedImplementationsForBusineses;
    public List<String> services;
    
    public ClientParameters(String name,
                            ClientReceiveMode receiveMode,
                            Subscriptions subscriptions,
                            boolean constructDedicatedImplementationsForBusineses) {
        this(name,  null, receiveMode, subscriptions, constructDedicatedImplementationsForBusineses);
    }
    
    public ClientParameters(String name,
                            String sender,
                            ClientReceiveMode receiveMode,
                            Subscriptions subscriptions,
                            boolean constructDedicatedImplementationsForBusineses) {
        this.name = name;
        this.sender = sender;
        this.receiveMode = receiveMode;
        this.subscriptions = subscriptions;
        this.constructDedicatedImplementationsForBusineses = constructDedicatedImplementationsForBusineses;
        this.services = new ArrayList<String>();
    }
    
    /** Copy constructor */
    public ClientParameters(ClientParameters original) {
         this(original.name, original.sender, original.receiveMode, original.subscriptions, original.constructDedicatedImplementationsForBusineses);
    }
    
    public void addServices(Biomine3000ServiceName... services) {
        for (Biomine3000ServiceName s: services) {
            this.services.add(s.toString());
        }
    }
}
