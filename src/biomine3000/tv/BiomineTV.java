package biomine3000.tv;

import java.awt.BorderLayout;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;

import javax.swing.*;


import biomine3000.objects.*;

import util.collections.OneToOneBidirectionalMap;
import util.dbg.ILogger;
import util.dbg.Logger;
//import util.net.NonBlockingSender;

public class BiomineTV extends JFrame {

    // CONSTANTS
    private static final double RETRY_INTERVAL_SEC = 1.0;
    private static final ClientParameters CLIENT_PARAMS = 
            new ClientParameters("BiomineTV", ClientReceiveMode.NO_ECHO, Subscriptions.ALL, true);
    
    /////////////////
    // GUI
	private JLabel zombiLabel;
	private JTextArea logArea;
	private JLabel statusLabel;
	private LinkedList<String> logLines;
	private BiomineTVImagePanel contentPanel;
	private BMTVMp3Player mp3Player;

	private ILogger log;
	
	//////////////////
	// BUSINESS
        
	// Kontenttia; either read directly from a vault using contentVaultAdapter, of from a server	 
	// private ContentVaultAdapter contentVaultAdapter;
//	private Socket serverSocket;

//	private SenderListener senderListener;
	
	// TODO: remontti kesken, kele
	
	/** Active connections. Access to this should naturally be synchronized */
	OneToOneBidirectionalMap<IServerAddress, ABBOEConnection> connectionsByAddress = new OneToOneBidirectionalMap<IServerAddress, ABBOEConnection>();

	// CONTROL
	ConnectionThread monitorThread;
	
    public BiomineTV(ILogger log) {
        this.log = log;
	    init();
    }

    static int LOG_SIZE = 10;
          
    /** TODO: support playing in a streaming fashion */     
    private void playMP3(BusinessObject bo) {
        contentPanel.setMessage("Playing: "+bo.getMetaData().get("name"));
        mp3Player.play(bo.getPayload());
    }
    
    private void init()  {

        contentPanel = new BiomineTVImagePanel(this, "Initializing content...");
        mp3Player = new BMTVMp3Player();
                               
	    setTitle("Biomine TV®");
	    zombiLabel = new JLabel("For relaxing times, make it zombie time");
	    logArea = new JTextArea();
	    logArea.setSize(400, 400);
	    logLines = new LinkedList<String>();
	    statusLabel = new JLabel();
	    setLayout(new BorderLayout());
	    add(zombiLabel, BorderLayout.NORTH);
	    add(logArea, BorderLayout.EAST);
	    add(contentPanel, BorderLayout.CENTER);
	    add(statusLabel, BorderLayout.SOUTH);
//	    log("Testing");	    
	    logArea.setFocusable(false);
	    // logArea.addKeyListener(new BMTKeyListener());
	    addKeyListener(new BMTVKeyListener());
	    
	    addWindowListener(new WindowAdapter() {
	 	  	public void windowClosing(WindowEvent e) {
	 		    close();
	 	  	}
	 	});	    	    	    	    	    
    } 

    private void startConnectionMonitorThread() {
        monitorThread = new ConnectionThread(ServerAddress.LIST);
        monitorThread.start();
    }
    
    private synchronized void stopMonitorThread() {        
        if (monitorThread != null) {
            monitorThread.stop = true;
            monitorThread = null;
        }
    }
    
//    public void startReceivingContentFromVault() {
//        contentVaultAdapter = new ContentVaultAdapter(this, 3000);
//        contentVaultAdapter.startLoading();
//    }
    
    public synchronized boolean connected() {
        return connectionsByAddress.size() > 0; 
    }
    
//    /** Try all known servers... */
//    public synchronized void startReceivingContentFromServer() throws IOException {
//        IOException e = null;
//        try {            
//            startReceivingContentFromServer(ServerAddress.LERONEN_HIMA);            
//        }
//        catch (IOException ex) {
//            e = ex;
//        }
//        
//        if (e != null) {
//            try {
//                log("Failed connecting to local server, trying the one at leronen-kapsi");
//                startReceivingContentFromServer(ServerAddress.LERONEN_KAPSI);
//            }
//            catch (IOException ex) {
//                throw (ex);
//            }
//        }
//        
//    }   
//    
//    /** Currently, only one server can be received from at a time. */
//    public synchronized void startReceivingContentFromServer(ServerAddress server) throws IOException {
//        startReceivingContentFromServer(server.getHost(), server.getPort());
//    }
//    
   /**
    * Start receiving content from an already established TCP connection. 
    * Note that multiple connections can be received from simultaneously!
    */
    public synchronized void startReceivingContentFromServer(IServerAddress address, Socket socket) throws IOException {
        log.info("startReceivingContentFromServer: "+address);
        contentPanel.setMessage("Receiving content from server: "+address);
        
        if (connectionsByAddress.containsSrcKey(address)) {
            throw new RuntimeException("Already receiving content from: "+address);
        }
                                
        ABBOEConnection connection = new ABBOEConnection(CLIENT_PARAMS, log);
        connectionsByAddress.put(address, connection);
        connection.init(socket, new ConnectionListener(connection));
        
//        serverSocket = new Socket(host, port);
                
//        senderListener = new SenderListener(); 
//        NonBlockingSender sender = new NonBlockingSender(serverSocket, senderListener);
//        BusinessObject registerObj = Biomine3000Utils.makeRegisterPacket("BiomineTV-java", ClientReceiveMode.NO_ECHO);
//        sender.send(registerObj.bytes());
                        
//        BusinessObjectReader readerRunnable = new BusinessObjectReader(serverSocket.getInputStream(), new ServerReaderListener(), "server reader", true);
//        contentPanel.setMessage("Awaiting content from server...");
//        Thread readerThread = new Thread(readerRunnable);
//        readerThread.start();        
    }
    
//    public void stopReceivingContentFromServer() {
//        closeConnectionToServer();
//    }
    
//    /** Listener to receive objects read by BusinessObjectReader from the server */
//    private class ServerReaderListener extends BusinessObjectReader.DefaultListener {        
//
//        
//        @Override
//        public void objectReceived(BusinessObject bo) {
//            BiomineTV.this.handle(bo);
//        }
//
//        @Override
//        public void noMoreObjects() {
//            log("noMoreObjects (client closed connection).");
//            contentPanel.setMessage("No more objects available at server");
//            closeConnectionToServer();
//        }
//        
//        @Override
//        public void handleException(Exception e) {            
//            contentPanel.setMessage(ExceptionUtils.format(e));
//        }                           
//    }
    
//    private synchronized void closeConnectionToServer() {
//        if (serverSocket != null) {
//            try {
//                serverSocket.close();
//            }
//            catch (IOException e) {
//                error("Failed closing connection to server", e);
//            }
//            serverSocket = null;
//        }
//        else {
//            warn("Cannot close connection to server, as one does not exist");
//        }
//    }
        
    
    @SuppressWarnings("unused")
    private void logToGUI(String s) {
    	logLines.addLast(s);
    	if (logLines.size() > LOG_SIZE) {
    		logLines.removeFirst();
    	}
    	logArea.setText("");
    	for (String l: logLines) {
    		logArea.append(l+"\n");
    	}    	
    }    
    
    public static void main(String[] pArgs) throws Exception {
        @SuppressWarnings("unused")
        Biomine3000Args args = new Biomine3000Args(pArgs, true);
        ILogger log = new Logger.ILoggerAdapter("BiomineTV: ");        
        BiomineTV tv = new BiomineTV(log);
        tv.setSize(800,600);
        tv.setLocation(300,300);
        tv.setVisible(true);
                                
        // will connect to the server, and keep trying every second until successful
        tv.startConnectionMonitorThread();     
    }
  
    /** Tries to maintain connections to all servers at given addresses at all times. Retries connections persistently */
    private class ConnectionThread extends Thread {
        private boolean stop = false;
        List<? extends IServerAddress> addresses;
        ConnectionThread(List<? extends IServerAddress> addresses) {
            this.addresses = addresses;
        }
        
        public void run() {
            int i = 0;
            // cyclicly loop through addresses until requested to stop 
            while (!stop) {
                if (i==addresses.size()) {
                    i = 0;
                }
                IServerAddress address = addresses.get(i++);                
                try {                                        
                    synchronized(BiomineTV.this) {                        
                        if  (!(connectionsByAddress.containsSrcKey(address))) {
                            // not connected to server at this particular address
                            try {
                                Socket socket = Biomine3000Utils.connectToServer(address.getHost(), address.getPort());
                                // successfully connected, start receiving content...
                                startReceivingContentFromServer(address, socket);
                            }
                            catch (IOException e) {
                                error("Failed connecting to server "+address, e);
                            }
                        }
                    }
                    
                    if (connectionsByAddress.size() == addresses.size()) {
                        // nothing to connect to
                    }
                    Thread.sleep((long)(RETRY_INTERVAL_SEC*1000));                    
                }
                catch (InterruptedException e) {
                    // no action 
                }
            }
            
        }
    }
        
    public synchronized void close() {
        log.info("Starting BiomineTV.close");
        stopMonitorThread();
        
        if (connectionsByAddress.size() > 0) {
            // exiting will be postponed to the closing down of the last connection!
            for (ABBOEConnection con: connectionsByAddress.getTgtValues()) {
                log.info("Initiating shutdown of connection: "+con);            
                con.initiateShutdown();
            }
        }
        else {
            // no connections, can exit right away
            System.exit(0);
        }
    }
  
    /** Handle arbitrary business object */
    public synchronized void handle(BusinessObject bo) {          
        log("Received content from channel "+bo.getMetaData().getChannel()+": "+bo.toString());
        
        if (bo instanceof ImageObject) {
            contentPanel.setImage((ImageObject)bo);
            String oldMsg = contentPanel.getMessage();
            if (oldMsg != null && oldMsg.equals("Awaiting content from server...")) {
                contentPanel.setMessage(null);
            }
        }
        else if (bo instanceof PlainTextObject) {
            contentPanel.setMessage(((PlainTextObject)bo).getText());
        }
        else if (bo.getMetaData().getOfficialType() == Biomine3000Mimetype.MP3) {
            playMP3(bo);
        }        
        else {
            // plain object with no or unsupported official type 
            log("Unable to display content:" +bo);            
        }
    }
    
    private boolean shuttingDown() {
        return monitorThread == null; 
    }
    
    private synchronized void connectionTerminated(ABBOEConnection con) {
        log("Connection terminated: "+con);
        this.connectionsByAddress.removeTgt(con);
        if (shuttingDown()) {
            if (connectionsByAddress.size() == 0) {
                // no more connections, we can finally die
                log("Last connection terminated, exiting");
                System.exit(0);
            }
        }
    }
    
    private class ConnectionListener implements ABBOEConnection.BusinessObjectHandler {

        ABBOEConnection connection;
        ConnectionListener(ABBOEConnection connection) {
            this.connection = connection;
        }
        
        @Override
        public void handleObject(BusinessObject obj) {
            handle(obj);
        }

        @Override
        public void connectionTerminated() {
           BiomineTV.this.connectionTerminated(connection);
        }

        @Override
        public void connectionTerminated(Exception e) {
            log.error("Connection to "+connection+" terminated");
            BiomineTV.this.connectionTerminated(connection);
        }
        
    }
    
//    private class SenderListener implements NonBlockingSender.Listener {
//        public void senderFinished() {
//            log("Sender finished");
//        }
//    
//    }
    
    /**
     * For now, the sole purpose of this listener is to enable closing the 
     * tv using ctrl+q instead of the abodominable ALT+F4.
     */
    private class BMTVKeyListener implements KeyListener {

	    @Override
	    public void keyTyped(KeyEvent e) {
	        // no action
		}
	
		@Override
		public void keyPressed(KeyEvent e) {
			int keyCode = e.getKeyCode();			
	    	if (keyCode == KeyEvent.VK_W && e.isControlDown()) {
	    		close();
	    	}
	    	else if (keyCode == KeyEvent.VK_Q && e.isControlDown()) {	    		
	    		close();
	    	}	    	
		}
	
		@Override
		public void keyReleased(KeyEvent e) {
			// no action
		}					
	}    
        
    private void log(String msg) {
        log.info("BiomineTV: "+msg);
    }    
    
    @SuppressWarnings("unused")
    private void warn(String msg) {
        log.warning("BiomineTV: "+msg);
    }        
    
    @SuppressWarnings("unused")
    private void error(String msg, Exception e) {
        log.error("BiomineTV: "+msg, e);
    }
    

}