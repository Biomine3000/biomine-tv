package biomine3000.objects;

import java.util.ArrayList;
import java.util.List;

public enum ServerAddress implements IServerAddress {
    
    LERONEN_HIMA("localhost", Biomine3000Constants.LERONEN_HIMA_PORT),
    LERONEN_KAPSI("lakka.kapsi.fi", Biomine3000Constants.LERONEN_KAPSI_PORT_1);

    public static List<IServerAddress> LIST;
    static {
        LIST = new ArrayList<IServerAddress>(values().length);
        LIST.add(LERONEN_HIMA);
        LIST.add(LERONEN_KAPSI);

    }

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
        else if (this == LERONEN_KAPSI && Biomine3000Utils.atWel120()) {
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
