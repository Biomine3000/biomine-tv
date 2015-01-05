package org.bm3k.abboe.server;

import java.util.Arrays;
import java.util.List;

/** State of our communications with a PRE-KNOWN peer.
 * 
 * State transitions:
 *  NOT_CONTACTED => CONTACTING_AT_STARTUP (try to connect to all peers during startup)
 *  CONTACTING_AT_STARTUP => CONNECTED (successful) 
 *  CONTACTING_AT_STARTUP => WAITING_FOR_RETRY (failed contacting, retrying in some time)
 *  CONTACTING_AT_STARTUP => FAILED_FOR_GOOD
 *  CONNECTED => WAITING_FOR_RETRY (connection closed for any reason)
 *  WAITING_FOR_RETRY => RETRYING_CONTACT (wait timeout passed)
 *  RETRYING_CONTACT => CONNECTED (retry succeeded)
 *  RETRYING_CONTACT => WAITING_FOR_RETRY (retry failed)
 *  RETRYING_CONTACT => FAILED_FOR_GOOD (is this needed?)
 *  * => CONNECTED (peer contacted us first; possible even if our own connecting is deemed failed for good)
 * */
enum PeerState {
    NOT_CONTACTED,
    CONTACTING_AT_STARTUP,    
    WAITING_FOR_RETRY,
    RETRYING_CONTACT,
    CONNECTED,
    FAILED_FOR_GOOD;
           
    static {
    	NOT_CONTACTED.setAllowedTransitions(CONTACTING_AT_STARTUP);
    	CONTACTING_AT_STARTUP.setAllowedTransitions(CONNECTED, WAITING_FOR_RETRY, FAILED_FOR_GOOD, CONNECTED);    	
    	CONNECTED.setAllowedTransitions(WAITING_FOR_RETRY, CONNECTED);
    	WAITING_FOR_RETRY.setAllowedTransitions(RETRYING_CONTACT, CONNECTED);
    	RETRYING_CONTACT.setAllowedTransitions(CONNECTED, WAITING_FOR_RETRY, RETRYING_CONTACT, CONNECTED);
    	FAILED_FOR_GOOD.setAllowedTransitions(CONNECTED);;     	    
    }
    
    private List<PeerState> allowedTransitions;
    
    private void setAllowedTransitions(PeerState... allowedDestionationStates) {
    	this.allowedTransitions = Arrays.asList(allowedDestionationStates);
    }
    
    public boolean allowTransitionTo(PeerState newState) {
    	return allowedTransitions.contains(newState);
	}  
    
}
