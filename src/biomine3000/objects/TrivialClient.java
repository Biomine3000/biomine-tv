package biomine3000.objects;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.rmi.UnknownHostException;

import org.json.JSONException;


import util.dbg.ILogger;
import util.dbg.Logger;

public class TrivialClient extends DefaultClient {
        
    boolean stopYourStdinReading = false;
    
    private SystemInReader systemInReader;

    private String user;
    
    private State state;
    
    /** Call {@link mainReadLoop()} to perform actual processing */
    public TrivialClient(String user, ILogger log) throws IOException, UnknownHostException, JSONException {
        super(new ClientParameters("TrivialClient", ClientReceiveMode.NO_ECHO, Subscriptions.make("text/plain"), false), log);
        this.user = user;
        this.state = State.NOT_YET_READING;
    }

    public void init(Socket socket) throws IOException {
        super.init(socket, new ObjectHandler());       
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
                requestCloseOutputIfNeeded();
            }
        }
    }
    
    /**
     * FOO: it does not seem to be possible to interrupt a thread waiting on system.in, even
     * by closing System.in... Thread.interrupt does not work either...
     * it seems that it is not possible to terminate completely cleanly, then.
     */
    private void stdInReadLoop() throws IOException {
        state = State.READING;
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));            
        String line = br.readLine();
        while (line != null && !stopYourStdinReading) {    
            BusinessObject sendObj = new PlainTextObject(line);
            sendObj.getMetaData().setSender(user);
            // log.dbg("Sending object: "+sendObj );  
            send(sendObj);
            line = br.readLine();
        }
        
        log.info("Tranquilly finished reading stdin");
        log.info("Likewise harmoniously requesting closing output...");
        state = State.FINISHED_READING;
        requestCloseOutputIfNeeded();        
    }
    
    /** To be called when connection to server has already been terminated */
    private void terminateStdinReadLoopIfNeeded() {
        
        if (state == State.READING) {
            stopYourStdinReading = true;
            
            // actually, setting the above flag is not enough, so let's just:
            log.dbg("Forcibly exiting");
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
    private class ObjectHandler implements DefaultClient.BusinessObjectHandler {

        @Override
        public void handleObject(BusinessObject bo) {
            String formatted = Biomine3000Utils.formatBusinessObject(bo);
//            String sender = bo.getMetaData().getSender();            
//            String channel = bo.getMetaData().getChannel();
//            if (channel != null) {
//                channel = channel.replace("MESKW", "");
//            }
//            String prefix;
//            
//            if (sender == null && channel == null) {
//                // no sender, no channel
//                prefix = "<anonymous>";
//            }
//            else if (sender != null && channel == null) {
//                // only sender
//                prefix = "<"+sender+">";
//            }
//            else if (sender == null && channel != null) {
//                // only channel
//                prefix = "<"+channel+">";
//            }
//            
//            else {
//                // both channel and sender
//                prefix = "<"+channel+"-"+sender+">";
//            }
//            
//            System.out.println(prefix+" "+bo);
            System.out.println(formatted);
            
        }

        @Override
        public void connectionTerminated() {
            terminateStdinReadLoopIfNeeded();
        }

        @Override
        public void connectionTerminated(Exception e) {
            log.error(e);
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
        TrivialClient client = new TrivialClient(user, new Logger.ILoggerAdapter("TrivialClient: "));
        client.init(socket);
        client.startMainReadLoop();
    }
    
    private enum State {
        NOT_YET_READING,
        READING,
        FINISHED_READING;
    }
}
