package biomine3000.objects;

public enum ServerAddress {
    
    LERONEN_KAPSI("lakka.kapsi.fi", Biomine3000Constants.LERONEN_KAPSI_PORT_1),
    LERONEN_HIMA("localhost", Biomine3000Constants.DEFAULT_PORT);    
    
    public final String host;
    public final int port;
    
    private ServerAddress(String host,int port) {
        this.host = host;
        this.port = port;
    }
    
}
