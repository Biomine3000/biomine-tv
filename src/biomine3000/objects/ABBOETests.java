package biomine3000.objects;

import java.net.Socket;

import util.commandline.CommandLineTests;
import util.dbg.StdErrLogger;

public class ABBOETests extends CommandLineTests {

    public static final String CMD_ADDRTEST = "addrtest";

    public ABBOETests(String[] args) {
        super(args);
    }

    @Override
    public void run(String cmd) throws Exception {
        if (cmd.equals(CMD_ADDRTEST)) {
            Socket socket = Biomine3000Utils.connectToBestAvailableServer(new StdErrLogger());
            log.info("Address: " + socket.getLocalSocketAddress().toString());
            socket.close();
        }
    }

    public static void main(String[] args) {
        ABBOETests tests = new ABBOETests(args);
        tests.run();
    }

}