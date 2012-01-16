package biomine3000.objects;

public enum ServerAddress implements IServerAddress {
    
    LERONEN_HIMA("localhost", Biomine3000Constants.LERONEN_HIMA_PORT),
    LERONEN_KAPSI("lakka.kapsi.fi", Biomine3000Constants.LERONEN_KAPSI_PORT_1);
        
    
    private final String host;
    private final int port;
    
    public int getPort() {
        return port;
    }
    
    public String getHost() {
    	if (this == LERONEN_KAPSI && Biomine3000Utils.atBC()) {
    		// tunnel
    		return "localhost";
    	}
    	else {
    		return host;
    	}
    }
    
    @Override
    public String toString() {
        return name()+" ("+getHost()+":"+getPort()+")";
    }
    
    private ServerAddress(String host,int port) {
        this.host = host;
        this.port = port;
    }
    
}
