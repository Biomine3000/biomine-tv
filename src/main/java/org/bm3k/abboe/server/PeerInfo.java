package org.bm3k.abboe.server;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.bm3k.abboe.common.ServerAddress;

/**
 * State of an ABBOEServer's communications with it's (pre-known) peers. These are the peers that we try to connect to.
 * As they can also connect us, some measures have to be taken to ensure that only one connection gets finalized.
 */ 
class PeerInfo {
                            
    final Set<ServerAddress> knownPeers;      
    Map<ServerAddress, PeerState> peerStates;
    
    PeerInfo(Collection<ServerAddress> addresses) {
        this.knownPeers = Collections.unmodifiableSet(new LinkedHashSet<>(addresses));
        for (ServerAddress address: addresses) {
            peerStates.put(address, PeerState.NOT_CONTACTED);
        }
    }
    
    synchronized void setState(ServerAddress peerAddress, PeerState state) {
        // TODO: check state transition?
        peerStates.put(peerAddress, state);
    }
    
    /** Check if a connection has been triead at least once for all peers. Only once this is the case, shall the server start accepting clients */ 
    synchronized boolean connectionAttemptedForAllPeers() {
        for (ServerAddress peerAddress: knownPeers) {
            PeerState state = peerStates.get(peerAddress);
            if (state == PeerState.NOT_CONTACTED || state == PeerState.CONTACTING_AT_STARTUP) {
                // doing the initial startup
                return false;
            }
        }
        return true;
    }
    
    public Set<ServerAddress> knownPeers() {
        return knownPeers;
    }
    
}

