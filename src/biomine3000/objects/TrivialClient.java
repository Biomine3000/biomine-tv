package biomine3000.objects;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.rmi.UnknownHostException;

import util.dbg.ILogger;
import util.dbg.StdErrLogger;

public class TrivialClient extends AbstractClient {
    
    String user;
    
    /** Call {@link mainReadLoop()} to perform actual processing */
    TrivialClient(Socket socket, ILogger log) throws IOException, UnknownHostException {
        super(socket, "TrivialClient", true, false, log);//        
    }

    private void mainReadLoop() throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String line = br.readLine();
        while (line != null) {    
            BusinessObject sendObj = new PlainTextObject(line);
            sendObj.getMetaData().setSender(user);
            // log.dbg("Sending object: "+sendObj );  
            send(sendObj);
            line = br.readLine();
        }
    }
    
    @Override
    public void handle(RuntimeException e) {
        log.error(e);        
    }

    @Override
    public void objectReceived(BusinessObject bo) {
        String sender = bo.getMetaData().getSender();
        if (sender == null) {
            sender = "<anonymous>";
        }
        else {
            sender = "<"+sender+">";
        }
        System.out.println(sender+" "+bo);        
    }    
    
    @Override
    public void noMoreObjects() {
        // TODO Auto-generated method stub        
    }

    @Override
    protected void handleException(Exception e) {
        log.error(e);        
    }
    
    public static void main(String args[]) throws Exception {
        Socket socket = Biomine3000Utils.connectToServer(args);
        TrivialClient client = new TrivialClient(socket, new StdErrLogger());
        client.mainReadLoop();
    }
}
