package biomine3000.objects;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;


import util.IOUtils;
import util.StringUtils;
import util.dbg.ILogger;
import util.dbg.Logger;

public class MP3Sender {

    private ILogger log;
    private Socket socket = null;

    public MP3Sender(Socket socket, ILogger log) {
        this.socket = socket;
        this.log = log;
    }

    /**
     * Channel and user may be null, file may not.
     */
    public void send(java.io.File file, String channel, String user) throws IOException {

        // read file.
        log.info("Reading file: " + file);
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
        byte[] payload = IOUtils.readBytes(bis);
        bis.close();
        BusinessObject bo = new BusinessObject(Biomine3000Mimetype.MP3, payload);
        bo.getMetaData().put("name", file.getName());
        if (channel != null) {
            bo.getMetaData().put("channel", channel);
        }
        if (user != null) {
            bo.getMetaData().put("user", user);
        }

        // write register object
        BusinessObject registerObj = Biomine3000Utils.makeRegisterPacket(
                "MP3Sender",
                ClientReceiveMode.NONE,
                Subscriptions.NONE);
        log.info("Writing register object:" + registerObj);
        IOUtils.writeBytes(socket.getOutputStream(), registerObj.bytes());

        // write actual mp3
        byte[] bytes = bo.bytes();
        log.info("Writing " + StringUtils.formatSize(bytes.length) + " bytes");
        IOUtils.writeBytes(socket.getOutputStream(), bo.bytes());
        log.info("Sent packet");
    }

    public static void main(String[] pArgs) throws Exception {
        Biomine3000Args args = new Biomine3000Args(pArgs);
        Socket socket = Biomine3000Utils.connectToServer(args);
        String channel = args.getChannel();
        String user = args.getUser();
        File file;
        if (args.hasPositionalArgs()) {
            file = new File(args.shift());
        } else {
            file = Biomine3000Utils.randomFile(".");
        }

        MP3Sender sender = new MP3Sender(socket, new Logger.ILoggerAdapter());
        sender.send(file, channel, user);

        socket.close();
    }

}
