package org.bm3k.abboe.common;

import org.bm3k.abboe.objects.BOB;
import org.bm3k.abboe.objects.BusinessObject;
import org.bm3k.abboe.objects.BusinessObjectEventType;
import org.bm3k.abboe.objects.BusinessObjectMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientUtils {
    private static Logger log = LoggerFactory.getLogger(ABBOEConnection.class);
    
    private ClientUtils() {
        // utils must not come into existence
    }

    /** Make object for join request to clients service */ 
    public static BusinessObject makeClientsJoinRequest(ClientParameters params) {                       
        return BOB.newBuilder()
                .event(BusinessObjectEventType.SERVICES_REQUEST)
                .attribute("name", "clients") // name of registration service
                .attribute("request", "join") // name of service request
                .attribute("user", Biomine3000Utils.getUser())
                .attribute("client", params.getClient())
                .build();
    }

    public static BusinessObject makeSubscriptionEvent(ClientParameters params) {
        BusinessObjectMetadata metadata = new BusinessObjectMetadata();        
        metadata.setSubscriptions(params.getySubscriptions());
        metadata.put("id", Biomine3000Utils.generateUID());

        return BOB.newBuilder()
                .event(BusinessObjectEventType.ROUTING_SUBSCRIPTION)
                .metadata(metadata)                
                .build();
    }

    public static BusinessObject makeSubscriptionEvent(Subscriptions subscriptions) {
        BusinessObjectMetadata metadata = new BusinessObjectMetadata();
        metadata.setSubscriptions(subscriptions);
        metadata.put("id", Biomine3000Utils.generateUID());

        return BOB.newBuilder()
                .event(BusinessObjectEventType.ROUTING_SUBSCRIPTION)
                .metadata(metadata)
                .build();
    }

    public static BusinessObject makeClientsJoinRequest(String client) {
        log.info("Making clientsJoinRequest with client: "+client);
        BusinessObjectMetadata metadata = new BusinessObjectMetadata();
        metadata.put("name", "clients"); // name of registration service        
        metadata.put("request", "join"); // name of service request        
        metadata.put("client", client);                        
        metadata.put("user", Biomine3000Utils.getUser());
        
        return BOB.newBuilder()
                .event(BusinessObjectEventType.SERVICES_REQUEST)
                .metadata(metadata)
                .build();
    }
}
