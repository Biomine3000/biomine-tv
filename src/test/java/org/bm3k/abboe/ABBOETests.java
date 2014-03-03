package org.bm3k.abboe;

import java.net.Socket;

import org.bm3k.abboe.common.Biomine3000Utils;
import util.commandline.CommandLineTests;

public class ABBOETests extends CommandLineTests {
	
public static final String CMD_ADDRTEST = "addrtest";

    public ABBOETests(String[] args) {
        super(args);
    }
    
    @Override
    public void run(String cmd) throws Exception {
        if (cmd.equals(CMD_ADDRTEST)) {
            Socket socket = Biomine3000Utils.connectToBestAvailableServer();
            log.info("Address: "+socket.getLocalSocketAddress().toString());
            socket.close();
        }
    }

    public static void main(String[] args) {
        ABBOETests tests = new ABBOETests(args);
        tests.run();
    }
    
}