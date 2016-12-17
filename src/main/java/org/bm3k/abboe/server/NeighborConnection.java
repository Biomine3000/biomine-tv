package org.bm3k.abboe.server;

import static org.bm3k.abboe.objects.BusinessObjectEventType.*;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.LinkedHashSet;
import java.util.List;

import org.bm3k.abboe.common.Biomine3000Utils;
import org.bm3k.abboe.common.BusinessObjectReader;
import org.bm3k.abboe.common.InvalidBusinessObjectException;
import org.bm3k.abboe.common.Subscriptions;
import org.bm3k.abboe.objects.BOB;
import org.bm3k.abboe.objects.BusinessObject;
import org.bm3k.abboe.objects.BusinessObjectEventType;
import org.bm3k.abboe.objects.BusinessObjectMetadata;
import org.bm3k.abboe.server.ABBOEServer.Role;
import org.bm3k.abboe.server.ABBOEServer.State;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.net.NonBlockingSender;

/**
 * Connection to a neighboring node. Each connection has a dedicated thread for reading and sending objects.
 */
class NeighborConnection implements NonBlockingSender.Listener {                       

	private final Logger log = LoggerFactory.getLogger(NeighborConnection.class);
	
	private final ABBOEServer abboeServer;
	private final Socket socket;
	private final BufferedInputStream is;
	private final OutputStream os;
    boolean subscribed = false;
    private boolean senderFinished;
    private boolean receiverFinished;
    /** Please do not call the send method of this sender directly, even within this class, except in the one dedicated place */
    private NonBlockingSender sender;
    private BusinessObjectReader reader;
    private final ReaderListener readerListener;        
    Subscriptions subscriptions = new Subscriptions();        
    boolean closed;
    String routingId;  // primary routing id of the neighbor (it is possibly believed that a node can have plurality of routing ids)

    /** actual name of neighbor program, not including user or addr */
    String neighborName;
    
    String user;
    
    /* Obtained by getRemoteSocketAddress() */
    private String addr;
    /** Derived from neighborName, user and addr */
    String name;                
    
    /** services implemented by neighbor */
    LinkedHashSet<String> services = new LinkedHashSet<String>();
    Role role;
    

    
    
    /** Creates sender, but not yet reader. Reader must be created later by calling {@link #startReaderThread()} */
    NeighborConnection(ABBOEServer abboeServer, Socket socket) throws IOException {
        this.abboeServer = abboeServer;
		senderFinished = false;
        receiverFinished = false;
        this.socket = socket;
        addr = socket.getRemoteSocketAddress().toString();
        initName();
        is = new BufferedInputStream(socket.getInputStream());
        os = socket.getOutputStream();
        sender = new NonBlockingSender(socket, this);
        sender.setName(name);
        readerListener = new ReaderListener(this);
        closed = false;
        
        synchronized(this.abboeServer) {
            this.abboeServer.neighbors.add(this);
        }
    }

    String getAddress() {
    	return addr;
    }
    
    private void initName() {
        StringBuffer buf = new StringBuffer();
        if (neighborName != null) {
            buf.append(neighborName+"-");
        }
        if (user != null) {
            buf.append(user+"-");
        }
        buf.append(addr);
        name = buf.toString();
    }
    
    
    public synchronized void setNeighborName(String neighborName) {
        this.neighborName = neighborName;
        initName();
        sender.setName("sender-"+this.name);
        reader.setName("reader-"+this.name);
    }
                               
    synchronized void setUser(String user) {
        this.user = user;
        initName();
        sender.setName("sender-"+name);
        reader.setName("reader-"+name);
    }


    /** 
     * Store info for a peer to which a connection was initiated by us ("outgoing" peer connection). 
     * 
     * Serves approximately similar purpose as {@link #handleRoutingSubscribeEvent(NeighborConnection, BusinessObject)};
     * in this case, we have already read the peer info while connecting to the peer, and will not receive the info 
     * through a subscribe event. 
     */
    synchronized void setPeerInfo(PeerInfo peerInfo) {
    	if (peerInfo.getSubscribeDirection() != SubscribeDirection.OUTGOING) {
    		throw new RuntimeException("Cannot set peer info for an incoming peer connection");
    	}
    	
    	// peerInfo.getAddress();
    	this.routingId = peerInfo.getRoutingId();
    	this.subscriptions =  peerInfo.getSubsciptions();
    	
    }
    
    /**
     * Send a warning message to a neighbor (natures=[warning,message], contenttype=plaintext, sender=java-A.B.B.O.E.) 
     * Sending is not conditional on subscriptions (they should be checked by this point if needed).
     */
    private void sendWarning(String text) {
        BusinessObject reply = BOB.newBuilder()
                .natures("message", "warning")
                .attribute("to", this.routingId)
                .attribute("sender", abboeServer.getServerAddress().getName())
                .payload(text).build();
        send(reply);
    }    
           
    Socket getSocket() {
    	return socket;
    }
    
    /**
     * Send an error reply to processing of an event RECEIVED FROM THIS NEIGHBOR.
     * 
     * See leronen-tv-todo for specs!
     * 
     * Reply will always be an event=error. It will also have natures "message", "error".
     * 
     * Send a error message to neighbor (natures=[error,message], contenttype=plaintext, sender=java-A.B.B.O.E.) 
     * Sending is not conditional on subscriptions (they should be checked by this point if needed).
     **/
    void sendErrorReply(String errorMessage, BusinessObject receivedObject) {                       
        String inReplyTo = receivedObject.getMetadata().getString("id");
                    
        if (inReplyTo == null) {
            this.abboeServer.log.warn("Sending an error reply to an object with unknown id: "+receivedObject);
        }
        
        BusinessObject reply = BOB.newBuilder()
                .event(BusinessObjectEventType.ERROR)
                .natures("message", "error")                    
                .attribute("in-reply-to", inReplyTo)
                .attribute("sender", abboeServer.getServerAddress().getName())
                .payload(errorMessage).build();
        
        String to;
        
        if (this.role == Role.CLIENT) {
            to = this.routingId;                                            
        }
        else {
            JSONArray route = receivedObject.getMetadata().asJSON().optJSONArray("route");                
            if (route == null) {
                // no route in object from server; actually this should mean that we are sending error reply namely to said server
                // (a horrendous implicit assumption)), and thus we can just use that very server as "to"
                to = this.routingId;
            }
            else {
                to = route.getString(0);
            }                
        }
        
        reply.getMetadata().put("to", to);
         
        this.abboeServer.sendServerGeneratedObject(reply);
    }
    
    /**
     * Send a message to client (nature=message, contenttype=plaintext, sender=java-A.B.B.O.E.) 
     * Sending is not conditional on subscriptions or routing (they should be checked by this point if needed).
     **/
    void sendMessage(String text) {                                                           
        // log("Sending message to client " + this + ": " + text);
        BusinessObject reply = BOB.newBuilder()
                .natures("message")
                .attribute("sender", abboeServer.getServerAddress().getName())
                .payload(text).build();
        send(reply);
    }                                      
    
    /**
     * Put object to queue of messages to be sent (to this one client) and return immediately.
     * Sending is not conditional on subscriptions or routing (they should be checked by this point if needed).
     * Assume send queue has unlimited capacity.
     */
    void send(BusinessObject bo) {
      if (senderFinished) {
          this.abboeServer.log.warn("No more sending business for client "+this);
          return;
      }

      if (bo.hasNature("error")) {
          this.abboeServer.log.error("Sending to "+this+" : "+bo);
      }
      else if (bo.hasNature("warning")) {
          this.abboeServer.log.warn("Sending to: "+this+" : "+bo);
      }
      else {
          this.abboeServer.log.info("Sending to: "+this+" : "+bo);
      }
      
      try {
          sender.send(bo.getMetadata().toString().getBytes("UTF-8"), false);
          byte[] payload = bo.getPayload();
          if (payload != null) {              
              sender.send(Biomine3000Utils.NULL_BYTE_ARRAY, false);                 
              sender.send(bo.getPayload(), true);
          }
          else {
              sender.send(Biomine3000Utils.NULL_BYTE_ARRAY, true);
          }
      }
      catch (IOException e) {
          this.abboeServer.log.error("Failed sending to client "+this, e);
          doSenderFinished();
      }
  }

    
    synchronized void registerServices(List<String> names) {
        services.addAll(names);
    }

    void startReaderThread() {
        reader = new BusinessObjectReader(is, readerListener, name);
        Thread readerThread = new Thread(reader);
        readerThread.setName("reader-" + name);
        readerThread.start();
    }

    /**
     * Forcibly terminate a connection with a neighbor (supposedly called when
     * neighbor steadfastly refuses to close the connection when requested)
     */
    void forceClose() {
        if (closed) {
            error("Attempting to close a client multiple times", null);
        }
        log("Forcing closing of connection with client: "+this);

        try {
            // closing socket also closes streams if needed
            socket.close();
        }
        catch (IOException e) {
            error("Failed closing socket", e);
        }

        synchronized(this.abboeServer) {
            this.abboeServer.neighbors.remove(this);
            closed = true;
            if (this.abboeServer.getState()== State.SHUTTING_DOWN && this.abboeServer.neighbors.size() == 0) {
                // last neighbor closed and we are shutting down, finalize shutdown sequence...
                this.abboeServer.log.info("No more neighbors, finalizing shutdown sequence...");
                this.abboeServer.finalizeShutdownSequence();
            }
        }
                                       
        this.abboeServer.sendServerGeneratedObject(makeRoutingDisconnectEvent());                                                                                      
    }

    /** Make event signaling the departure of this neighbor */
    private BusinessObject makeRoutingDisconnectEvent() {
        return BOB.newBuilder()
                .event(ROUTING_DISCONNECT)
                .attribute("routing-id", routingId)                            
                .payload(""+this.role + " " + this + " disconnected").build();
    }
    
    /**
     * Gracefully dispose of a single neighbor after ensuring both receiver and neighbor
     * have finished
     */
    private void doClose() {
        if (closed) {
            error("Attempting to close a connection with neighbor " + this + " multiple times", null);
        }

        log("Closing connection with "+this.role+": "+this);

        try {
            os.flush();
        }
        catch (IOException e) {
            // let's not bother to even log the exception at this stage
        }

        try {
            // closing socket also closes streams if needed
            socket.close();
        }
        catch (IOException e) {
            // let's not bother to even log the exception at this stage
        }

        synchronized(this.abboeServer) {
        	// TODO: remove from peer manager; or better, should somehow unify PeetManager and neighbors list
            this.abboeServer.neighbors.remove(this);
            closed = true;
            if (this.abboeServer.getState() == State.SHUTTING_DOWN && this.abboeServer.neighbors.size() == 0) {
                // last neighbor closed, no more neighbors, finalize shutdown sequence...
                this.abboeServer.log.info("No more neighbors, finalizing shutdown sequence...");
                this.abboeServer.finalizeShutdownSequence();
            }
        }
        
        this.abboeServer.sendServerGeneratedObject(makeRoutingDisconnectEvent());
    }
        
    /** Implement {@link NonBlockingSender.Listener#senderFinished() */
    @Override
    public void senderFinished() {
        doSenderFinished();
    }

    /**
     * Initiate shutting down of proceedings with this neighbor.
     *
     * Actually, just initiate closing of output channel. On noticing this,
     * neighbor should close its socket, which will then be noticed on this server
     * as a {@link BusinessObjectReader.Listener#noMoreObjects()} notification from the {@link #reader}.
     */
    void initiateClosingSequence(BusinessObject notification) {
        this.abboeServer.log.info("Initiating closing sequence for neighbor: "+this);
        
        this.abboeServer.log.info("Sending shutdown event to neighbor: "+this);
        if (subscriptions.pass(notification)) {
            send(notification);
        }

        sender.requestStop();
    }

    private synchronized void doSenderFinished() {
        log("doSenderFinished");
        if (senderFinished) {
            log("Already done");
            return;
        }

        senderFinished = true;

        try {
            socket.shutdownOutput();
        }
        catch (IOException e) {
            this.abboeServer.log.error("Failed closing socket output after finishing neighbor", e);
        }


        if (receiverFinished) {
            doClose();
        }
    }

    private synchronized void doReceiverFinished() {
        log("doReceiverFinished");
        if (receiverFinished) {
            log("Already done");
            return;
        }

        // request stop of neighbor
        sender.requestStop();

        receiverFinished = true;

        if (senderFinished) {
            doClose();
        }
    }
    
    private void error(String msg, Exception e) {
        this.abboeServer.log.error(name+": "+msg, e);
    }

    private void log(String msg) {
        this.abboeServer.log.info(name+": "+msg);
    }

    public String toString() {
        return name;
    }

    public void sendPong(BusinessObject bo) {
        BusinessObjectMetadata metadata = new BusinessObjectMetadata();
    
        if (bo.getMetadata().hasKey("id")) {
            metadata.put("in-reply-to", bo.getMetadata().getString("id"));
        }
    
        send(BOB.newBuilder().event(PONG).metadata(metadata).build());
    }
    
    /** Listens to a single dedicated reader thread reading objects from the input stream of a single neighbor */
    private class ReaderListener implements BusinessObjectReader.Listener {
        NeighborConnection source;

        ReaderListener(NeighborConnection neighbor) {
            this.source = neighbor;
        }

        @Override
        public void objectReceived(BusinessObject bo) {
            if (bo.isEvent()) {
                BusinessObjectEventType et = bo.getMetadata().getKnownEvent();                                                                                                                                                                       
                                                
                // assert that route is set for server-originating object, and not set for client-originating ones.
                if (source.role != null) {   
                    switch (source.role) {
                    case SERVER:
                        if (!bo.getMetadata().hasKey("route")) {
                            source.sendErrorReply("No route in object from server", bo);
                            return;
                        }
                        break;
                    case CLIENT:                        
                        if (bo.getMetadata().hasKey("route")) {
                            source.sendErrorReply("A client should not specify a route", bo);
                            return;
                        }
                        break;
                    }
                }
                
                boolean forwardEvent = true;  // does this event need to be sent to other neighbors?
                if (et != null) {
                    log.info("Received event from {} : {} ", source, bo);                    
                    if (et == SERVICES_REQUEST) {                                            
                        String serviceName = bo.getMetadata().getString("name");
                        
                        if (serviceName.equals("clients")) {
                            String request = bo.getMetadata().getString("request");
                            
                            if (request.equals("join")) {
                                abboeServer.handleClientJoinRequest(source, bo);
                            }
                            else if (request.equals("leave")) {
                                source.sendWarning("Unhandled request: clients/leave (request id: "+bo.getMetadata().get("id"));         
                            }
                            else if (request.equals("list")) {
                            	abboeServer.handleClientsListEvent(source, bo);
                            }
                            else {
                                source.sendWarning("Unknown request to clients service: " + request + " (request id: "+bo.getMetadata().get("id"));
                            }
                        }
                        else {
                            source.sendWarning("Forwarding service requests not implemented. (service=" + serviceName+ " ;request id: "+bo.getMetadata().get("id"));        
                        }
                        
                        forwardEvent = false;
                    }
                    else if (et == SERVICES_REGISTER) {
                    	abboeServer.handleServicesRegisterEvent(source, bo);
                        forwardEvent = false;
                    }
                    else if (et == ROUTING_SUBSCRIPTION) {
                    	abboeServer.handleRoutingSubscribeEvent(source, bo);
                        forwardEvent = false;
                    }
                    else if (et == PING) {
                        source.sendPong(bo);
                        forwardEvent = false;
                    }
                    else {
                        log.info("Received known event which this ABBOE implementation does not handle: {}", bo);
                    }
                }
                else {
                    log.info("Received unknown event: {}", bo.getMetadata().getEvent());
                }

                // forward the event if needed 
                if (forwardEvent) {
                    log.info("Forwarding event to all clients...");
                    abboeServer.forward(bo, source);
                }
            }
            else {
                // not an event, assume mythical "content"
                log.info("Received content: {}", Biomine3000Utils.formatBusinessObject(bo));
                                
                abboeServer.forward(bo, source);
            }
        }

        @Override
        public void noMoreObjects() {
            log.info("connectionClosed (neighbor closed connection).");
            source.doReceiverFinished();
        }

        private void handleException(Exception e) {
            if (e.getMessage() != null && e.getMessage().equals("Connection reset")) {
                log.info("Connection reset by neighbor: "+this.source);
            }
            else {
                log.error("Exception while reading objects from neighbor " + source, e);
            }
            source.doReceiverFinished();
        }

        @Override
        public void handle(IOException e) {
            handleException(e);
        }

        @Override
        public void handle(InvalidBusinessObjectException e) {
            handleException(e);
        }

        @Override
        public void handle(RuntimeException e) {
            handleException(e);
        }

        public void connectionReset() {
            log.error("Connection reset by neighbor: {}", this.source);
            source.doReceiverFinished();
        }
    }

}

