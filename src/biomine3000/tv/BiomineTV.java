package biomine3000.tv;

import java.awt.BorderLayout;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.Socket;
import java.util.LinkedList;

import javax.swing.*;

import biomine3000.objects.BusinessObject;
import biomine3000.objects.BusinessObjectHandler;
import biomine3000.objects.BusinessObjectReader;
import biomine3000.objects.ContentVaultAdapter;
import biomine3000.objects.ImageObject;
import biomine3000.objects.PlainTextObject;
import biomine3000.objects.TestServer;

import util.ExceptionUtils;
import util.dbg.Logger;

public class BiomineTV extends JFrame implements BusinessObjectHandler {
	   
    /////////////////
    // GUI
	private JLabel zombiLabel;
	private JTextArea logArea;
	private JLabel statusLabel;
	private LinkedList<String> logLines;
	private BiomineTVImagePanel contentPanel;

	//////////////////
	// BUSINESS
        
	// Kontenttia; either read directly from a vault using contentVaultAdapter, of from a server	 
	private ContentVaultAdapter contentVaultAdapter;
	private Socket serverSocket;
	        	   
    public BiomineTV() throws IOException {
	    init();
    }

    static int LOG_SIZE = 10;
        
    private void init() throws IOException {               
    
        contentPanel = new BiomineTVImagePanel(this, "Initializing content...");
                               
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

    public void startReceivingContentFromVault() {
        contentVaultAdapter = new ContentVaultAdapter(this);
        contentVaultAdapter.startLoading();
    }
    
       
    /** Currently, only one server can be received from at a time */
    public void startReceivingContentFromServer(String server, int port) throws IOException {
        serverSocket = new Socket(server, port);
        BusinessObjectReader readerRunnable = new BusinessObjectReader(serverSocket.getInputStream(), new ServerReaderListener(), "server reader", true);
        contentPanel.setMessage("Awaiting content from server...");
        Thread readerThread = new Thread(readerRunnable);
        readerThread.start();        
    }

    
    public void stopReceivingContentFromServer() {
        closeConnectionToServer();
    }
    
    private class ServerReaderListener extends BusinessObjectReader.DefaultListener {        
        
        @Override
        public void objectReceived(BusinessObject bo) {
            log("Received business object: "+bo);
            BiomineTV.this.handle(bo);
        }

        @Override
        public void noMoreObjects() {
            log("noMoreObjects (client closed connection).");
            contentPanel.setMessage("No more objects available at server");
            closeConnectionToServer();
        }
        
        @Override
        public void handleException(Exception e) {            
            // error("Exception while reading", e);
            contentPanel.setMessage(ExceptionUtils.format(e));
        }                           
    }
    
    private synchronized void closeConnectionToServer() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            }
            catch (IOException e) {
                error("Failed closing connection to server", e);
            }
            serverSocket = null;
        }
        else {
            warn("Cannot close connection to server, as one does not exist");
        }
    }
        
    
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
    
    public static void main(String[] args) throws IOException {
        BiomineTV tv = new BiomineTV();
        tv.setSize(800,600);
        tv.setLocation(300,300);
        tv.setVisible(true);
                
        tv.startReceivingContentFromServer(TestServer.DEFAULT_HOST, TestServer.DEFAULT_PORT);
    }
  
    public void close() {
        Logger.info("Starting BiomineTV.close");
        if (contentVaultAdapter != null) {
            contentVaultAdapter.stop();
        }
        if (serverSocket != null) {
            closeConnectionToServer();
        }
    	System.exit(0);
    }
  
    /** Handle arbitrary business object */
    public void handle(BusinessObject bo) {          
        log("Received content from channel "+bo.getMetaData().getChannel()+": "+bo.toString());
        
        if (bo instanceof ImageObject) {
            contentPanel.setImage((ImageObject)bo);
            contentPanel.setMessage(null);
        }
        else if (bo instanceof PlainTextObject) {
            contentPanel.setMessage(((PlainTextObject)bo).getText());
        }
        else {
            log("Unable to display content:" +bo);
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
		}
	
		@Override
		public void keyReleased(KeyEvent e) {
			// no action
		}					
	}    
        
    private static void log(String msg) {
        Logger.info("BiomineTV: "+msg);
    }    
    
    @SuppressWarnings("unused")
    private static void warn(String msg) {
        Logger.warning("BiomineTV: "+msg);
    }        
    
    @SuppressWarnings("unused")
    private static void error(String msg, Exception e) {
        Logger.error("BiomineTV: "+msg, e);
    }

}