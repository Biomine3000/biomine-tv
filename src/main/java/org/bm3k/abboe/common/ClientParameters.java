package org.bm3k.abboe.common;

import org.bm3k.abboe.services.Biomine3000ServiceName;

import java.util.ArrayList;
import java.util.List;

public class ClientParameters {
    // public String name;    // more specific name than provided by field "client"; name may also contain identifying info such as username or host 
    private String clientName;  // name of the client program            //     
    private Subscriptions subscriptions;    
    private List<String> services; // services implemented by the client      
            
    public ClientParameters(String clientName,                                                  
                            Subscriptions subscriptions,                             
                            List<String> services) {        
        this.clientName = clientName;
        this.subscriptions = subscriptions;        
        this.services = new ArrayList<String>(services);
    }
    
    public ClientParameters(String clientName,                                                  
            Subscriptions subscriptions) {             
        this (clientName, subscriptions, new ArrayList<String>());       
    }
        
    /** Copy constructor */
    public ClientParameters(ClientParameters src) {
         this(src.clientName, src.subscriptions,  src.services);
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
   
}
