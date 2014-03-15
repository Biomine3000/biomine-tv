package org.bm3k.abboe.common;

import org.bm3k.abboe.objects.BOB;
import org.bm3k.abboe.objects.BusinessObject;
import org.bm3k.abboe.objects.BusinessObjectEventType;

public class ClientUtils {
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

    public static BusinessObject makeRegistrationObject(ClientParameters params) {
        BusinessObjectMetadata metadata = new BusinessObjectMetadata();
        metadata.put("name", "clients"); // name of registration service
        metadata.put("request", "join"); // name of service request
        metadata.put("user", params.name);
        metadata.put("client", params.client);

        return BOB.newBuilder()
                .event(BusinessObjectEventType.SERVICES_REQUEST)
                .metadata(metadata)
                .build();
    }

    public static BusinessObject makeSubscriptionObject(ClientParameters params) {
        BusinessObjectMetadata metadata = new BusinessObjectMetadata();        
        metadata.setSubscriptions(params.subscriptions);
        metadata.setBoolean("echo", params.echo);
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

    public static BusinessObject makeRegistrationObject(String client) {
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
