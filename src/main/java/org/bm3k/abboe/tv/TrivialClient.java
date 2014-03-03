package org.bm3k.abboe.tv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.List;

import org.bm3k.abboe.common.*;
import org.bm3k.abboe.objects.BusinessObject;
import org.bm3k.abboe.objects.BusinessObjectEventType;
import org.bm3k.abboe.objects.LegacyBusinessObject;
import org.bm3k.abboe.objects.PlainTextObject;
import org.json.JSONException;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.StringUtils;

public class TrivialClient {
    private final Logger log = LoggerFactory.getLogger(TrivialClient.class);
              
    private static final ClientParameters CLIENT_PARAMS =
            new ClientParameters("TrivialClient", ClientReceiveMode.NO_ECHO,
                                 Subscriptions.make("text/plain"), false);
    
    private ABBOEConnection connection;
    private boolean stopYourStdinReading = false;    
    private SystemInReader systemInReader;
    private String user;
    
    /** Note that superclasses also have their own state related to the connection to the server */
    private StdinReaderState stdinReaderState;
    
    /** Call {@link startMainReadLoop()} to perform actual processing */
    public TrivialClient(Socket socket, String user) throws IOException, JSONException {
        this.user = user;
        this.stdinReaderState = StdinReaderState.NOT_YET_READING;
        ClientParameters clientParams = new ClientParameters(CLIENT_PARAMS);
        clientParams.sender = Biomine3000Utils.getUser();
        this.connection = new ABBOEConnection(clientParams, socket);
        this.connection.init(new ObjectHandler());
        this.connection.sendClientListRequest();
    }
       
    /** Start a SystemInReader thread */
    private void startMainReadLoop() {
        systemInReader = new SystemInReader();
        systemInReader.start();
    }
    
    private class SystemInReader extends Thread {
        public void run() {
            try {
                stdInReadLoop();
            }
            catch (IOException e)  {
                log.error("IOException in SystemInReader", e);
                log.info("Requesting closing output if needed...");
                connection.requestCloseOutputIfNeeded();
            }
        }
    }
    
    /**
     * FOO: it does not seem to be possible to interrupt a thread waiting on system.in, even
     * by closing System.in... Thread.interrupt does not work either...
     * it seems that it is not possible to terminate completely cleanly, then.
     */
    private void stdInReadLoop() throws IOException {
        stdinReaderState = StdinReaderState.READING;
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));            
        String line = br.readLine();
        while (line != null && !stopYourStdinReading) {
            if (line.equals("stop")) {
                stopYourStdinReading = true;
                break;
            }
            else if (line.equals("s")) {
                stopYourStdinReading = true;
                break;
            }
            else if (line.equals("clients")) {
                connection.sendClientListRequest();
            }
            else {
                LegacyBusinessObject sendObj = new PlainTextObject(line);
                sendObj.getMetadata().setSender(user);
                // log.dbg("Sending object: "+sendObj );  
                connection.send(sendObj);                
            }
            line = br.readLine();
        }
        
        log.info("Tranquilly finished reading stdin");
        log.info("Likewise harmoniously requesting closing output...");
        stdinReaderState = StdinReaderState.FINISHED_READING;
        connection.requestCloseOutputIfNeeded();        
    }
    
    /** To be called when connection to server has already been terminated */
    private void terminateStdinReadLoopIfNeeded() {
        
        if (stdinReaderState == StdinReaderState.READING) {
            stopYourStdinReading = true;
            
            // actually, setting the above flag is not enough, so let's just:
            log.debug("Forcibly exiting");
            System.exit(0);
        }
        
        // NOTE: there seems to be NO SAFE WAY to terminate a thread that is waiting on reading System.in
        // the only thing we can do here is to wait for a line to be read, after which actual 
        // closing can occur.
        
        // - Thread.interrupt does not work (nothing happens)
        // - System.in.notify does not work (error if not a owner of the monitor; trying to become owner by 
        //                                   synchronizing on System.in waits for the current read to complete)
        // - closing system.in does not work (it only leads to a null being read AFTER finishing the current read...)
        
        // TODO: actually, maybe we should just exit, after ensuring that all other activities have finished...         
    }
    
    /** Client receive buzinezz logic contained herein */
    private class ObjectHandler implements ABBOEConnection.BusinessObjectHandler {

        @Override
        public void handleObject(BusinessObject bo) {
            if (bo.isEvent()) {                
                BusinessObjectEventType et = bo.getMetadata().getKnownEvent();
                if (et == BusinessObjectEventType.CLIENTS_LIST_REPLY) {
                    String registeredAs = bo.getMetadata().getString("you");
                    System.out.println("This client registered on the server as: "+registeredAs);
                    List<String> clients = bo.getMetadata().getList("others");
                    if (clients.size() == 0) {
                        System.out.println("No other clients");
                    }
                    else {
                        System.out.println("Other clients:");
                        System.out.println("\t"+StringUtils.collectionToString(clients, "\n\t"));
                    }
                    
                }
                else if (et == BusinessObjectEventType.CLIENTS_REGISTER_REPLY) {
                    System.out.println("Registered successfully to the server");
                }
                else if (et == BusinessObjectEventType.CLIENTS_REGISTER_NOTIFY) {
                    String name = bo.getMetadata().getName();
                    System.out.println("Client "+name+" registered to ABBOE");
                }
                else if (et == BusinessObjectEventType.CLIENTS_PART_NOTIFY) {
                    String name = bo.getMetadata().getName();
                    System.out.println("Client "+name+" parted from ABBOE");
                }
                else {
                    String formatted = Biomine3000Utils.formatBusinessObject(bo);
                    System.out.println(formatted);
                }
                
            }
            else {
                String formatted = Biomine3000Utils.formatBusinessObject(bo);
                System.out.println(formatted);
            }
        }

        @Override
        public void connectionTerminated() {
            terminateStdinReadLoopIfNeeded();
        }

        @Override
        public void connectionTerminated(Exception e) {
            log.error("Connection terminated", e);
            terminateStdinReadLoopIfNeeded();
        }
        
    }       
        
    public static void main(String pArgs[]) throws Exception {
        Biomine3000Args args = new Biomine3000Args(pArgs, true);
        Socket socket = Biomine3000Utils.connectToServer(args);        
        String user = args.getUser();
        if (user == null) {
            user = "anonymous";
        }
        TrivialClient client = new TrivialClient(socket, user);
        client.startMainReadLoop();
    }
    
    private enum StdinReaderState {
        NOT_YET_READING,
        READING,
        FINISHED_READING;
    }
}
