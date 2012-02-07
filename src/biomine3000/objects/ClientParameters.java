package biomine3000.objects;

/** Current parameters are as follows:
 * <ul>
 *   <li>name</li> 
 *   <li>receive mode (affects what is sent to client by server; see {@link ClientReceiveMode})</li>
 *   <li>subscriptions (what content types are sent to client by server; see {@link Subscriptions})</li>
 *   <li>construct dedicated implementations for business objects?</li> 
 * </ul> 
 */
public class ClientParameters {
    public String name; 
    public ClientReceiveMode receiveMode;
    public Subscriptions subscriptions;
    public boolean constructDedicatedImplementationsForBusineses;
    
    public ClientParameters(String name, 
                            ClientReceiveMode receiveMode,
                            Subscriptions subscriptions,
                            boolean constructDedicatedImplementationsForBusineses) {
        this.name = name;
        this.receiveMode = receiveMode;
        this.subscriptions = subscriptions;
        this.constructDedicatedImplementationsForBusineses = constructDedicatedImplementationsForBusineses;
    }
}
