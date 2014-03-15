package org.bm3k.abboe.common;

import org.bm3k.abboe.services.Biomine3000ServiceName;

import java.util.ArrayList;
import java.util.List;

public class ClientParameters {
    // public String name;    // more specific name than provided by field "client"; name may also contain identifying info such as username or host 
    private String clientName;  // name of the client program            //     
    private Subscriptions subscriptions;
    private boolean echo;
    private List<String> services; // services implemented by the client      
            
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
