package org.bm3k.abboe.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bm3k.abboe.common.ServerAddress;
import org.bm3k.abboe.common.Subscriptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.StringUtils;

/**
 * State of an ABBOEServer's communications with it's (pre-known and non-preknown) peers. These are the peers that we try to connect to.
 * As they can also connect us, some measures have to be taken to ensure that only one connection gets finalized.
 * 
 * TODO: elaborate the identity of peers. Before connecting peers are known only by their ServerAddress.
 * After connecting, they should be known by their routing id.
 */ 
class PeerManager {
                            
	private final Logger log = LoggerFactory.getLogger(PeerManager.class);	
    private final Set<ServerAddress> preKnownPeerAddresses;  // addresses for pre-known peers      
    private Map<ServerAddress, PeerState> peerStates;        // connection states for preknown peers
    private Map<String, PeerInfo> peerInfoByRoutingId;      // info for contacted peers
    private List<PeerStateListener> stateListeners;    
    
    /** Initialize peer info with knowledge of known peers. No connections exist at this stage */
    PeerManager(Collection<ServerAddress> addresses) {    	
        this.preKnownPeerAddresses = Collections.unmodifiableSet(new LinkedHashSet<>(addresses));
        peerStates = new LinkedHashMap<>();
        for (ServerAddress address: addresses) {
            peerStates.put(address, PeerState.NOT_CONTACTED);
        }
        log.info("Initialized peer info: " + StringUtils.collectionToString(preKnownPeerAddresses, ", "));
        
        peerInfoByRoutingId = new LinkedHashMap<>();
        stateListeners = new ArrayList<>();
    }
    
    synchronized void setState(ServerAddress peerAddress, PeerState state) {    	
    	PeerState oldState = peerStates.get(peerAddress);
    	if (oldState == null) {
    		throw new RuntimeException("No state for peer: " + peerAddress + " (trying to perform state transition to: " + state);
    	}
    	
    	if (oldState.allowTransitionTo(state)) {
    		log.info("Performing state transition for " + peerAddress + ": " + oldState + " => " + state);    
    		peerStates.put(peerAddress, state);
    		notifyStateChange();
    	}
    	else {
    		throw new RuntimeException("Invalid state transition attempt for " + peerAddress + ": " + oldState + " => " + state); 
    	}
    }
    
    private void notifyStateChange() {
    	for (PeerStateListener listener: stateListeners) {
    		listener.statesChanged();
    	}
    }
    
    /** Check if a connection has been tried at least once for all peers. Only once this is the case, shall the server start accepting clients */ 
    synchronized boolean connectionAttemptedForAllPeers() {
        for (ServerAddress peerAddress: preKnownPeerAddresses) {
            PeerState state = peerStates.get(peerAddress);
            if (state == PeerState.NOT_CONTACTED || state == PeerState.CONTACTING_AT_STARTUP) {
                // doing the initial connects
                return false;
            }
        }
        return true;
    }
    
    /** 
     * Try to register a subscribed or subscribed-to peer. Note that caller must not try to check existence of peer beforehand, as checking
     * for existence and registering must be done as a single atomic operation. Instead, it is a valid situation to call this 
     * and fail due to a DuplicatePeerException. In this case, client must terminate the connection being registered and perform appropriate cleanup.
     * 
     * @throws DuplicatePeerException if peer with same address already exists.
     * @return info for the newly registered peer.
     */
    public synchronized PeerInfo registerPeer(ServerAddress address, String routingId, Subscriptions subscriptions, SubscribeDirection subscribeDirection) 
    		throws DuplicatePeerException {
    	
    	if (peerInfoByRoutingId.containsKey(routingId)) {
    		throw new DuplicatePeerException(routingId);
    	}
    	
    	PeerInfo peerInfo = new PeerInfo(address, routingId, subscriptions, subscribeDirection);
    	peerInfoByRoutingId.put(routingId,  peerInfo);
    	return peerInfo;
    }
    
    public synchronized void removePeer(String routingId) {     		    	    	
    	peerInfoByRoutingId.remove(routingId);
    }
    
    public Set<ServerAddress> knownPeers() {
        return preKnownPeerAddresses;
    }
    
    interface PeerStateListener {
    	public void statesChanged();
    }
    
    public void addStateListener(PeerStateListener listener) {
    	stateListeners.add(listener);
    }
    
    
    @SuppressWarnings("serial")
	public static class DuplicatePeerException extends Exception {    		

		DuplicatePeerException(String routingId) {
    		super("There already exists a peer with routing id " + routingId);
    	}
    }
}

