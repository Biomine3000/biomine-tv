package org.bm3k.abboe.server;

import org.bm3k.abboe.common.ServerAddress;
import org.bm3k.abboe.common.Subscriptions;

/** 
 * Info for a peer, be it pre-known or one adhocly connected to us.
 * Only exists for peers that have subscibed to us / been subscibed to by us.
 */
public class PeerInfo {
	private final ServerAddress address;
	
	public PeerInfo(ServerAddress address, String routingId, Subscriptions subscriptions, SubscribeDirection subscribeDirection) {		
		this.address = address;
		this.routingId = routingId;
		this.subscriptions = subscriptions;
		this.subscribeDirection = subscribeDirection;
	}

	private final String routingId;
	private final Subscriptions subscriptions; 
	private final SubscribeDirection subscribeDirection;
	
	/** only available for pre-known peers; null for adhoc peers */
	public ServerAddress getAddress() {
		return address;
	}
	
	/** only available for pre-known peers; null for adhoc peers */
	public String getRoutingId() {
		return routingId;
	}
	
	/** specifies objects to be sent to the server */
	public Subscriptions getSubsciptions() {
		return subscriptions;
	}
	
	/** Might or might not be relevant for some processing. Have this anyway, to facilitate debugging. */ 
	public SubscribeDirection getSubscribeDirection() {
		return subscribeDirection;
	}

}
