package biomine3000.tv;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;


import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.*;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.util.*;

import biomine3000.objects.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.StringUtils;
import util.collections.OneToOneBidirectionalMap;

@SuppressWarnings("serial")
public class BiomineTV extends JFrame {
    private final Logger log = LoggerFactory.getLogger(BiomineTV.class);

    //////////////////////////////
    // CONSTANTS
    private static final double RETRY_INTERVAL_SEC = 1.0;
    private static final ClientParameters CLIENT_PARAMS = 
            new ClientParameters("BiomineTV", ClientReceiveMode.NO_ECHO, Subscriptions.ALL, true);
    
    ////////////////////////////////
    // GUI
	private JLabel zombiLabel;
	private LogPanel logPanel;
	private JTextArea logArea;
	private JPanel contentPanels;
	/** Only non-null when no connections */
	private JLabel notConnectedLabel;
	
	private LinkedList<String> logLines;
	// private BiomineTVImagePanel contentPanel;
	private BMTVMp3Player mp3Player;
	
	
	////////////////////////////////
	// Damagement of server connections
	/** Active connections. Access to this should naturally be synchronized */
	private OneToOneBidirectionalMap<IServerAddress, ABBOEConnection> connectionsByAddress = new OneToOneBidirectionalMap<IServerAddress, ABBOEConnection>();
	
	private Map<ABBOEConnection, BiomineTVImagePanel> imagePanelByConnection = new LinkedHashMap<ABBOEConnection, BiomineTVImagePanel>();

	/** Thread for initiating and retrying connections */
	private ConnectionThread monitorThread;
	
    public BiomineTV() {
	    init();
    }

    static int LOG_SIZE = 10;
          
    /** TODO: support playing in a streaming fashion */     
    private void playMP3(BusinessObject bo) {
        message("Playing: "+bo.getMetadata().get("name"));
        mp3Player.play(bo.getPayload());
    }
    
    private void init()  {

        mp3Player = new BMTVMp3Player();
                               
	    setTitle("Biomine TV®");
	    zombiLabel = new JLabel("For relaxing times, make it zombie time");
	    contentPanels = new JPanel();
	    contentPanels.setLayout(new GridLayout(1,1));
	    notConnectedLabel = new JLabel("Not connected to any server");
	    contentPanels.add(notConnectedLabel);
	    logArea = new JTextArea();
	    logArea.setSize(400, 400);
	    logPanel = new LogPanel();
	    logPanel.setPreferredSize(new Dimension(400, 400));
	    logLines = new LinkedList<String>();	    
	    setLayout(new BorderLayout());
	    add(zombiLabel, BorderLayout.NORTH);
	    add(logArea, BorderLayout.EAST);
	    add(contentPanels, BorderLayout.CENTER);
	    add(logPanel, BorderLayout.SOUTH);
	    
	    logArea.setFocusable(false);
	    addKeyListener(new BMTVKeyListener());
	    
	    addWindowListener(new WindowAdapter() {
	 	  	public void windowClosing(WindowEvent e) {
	 		    close();
	 	  	}
	 	});	    	    	    	    	    
    } 

    private void startConnectionMonitorThread(List<IServerAddress> serverAddresses) {
        monitorThread = new ConnectionThread(serverAddresses);
        monitorThread.start();
    }
    
    private synchronized void stopMonitorThread() {        
        if (monitorThread != null) {
            monitorThread.stop = true;
            monitorThread = null;
        }
    }    
    
    public synchronized boolean connected() {
        return connectionsByAddress.size() > 0; 
    }
    
    private int numConnections() {
        return imagePanelByConnection.size();
    }
    
   /**
    * Start receiving content from an already established TCP connection. 
    * Note that multiple connections can be received from simultaneously!
    */
    public synchronized void startReceivingContentFromServer(IServerAddress address, Socket socket) throws IOException {
                                            
        if (connectionsByAddress.containsSrcKey(address)) {
            throw new RuntimeException("Already receiving content from: "+address);
        }
               
        ClientParameters clientParams = new ClientParameters(CLIENT_PARAMS);
        clientParams.sender = Biomine3000Utils.getUser();
        ABBOEConnection connection = new ABBOEConnection(CLIENT_PARAMS, socket);
        BiomineTVImagePanel imagePanel = new BiomineTVImagePanel(this);
        imagePanelByConnection.put(connection, imagePanel);
        if (notConnectedLabel != null) {
            contentPanels.remove(notConnectedLabel);
        }
        contentPanels.add(imagePanel);
        contentPanels.setLayout(new GridLayout(1, numConnections()));
        contentPanels.revalidate();
        message("Connected to server: "+address);
        imagePanel.setMessage("Receiving content from server: "+address);
        connectionsByAddress.put(address, connection);
        connection.init(new ConnectionListener(connection, imagePanel));
        connection.sendClientListRequest();
    }
               

    /** some attempt at more manual cyclic log buffer utilization */
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
        Biomine3000Args args = new Biomine3000Args(pArgs, true);
        BiomineTV tv = new BiomineTV();
        tv.setSize(800,600);
        tv.setLocation(300,300);
        tv.setVisible(true);

        // Handle possible command line arguments
        List<IServerAddress> serverAddresses;
        if (args.getHost() != null) {
            if (args.getPort() == null)
                serverAddresses = getServerAddressList(args.getHost(), Biomine3000Constants.DEFAULT_ABBOE_PORT);
            else
                serverAddresses = getServerAddressList(args.getHost(), args.getPort());
        } else
            serverAddresses = ServerAddress.LIST;
        
        // will connect to the server, and keep trying every second until successful
        tv.startConnectionMonitorThread(serverAddresses);
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
                            catch (ConnectException e) {                                
                                // no action 
                            }
                            catch (IOException e) {
                                log.error("Failed connecting to server "+address, e);
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
     
    private boolean shuttingDown() {
        return monitorThread == null; 
    }
    
    private synchronized void connectionTerminated(ABBOEConnection con) {
        message("Connection terminated: "+con);
        this.connectionsByAddress.removeTgt(con);
        
        BiomineTVImagePanel imagePanel = imagePanelByConnection.get(con);
        contentPanels.remove(imagePanel);
        if (numConnections() == 0) {
            contentPanels.setLayout(new GridLayout(1, 1));
            contentPanels.add(new JLabel("Not connected to any server"));
        }
        else {
            contentPanels.setLayout(new GridLayout(1, numConnections()));
        }
        contentPanels.revalidate();
        
        if (shuttingDown()) {
            if (connectionsByAddress.size() == 0) {
                // no more connections, we can finally die
                message("Last connection terminated, exiting");
                System.exit(0);
            }
        }
    }
    
    private class ConnectionListener implements ABBOEConnection.BusinessObjectHandler {

        ABBOEConnection connection;
        BiomineTVImagePanel imagePanel;
        
        ConnectionListener(ABBOEConnection connection, BiomineTVImagePanel imagePanel) {
            this.connection = connection;
            this.imagePanel = imagePanel;
        }
        
        @Override
        public void handleObject(BusinessObject bo) {
            
            if (bo.getMetadata().isEvent()) {                
                BusinessObjectEventType et = bo.getMetadata().getKnownEvent();
                if (et == BusinessObjectEventType.CLIENTS_LIST_REPLY) {
                    String registeredAs = bo.getMetadata().getString("you");
                    message("This client registered on the server as: "+registeredAs);
                    List<String> clients = bo.getMetadata().getList("others");
                    if (clients.size() == 0) {
                        message("No other clients");
                    }
                    else {
                        message("Other clients:");
                        message("\t"+StringUtils.collectionToString(clients, "\n\t"));
                    }
                    
                }
                else if (et == BusinessObjectEventType.CLIENTS_REGISTER_REPLY) {
                    message("Registered successfully to the server");
                }
                else if (et == BusinessObjectEventType.CLIENTS_REGISTER_NOTIFY) {
                    String name = bo.getMetadata().getName();
                    message("Client "+name+" registered to ABBOE");
                }
                else if (et == BusinessObjectEventType.CLIENTS_PART_NOTIFY) {
                    String name = bo.getMetadata().getName();
                    message("Client "+name+" parted from ABBOE");
                }
                else if (et == BusinessObjectEventType.ROUTING_SUBSCRIBE_NOTIFICATION) {
                	message("ROUTING_SUBSCRIBE_NOTIFICATION");
                }
                else if (et == BusinessObjectEventType.ROUTING_DISCONNECT) {
                	message("ROUTING_DISCONNECT");
                }
                else if (et == BusinessObjectEventType.SERVICES_REQUEST) {
                	String name = bo.getMetadata().getName();
                	String request = bo.getMetadata().getString("request");
                	message("HOST " +bo.getMetadata().get("host")+" REQUESTED "+ request + " FROM SERVICE " +name);
                }
                else if (et == BusinessObjectEventType.SERVICES_REPLY) {
                	message("SERVICE_REPLY: "+Biomine3000Utils.formatBusinessObject(bo));
                }
                else {
                    // unknown event
                    message("UNKNOWN_EVENT: "+Biomine3000Utils.formatBusinessObject(bo));
                }
            }
            else if (bo instanceof BusinessObjectImpl){
            	// NOTICE (of retraction): very horribly indeed examine implementation class type
            	// to make things work during the transition period from LegacyBusinessObject to BusinessObjectImpl
            	            	            	            	
            	BusinessObjectImpl bo2 = (BusinessObjectImpl)bo;
            	Payload payload = bo2.getPayloadObject();            	
            	if (payload instanceof ImagePayload) {
            		imagePanel.setImage(((ImagePayload)payload).getImage());
	                String oldMsg = imagePanel.getMessage();
	                if (oldMsg != null && oldMsg.equals("Awaiting content from server...")) {
	                    imagePanel.setMessage(null);
	                }
            	}
            	else if (payload instanceof PlainTextPayload) {
	                logPanel.appendText(Biomine3000Utils.formatBusinessObject(bo)+"\n");
	            }
	            else if (payload instanceof MP3Payload) {
	                playMP3(bo);
	            }        
	            else {
	                // plain object with no or unsupported type 
	                message("Unable to display payload for non-event object:" +bo2);            
	            }
            	
            }
            else if (bo instanceof LegacyBusinessObject) {
            	// support legacy business objects
            	// TODO: this branch to be removed once LegacyBusinessObject no longer needed
	            if (bo instanceof ImageObject) {
	                imagePanel.setImage(((ImageObject)bo).getImage());
	                String oldMsg = imagePanel.getMessage();
	                if (oldMsg != null && oldMsg.equals("Awaiting content from server...")) {
	                    imagePanel.setMessage(null);
	                }
	            }
	            else if (bo instanceof PlainTextObject) {
	//                PlainTextObject to = (PlainTextObject)bo;
	                logPanel.appendText(Biomine3000Utils.formatBusinessObject(bo)+"\n");
	            }
	            else if (bo.getMetadata().getOfficialType() == BusinessMediaType.MP3) {
	                playMP3(bo);
	            }        
	            else {
	                // plain object with no or unsupported official type 
	                message("Unable to display content:" +bo);            
	            }
            }
            else {
            	message("Unable to display content; unknown BusinessObject implementation: "+bo);
            }
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

    private static List<IServerAddress> getServerAddressList(final String host, final int port) {
        List<IServerAddress> ret = new ArrayList<IServerAddress>(1);

        ret.add(new IServerAddress() {
            @Override
            public int getPort() {
                return port;
            }

            @Override
            public String getHost() {
                return host;
            }
        });

        return ret;
    }

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
        
    private void message(String msg) {
        logPanel.appendText(msg+"\n");
        log.info("BiomineTV: "+msg);
    }    
}