package org.bm3k.abboe.server;

/** State of our communications wit a known peer.
 * 
 * State transitions:
 *  NOT_CONTACTED => CONTACTING_AT_STARTUP (try to connect to all peers during startup)
 *  CONTACTING_AT_STARTUP => CONNECTED (successful) 
 *  CONTACTING_AT_STARTUP => WAITING_FOR_RETRY (failed contacting, retrying in some time)
 *  CONNECTED => WAITING_FOR_RETRY (connection closed for any reason)
 *  WAITING_FOR_RETRY = RETRYING_CONTACT (wait timeout passed)
 *  RETRYING_CONTACT => CONNECTED (retry succeeded)
 *  RETRYING_CONTACT => WAITING_FOR_RETRY (retry failed)
 *  FAILED_FOR_GOOD (is this needed?)
 *  * => CONNECTED (peer contacted us first)
 * */
enum PeerState {
    NOT_CONTACTED,
    CONTACTING_AT_STARTUP,
    CONNECTED,
    WAITING_FOR_RETRY,
    RETRYING_CONTACT,
    FAILED_FOR_GOOD;
}
