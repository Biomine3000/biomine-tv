package org.bm3k.abboe.server;

import static org.bm3k.abboe.objects.BusinessObjectEventType.ROUTING_SUBSCRIBE_REPLY;
import static org.bm3k.abboe.objects.BusinessObjectEventType.ROUTING_SUBSCRIPTION;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;

import org.bm3k.abboe.common.Biomine3000Utils;
import org.bm3k.abboe.common.InvalidBusinessObjectException;
import org.bm3k.abboe.common.ServerAddress;
import org.bm3k.abboe.common.Subscriptions;
import org.bm3k.abboe.objects.BOB;
import org.bm3k.abboe.objects.BusinessObject;
import org.bm3k.abboe.objects.BusinessObjectEventType;
import org.bm3k.abboe.objects.BusinessObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>Connect to a single peer server in a dedicated thread</b>
 * <p> 
 *  Send routing/subscription with following attributes (from specs):
 *  <ul>
 *    <li> routing-id – the server’s own routing id (unlike clients, it should preferably pick its own) </li>
 *    <li> role = server – differentiate from clients (affects routing)</li>
 *    <li> subscriptions – the array of subscriptions (practically always [ * ] between servers, otherwise routes through such server may silently discard Objects even if clients subscribe to them)</li>
 *   <li> id – the unique, generated id of the subscription object</li>
 *  </ul>
 *  <p>
 *  Expect a subsciption reply in return with following attributes:</li><
 *  <ul>  
 *    <li> role = server
 *    <li> routing-id – the replying server’s routing id
 *    <li> subscriptions – the array of subscriptions (i.e., [ * ])
 * </ol>
 * 
 * Apparently, at least for the sake of the actual business of delivering objects, it makes no
 * difference which end initiated the connection.
 *  
 * Once we have sent the routing/subscribe and received an expected reply, the connection 
 * is made into a normal NeighborConnection.     
 */
public class PeerConnectionThread extends Thread {

	private final Logger log = LoggerFactory.getLogger(PeerConnectionThread.class);
	
	private final ABBOEServer abboeServer;
	
	private ServerAddress peerAddress;
	private Socket socket = new Socket();
    	
	PeerConnectionThread(ABBOEServer abboeServer, ServerAddress peerAddress) {
        this.abboeServer = abboeServer;
		this.peerAddress = peerAddress;
        this.setName("peerconnector-" + peerAddress.getShortName());
    }
    
    private void connect() throws PeerConnectException {
        socket = new Socket();
        InetSocketAddress inetAddress = new InetSocketAddress(peerAddress.getHost(), peerAddress.getPort());
        log.info("Trying to connect to peer at addr " + peerAddress + " (timeout in " + this.abboeServer.args.getPeerConnectTimeout() + " seconds)");
        try {                
            socket.connect(inetAddress, this.abboeServer.args.getPeerConnectTimeout() * 1000);
        }
        catch (SocketTimeoutException e) {
            log.info("Connecting to server at "+peerAddress+ "timed out");
            throw new PeerConnectException("Connection to peer " + peerAddress + " timed out", e, true);
        }
        catch (ConnectException e) {
            log.info("Connecting to server at "+peerAddress+ " threw ConnectException: " + e);
            throw new PeerConnectException("Connection to peer " + peerAddress + " failed with connect exception", e, true);
        }
        catch (IOException e) {
        	throw new PeerConnectException("Connection to peer " + peerAddress + " failed with connect exception", e, true);                
        }           
    }

    /** 
     * Subscribe to the other server.
     * 
     * Performs following actions:
     * <ul>
     * <li> write subscription object
     * <li> read reply
     * <li> read return subscription
     * <li> write return subscription reply
     * <li> register peer to peer manager and sets state to CONNECTED.
     * </ul>
     */
    private void subscribe() throws SubscribeException {
        // write outgoing subscription
    	String subscribeEventId = Biomine3000Utils.generateUID();
    	try {
            log.info("writing outgoing subscription");
            BusinessObject subscription = BOB.newBuilder()
                    .event(BusinessObjectEventType.ROUTING_SUBSCRIPTION)
                    .attribute("routing-id", this.abboeServer.serverRoutingId)
                    .attribute("role", "server")
                    .attribute("subscriptions", new Subscriptions("*").toJSON())
                    .attribute("id", subscribeEventId)
                    .build();
            socket.getOutputStream().write(subscription.toBytes());
            socket.getOutputStream().flush();
    	}
    	catch (IOException e) {
    		throw new SubscribeException("Failed writing subscription object", e, false);
    	}
        
        // expect a routing/subscribe/reply
    	log.info("reading subscribe reply");
    	BusinessObject subscribeReply = null;
        try {            
        	subscribeReply = BusinessObjectUtils.readObject(socket.getInputStream());
        }
        catch (IOException e) {
            throw new SubscribeException("Failed reading routing/subscribe/reply from server " + peerAddress, e, false); 
        }
        catch (InvalidBusinessObjectException e) {
        	// other server responded with an invalid business object, do not retry
        	throw new SubscribeException("Failed reading routing/subscribe/reply from server " + peerAddress, e, false);
        }

        if (subscribeReply == null) {
        	throw new SubscribeException("Peer closed connection", false);
        }
        
        if (!subscribeReply.isEvent(ROUTING_SUBSCRIBE_REPLY)) {            
        	throw new SubscribeException("Invalid reply object: not a " + ROUTING_SUBSCRIBE_REPLY.getEventName(), false);
        }
        
        String inReplyTo = subscribeReply.getMetadata().getString("in-reply-to");
        if (inReplyTo == null || !inReplyTo.equals(subscribeEventId)) {
        	throw new SubscribeException("Invalid in-reply-to: " + inReplyTo+ "; expecting " + subscribeEventId, false);
        }
                
        // expect a return subscription
        log.info("reading return subscription");
        BusinessObject returnSubscription = null;
        try {            
        	returnSubscription = BusinessObjectUtils.readObject(socket.getInputStream());
        }
        catch (IOException e) {
        	// IOException while connecting, assume that we might still be able to retry
        	// TODO: elaborate cause of IOException to decide whether retrying makes sense
            throw new SubscribeException("Failed reading return subscription from server " + peerAddress + 
            		                     e, true);
        }
        catch (InvalidBusinessObjectException e) {
        	// other server responded with an invalid business object, do not retry
        	// TODO: more elaborate logic to decide if and when to retry
        	throw new SubscribeException("Failed reading return subscription from server " + peerAddress + 
                     					e, false);
        }
        	            	
    	if (returnSubscription == null) {
    		throw new SubscribeException("Other server terminated connection without sending a reply", false); 
    	}
        
    	if (!returnSubscription.isEvent(ROUTING_SUBSCRIPTION)) {
    		throw new SubscribeException("Invalid reply event: not a " + ROUTING_SUBSCRIBE_REPLY.getEventName(), false);
    	}
    	
        String peerRole = returnSubscription.getMetadata().getString("role");
        if (peerRole == null || !peerRole.equals("server")) {
        	throw new SubscribeException("Other server has illegal role in return subscription: " + peerRole, false);
        	// TODO: send error about utter folly
        }
        
        String peerRoutingId = returnSubscription.getMetadata().getString("routing-id");
        if (peerRoutingId == null) { 
        	throw new SubscribeException("Other server has no routing-id return subscription", false);
        	// TODO: send error about utter folly
        }
        
        List<String> peerSubscriptionsList = returnSubscription.getMetadata().getList("subscriptions");
        if (peerSubscriptionsList == null) {
        	throw new SubscribeException("Other server did not specify field subscriptions in subscribe reply", false);	
        }
        Subscriptions peerSubscriptions = new Subscriptions(peerSubscriptionsList);
                    
        // send return subscribe reply
        String returnSubscriptionId = returnSubscription.getMetadata().getString("id");                       
        
        BusinessObject returnSubscriptionReply = BOB.newBuilder()
                .event(ROUTING_SUBSCRIBE_REPLY)
                .attribute("routing-id", peerRoutingId)                
                .build();
        
        if (returnSubscriptionId != null) {
        	returnSubscriptionReply.getMetadata().put("in-reply-to", returnSubscriptionId);
        }
        
        try {
        	socket.getOutputStream().write(returnSubscriptionReply.toBytes());
        	socket.getOutputStream().flush();
        }
        catch (IOException e) {
        	throw new SubscribeException("Failed sending return subscription reply", e, false);
        }            
        
        // successfully subscribed, received reply, read return subscription and sent return subscription reply
        // => proceed to register connection
        try {
        	PeerInfo peerInfo = this.abboeServer.peerManager.registerPeer(peerAddress, peerRoutingId, peerSubscriptions, SubscribeDirection.OUTGOING);
            
            NeighborConnection neighbor;

            try {
                neighbor = new NeighborConnection(this.abboeServer, socket);
                neighbor.setPeerInfo(peerInfo);
            }
            catch (IOException e) {
                log.error("IOException while initializing connection to neighbor " + peerAddress.getName(), e, true);
                
                this.abboeServer.peerManager.removePeer(peerRoutingId);
                
                try {
                    socket.close();
                }
                catch (IOException e2) {
                    // failed even this, no further action possible
                }
                return;
            }
            
            this.abboeServer.peerManager.setState(peerAddress, PeerState.CONNECTED);
            neighbor.startReaderThread();
        }
        catch (PeerManager.DuplicatePeerException e) {
        	throw new SubscribeException("Already connected to peer at address " + peerAddress.getName() + " with routing-id " + peerRoutingId, e, false); 
        }            
    }        
    
    @Override
    public void run() {
        log.info("Running PeerConnectionThread for address: "+peerAddress);
        
        this.abboeServer.peerManager.setState(peerAddress, PeerState.CONTACTING_AT_STARTUP);
        
        int peerConnectRetryInterval = this.abboeServer.args.getPeerConnectRetryInterval();
        log.info("Using peer connect retry interval: " + peerConnectRetryInterval);
        
        boolean keeptrying = true;
        
        while (keeptrying) {            
            try {
            	log.info("Trying to connect to peer: " + peerAddress.getName());
                connect();                
            }
            catch (PeerConnectException e) {
            	if (peerConnectRetryInterval < 0 || !e.isRetryable()) {
            		log.info("Failed connecting to server " + peerAddress + ": " + e + ". Not retrying");
            		keeptrying = false;
            		this.abboeServer.peerManager.setState(peerAddress, PeerState.FAILED_FOR_GOOD);
            	}
            	else {
	            	log.info("Failed connecting to server " + peerAddress + ", retrying in " + this.abboeServer.args.getPeerConnectRetryInterval()+ " seconds");
	            	this.abboeServer.peerManager.setState(peerAddress, PeerState.WAITING_FOR_RETRY);
	            	try {
	            		Thread.sleep(this.abboeServer.args.getPeerConnectRetryInterval() * 1000);
	            	}
	            	catch (InterruptedException ie) {
	            		log.warn("interrupted while waiting for connection retry");
	            	}
	            	
	            	this.abboeServer.peerManager.setState(peerAddress, PeerState.RETRYING_CONTACT);
            	}
            	continue;
            }
            	     
            // connected successfully !
            	                      
            try {
            	log.info("Subscribing to peer: " + peerAddress.getName());
                subscribe();
            }
            catch (SubscribeException e) {
            	// first, clean up the connection
            	try {
            		socket.close();
            	}
            	catch (IOException closeEx) {
            		// no action possible
            	}
            	socket = null;
            		            		            	
            	if (peerConnectRetryInterval < 0 || !e.isRetryable()) {
            		log.info("Failed subscribing to server " + peerAddress + ": " + e + ". Not retrying");
            		keeptrying = false;
            		this.abboeServer.peerManager.setState(peerAddress, PeerState.FAILED_FOR_GOOD);
            	}
            	else {
	            	log.info("Failed subscribing to server " + peerAddress + ", retrying in " + this.abboeServer.args.getPeerConnectRetryInterval() + " seconds");
	            	this.abboeServer.peerManager.setState(peerAddress, PeerState.WAITING_FOR_RETRY);
	            	try {
	            		Thread.sleep(this.abboeServer.args.getPeerConnectRetryInterval() * 1000);
	            	}
	            	catch (InterruptedException ie) {
	            		log.warn("interrupted while waiting for subscribe retry");
	            	}
	            	
	            	this.abboeServer.peerManager.setState(peerAddress, PeerState.RETRYING_CONTACT);
            	}
            	continue;	            	
            }	            	           

        }
        
        log.info("Giving up <connecting to peer: " + peerAddress);
        
    }
}