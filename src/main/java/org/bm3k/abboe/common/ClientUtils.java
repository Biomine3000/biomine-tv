package org.bm3k.abboe.common;

import org.bm3k.abboe.objects.BOB;
import org.bm3k.abboe.objects.BusinessObject;
import org.bm3k.abboe.objects.BusinessObjectEventType;
import org.bm3k.abboe.objects.BusinessObjectMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientUtils {
    private static Logger log = LoggerFactory.getLogger(ABBOEConnection.class);
    
    private ClientUtils () {
    }

    /*
    def registration_object(client_name, user_name):
        metadata = {
            'event': 'services/request',
            'name': 'clients',
            'request': 'join',
            'client': client_name,
            'user': user_name
            }
        return BusinessObject(metadata, None)

    def subscription_object(natures=[]):
        metadata = {
            'event': 'routing/subscribe',
            'receive-mode': 'all',
            'types': 'all',
            'natures': natures
            }
        return BusinessObject(metadata, None)
     */

    /** Make object for join request to clients service */ 
    public static BusinessObject makeClientsJoinRequest(ClientParameters params) {        
        BusinessObjectMetadata metadata = new BusinessObjectMetadata();
        metadata.put("name", "clients"); // name of registration service
        metadata.put("request", "join"); // name of service request
        metadata.put("user", Biomine3000Utils.getUser());
        metadata.put("client", params.getClient());

        return BOB.newBuilder()
                .event(BusinessObjectEventType.SERVICES_REQUEST)
                .metadata(metadata)
                .build();
    }

    public static BusinessObject makeSubscriptionEvent(ClientParameters params) {
        BusinessObjectMetadata metadata = new BusinessObjectMetadata();        
        metadata.setSubscriptions(params.getySubscriptions());
        metadata.setBoolean("echo", params.getEcho());
        String requestId = Biomine3000Utils.generateId(Biomine3000Utils.getHostName());
        metadata.put("id", requestId);

        return BOB.newBuilder()
                .event(BusinessObjectEventType.ROUTING_SUBSCRIPTION)
                .metadata(metadata)
                .build();
    }

    public static BusinessObject makeSubscriptionObject(Subscriptions subscriptions) {
        BusinessObjectMetadata metadata = new BusinessObjectMetadata();
        metadata.setSubscriptions(subscriptions);

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
