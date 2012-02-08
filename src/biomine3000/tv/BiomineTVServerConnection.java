package biomine3000.tv;

import biomine3000.objects.*;

import util.dbg.ILogger;

import java.io.IOException;
import java.net.Socket;
import java.rmi.UnknownHostException;

import org.json.JSONException;


public class BiomineTVServerConnection extends ABBOEConnection {
              
    public BiomineTVServerConnection(BiomineTV tv, ILogger log) throws IOException, UnknownHostException, JSONException {
        super(new ClientParameters("BiomineTV", ClientReceiveMode.NO_ECHO, Subscriptions.ALL, true), log);        
    }

    public void init(Socket socket) throws IOException {
        super.init(socket, new ObjectHandler());       
    }                          
    
    /** Client receive buzinezz logic contained herein */
    private class ObjectHandler implements ABBOEConnection.BusinessObjectHandler {

        @Override
        public void handleObject(BusinessObject bo) {
            String formatted = Biomine3000Utils.formatBusinessObject(bo);
            System.out.println(formatted);            
        }

        @Override
        public void connectionTerminated() {
//            terminateStdinReadLoopIfNeeded();
        }

        @Override
        public void connectionTerminated(Exception e) {
            log.error(e);
//            terminateStdinReadLoopIfNeeded();
        }
        
    }       
                 
}

