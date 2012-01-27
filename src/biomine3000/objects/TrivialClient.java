package biomine3000.objects;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.rmi.UnknownHostException;

import org.json.JSONException;


import util.dbg.ILogger;
import util.dbg.Logger;

public class TrivialClient extends AbstractClient {
        
    boolean stdinClosed = false;
    
    private SystemInReader systemInReader;
    
    /** Call {@link mainReadLoop()} to perform actual processing */
    TrivialClient(Socket socket, ILogger log) throws IOException, UnknownHostException, JSONException {
        super("TrivialClient", ClientReceiveMode.NO_ECHO, Subscriptions.make("text/plain"), false, log);        
        init(new ReaderListener(), socket);
    }

    private void startMainReadLoop() {
        systemInReader = new SystemInReader();
        systemInReader.start();
    }
    
    private class SystemInReader extends Thread {
        public void run() {
            try {
                mainReadLoop();
            }
            catch (IOException e)  {
                log.error(e);
                log.info("Requesting closing output...");
                requestCloseOutput();
            }
        }
    }
    
    /**
     * FOO: it does not seem to be possible to interrupt a thread waiting on system.in, even
     * by closing System.in... Thread.interrupt does not work either...
     */
    private void mainReadLoop() throws IOException {
        try {                        
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String line = br.readLine();
            while (line != null) {    
                BusinessObject sendObj = new PlainTextObject(line);
                sendObj.getMetaData().setSender(user);
                // log.dbg("Sending object: "+sendObj );  
                send(sendObj);
                line = br.readLine();
            }
            
            log.info("Finished reading stdin");
            log.info("Requesting closing output...");
            requestCloseOutput();
        }
        catch (IOException e) {
            if (e.getMessage().equals("Stream closed") && stdinClosed) {
                // this was to be expected => no action 
            }
            else {
                log.error("Failed reading stdin", e);
                log.info("Requesting closing output...");
                requestCloseOutput();                               
            }
        }

    }
    
    private void terminateStdinReadLoop() {
        
        stdinClosed = true;
        
        log.dbg("Interruting stdin reader thread...");
        systemInReader.interrupt();
        log.dbg("Done trying to interrupt stdin reader thread");
        
        // TODO: come up with a better way to terminate main read loop
//        try {
//            System.in.close();
//        }
//        catch (IOException e) {
//            log.error("Failed closing System.in", e);
//        }
    }
    
    private class ReaderListener extends BusinessObjectReader.DefaultListener {
    
        @Override
        public void handle(RuntimeException e) {
            log.error(e);
            
            terminateStdinReadLoop();
            // delegate to outer class superclass AbstractClient
            handleNoMoreObjects();
        }
    
        @Override
        public void objectReceived(BusinessObject bo) {
            String sender = bo.getMetaData().getSender();            
            String channel = bo.getMetaData().getChannel();
            if (channel != null) {
                channel = channel.replace("MESKW", "");
            }
            String prefix;
            
            if (sender == null && channel == null) {
                // no sender, no channel
                prefix = "<anonymous>";
            }
            else if (sender != null && channel == null) {
                // only sender
                prefix = "<"+sender+">";
            }
            else if (sender == null && channel != null) {
                // only channel
                prefix = "<"+channel+">";
            }
            
            else {
                // both channel and sender
                prefix = "<"+channel+"-"+sender+">";
            }
            
            System.out.println(prefix+" "+bo);        
        }    
        
        @Override
        public void noMoreObjects() {
            // server has closed connection?
            
            terminateStdinReadLoop();
            
            // delegate to outer class superclass AbstractClient
            handleNoMoreObjects();

        }
    
        @Override
        protected void handleException(Exception e) {
            if (e.getMessage().equals("Connection reset")) {
                log.info("Connection reset by the server");
            }
            else {
                log.error(e);        
            }
            
            terminateStdinReadLoop();
            // delegate to outer class superclass AbstractClient
            handleNoMoreObjects();
        }
    }
        
    public static void main(String args[]) throws Exception {
        Socket socket = Biomine3000Utils.connectToServer(args);
        Biomine3000Utils.configureLogging(args);
        TrivialClient client = new TrivialClient(socket, new Logger.ILoggerAdapter());
//        client.mainReadLoop();
        client.startMainReadLoop();
    }
}
