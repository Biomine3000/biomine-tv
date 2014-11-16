package org.bm3k.abboe.tv;

import java.awt.*;


import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.imageio.ImageIO;
import javax.swing.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.util.*;
import java.util.List;

import com.google.common.net.MediaType;
import org.bm3k.abboe.common.*;

import org.bm3k.abboe.objects.*;
import org.json.JSONArray;
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
            new ClientParameters("BiomineTV", Subscriptions.EVERYTHING);        
    
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
	
	private boolean paused;
	
	
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
    
    private void togglePaused() {    	
    	paused = !paused;
    	if (paused) {
    		message("PAUSED!");
    	}
    	else {
    		message("UNPAUSED!");
    	}
    }
    
    private void tryToListenKeys(List<? extends Component> components) {
    	BMTVKeyListener keyListener = new BMTVKeyListener();
    	for (Component c: components) {
    		c.setFocusable(true);    		
    		c.addKeyListener(keyListener);    	
		    logArea.addFocusListener(new FocusListener() {
				
				@Override			
		        public void focusGained(FocusEvent e) { 
		        	log.info("focusGained: "+e.getComponent());
		        }
				@Override
		        public void focusLost(FocusEvent e) {
					log.info("focusLost"+e.getComponent() );
		        }
		    });
		    
		    c.addMouseListener(new MouseAdapter() {
		    	 public void mouseClicked(MouseEvent e) {
		    		 log.info("Mouse clicked, requesting focus:  "+e.getSource());
		    		 if (e.getSource() instanceof JComponent) {
		    			 ((JComponent)e.getSource()).requestFocusInWindow();
		    		 }
		    	 }
		    });
    	}
    }
    
    private void init()  {

    	log.info("Starting tv.init()");   
    	
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
	    logArea.addMouseListener(new MouseAdapter() {
	    	 public void mouseClicked(MouseEvent e) {
	    		 log.info("Mouse clicked.");
	    	 }
	    });
	    add(logArea, BorderLayout.EAST);
	    add(contentPanels, BorderLayout.CENTER);
	    add(logPanel, BorderLayout.SOUTH);
	    contentPanels.requestFocusInWindow();
	    		    
	    logArea.addFocusListener(new FocusListener() {
			
			@Override			
	        public void focusGained(FocusEvent e) { 
	        	log.info("focusGained");
	        }
			@Override
	        public void focusLost(FocusEvent e) {
				log.info("focusLost");
	        }
	    });	    
	    
	    tryToListenKeys(Arrays.asList(getContentPane()));	    
	    
	    addWindowListener(new WindowAdapter() {
	 	  	public void windowClosing(WindowEvent e) {
	 		    close();
	 	  	}
	 	});	    	    	    	    	    
    } 

    private void startConnectionMonitorThread(List<? extends IServerAddress> serverAddresses) {
    	log.info("starting connectionmonitorthread with servers: " + StringUtils.listToString(serverAddresses));
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
        
        List<IServerAddress> serverAddresses = new ArrayList<IServerAddress>();
        ServerAddress serverFromArgs = args.getServerAddress();
    	if (serverFromArgs != null) {
    		serverAddresses.add(serverFromArgs);
    	}
        
        if (!(args.noServersFile())) {
        	List<? extends IServerAddress> serversFromConfigFile = Biomine3000Utils.readServersFromConfigFile();        

        	serverAddresses.addAll(serversFromConfigFile);
        }
                                 
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
        
        @Override
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
                                log.error("Failed connecting to server "+address, e);
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
            
            log.info("Handling object: "+bo);
            
            if (bo.getMetadata().isEvent()) {                
                BusinessObjectEventType et = bo.getMetadata().getKnownEvent();
                               
                if (et == BusinessObjectEventType.ROUTING_SUBSCRIPTION) {
                    message("ROUTING_SUBSCRIPTION: "+bo);
                }
                else if (et == BusinessObjectEventType.ROUTING_SUBSCRIBE_REPLY) {
                    String routingId = bo.getMetadata().getString("routing-id");
                    connection.setRoutingId(routingId);
                    message("Subscribed successfully to the server: "+bo);
                    message("Routing id of TV using connection " + connection + " is now: " + routingId);
                    
                }                
                else if (et == BusinessObjectEventType.ROUTING_SUBSCRIBE_NOTIFICATION) {
                    if (bo.getMetadata().get("routing-id").equals(connection.getRoutingId())) {
                        message("Ignoring notification about our own routing/subscribe");
                    }
                    else {
                        message("Client subscribed to server: "+bo);
                    }
                }
                else if (et == BusinessObjectEventType.ROUTING_DISCONNECT) {
                    message("Client disconnected from server: "+bo);
                }
                else if (et == BusinessObjectEventType.SERVICES_REQUEST) {
                	String serviceName = bo.getMetadata().getString("name");
                	String request = bo.getMetadata().getString("request");
                	String client = bo.getMetadata().getString("client");
                	String clientName;
                	if (client != null) {
                	    clientName = "Client "+client;
                	}
                	else {
                	    clientName = "Unknown client";
                	}
                	List<String> route = bo.getMetadata().getList("route");
                	if (route != null) {
                	    String sourceRoutingId = route.get(0);
                	    clientName += " with routing id \"" + sourceRoutingId;
                	}
                	else {
                	    clientName += " with unknown routing id";
                	}
                	message(clientName + " requested \""+ request + "\" from service \"" +serviceName+"\": "+bo);
                }
                else if (et == BusinessObjectEventType.SERVICES_REGISTER) {
                    String serviceName = bo.getMetadata().getString("name");
                    List<String> route = bo.getMetadata().getList("route");
                    if (route != null) {
                        String sourceRoutingId = route.get(0);
                        message("Node with routing id " + sourceRoutingId +" registered service \"" + serviceName + "\": "+bo);
                    }
                    else {
                        message("Some unidentified node registered service \"" + serviceName + "\" (no route attribute): "+bo);
                    }
                }
                else if (et == BusinessObjectEventType.SERVICES_REGISTER_REPLY) {                    
                    if (bo.hasNature("error")) {
                        message("Registered services offered by the TV™: "+bo);
                    }
                    else {
                        message("Failed registering services offered by the TV: "+bo);
                    }
                }
                
                else if (et == BusinessObjectEventType.SERVICES_REPLY) {
                    boolean recognizedReply = false;
                    
                    String serviceName = bo.getMetadata().getString("name");
                                                                    
                    if (serviceName != null) {
                        String request = bo.getMetadata().getString("request");
                        if (request != null) {
                            if (request.equals("list")) {
                                recognizedReply = true;
				                JSONArray clients = bo.getMetadata().asJSON().getJSONArray("clients");
				                message("Clients on this server:\n"+clients.toString(4));
                            }
                            else if (request.equals("join")) {
                                recognizedReply = true;
								if (bo.hasNature("error"))  {
								    message("Failed registering to clients registry: "+bo);
								}
								else {
								    message("Registered to clients registry: "+bo);
								}							
                            }
                            else if (request.equals("leave")) {
                                if (bo.hasNature("error"))  {
                                    message("Failed registering our departure to clients registry: "+bo);
                                }
                                else { 
                                    message("Our departure was duly noted by clients registry: "+bo);
                                }
                                recognizedReply = true;
                            }
                        }                            
                    }
                                            
                    if (!recognizedReply) {
                        message("UNRECOGNIZED services/reply: "+Biomine3000Utils.formatBusinessObject(bo));
                    }
                }                
                else {
                    // unknown event
                    message("UNRECOGNIZED event: "+Biomine3000Utils.formatBusinessObject(bo));
                }
            } else {                       
            	// show media
                MediaType type = bo.getType();
                if (type.is(MediaType.ANY_IMAGE_TYPE)) {                	
                	if (!paused) {
	                    ByteArrayInputStream is = null;
	                    try {
	                        is = new ByteArrayInputStream(bo.getPayload());
	                        BufferedImage image = ImageIO.read(is);
	                        imagePanel.setImage(image);
	                        String oldMsg = imagePanel.getMessage();
	                        if (oldMsg != null && oldMsg.equals("Awaiting content from server...")) {
	                            imagePanel.setMessage(null);
	                        }
	                    } catch (IOException e) {
	                        log.warn("Couldn't read image", e);
	                    } finally {
	                        try {
	                            is.close();
	                        } catch (Exception e) {
	                        }
	                    }
                	}
                } else if (type.is(MediaType.ANY_TEXT_TYPE)) {                	
                	Set<String> natures = bo.getMetadata().getNatures();
                    if (natures.contains("message")) {
                    	// show business object as is (no interpretation by TV)
                    	message(bo);
                    }
                    else if (natures.contains("url")) {
                     // show business object as is (no interpretation by TV)
                        message(bo);
                    }
                    else {                 	
                    	message("NO MESSAGE OR URL NATURE: "+Biomine3000Utils.formatBusinessObject(bo)+"\n");
                	}
	            } else if (type.equals(BusinessMediaType.MP3)) {
	                playMP3(bo);
	            } else {
	                message("Unable to display payload for non-event object:" + bo);
	            }
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
	    	else if (keyCode == KeyEvent.VK_SPACE) {
	    		togglePaused();
	    	}
	    	
		}
	
		@Override
		public void keyReleased(KeyEvent e) {
			// no action
		}					
	}    
            
    /* show business object showable as such, with no interpretation done by tv */
    private void message(BusinessObject bo) {        
        String msg = BusinessObjectFormatter.format(bo);
        logPanel.appendText(msg);
        log.info(msg);
    }
    
    private void message(String msg) {
        String ircTime = BusinessObjectFormatter.formatIRCTime();        
        String formattedMsg = ircTime + " <TV> " +msg; 
        logPanel.appendText(formattedMsg);
        log.info(formattedMsg);
    }    
}