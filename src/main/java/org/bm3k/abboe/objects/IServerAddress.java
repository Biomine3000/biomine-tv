package org.bm3k.abboe.objects;

public interface IServerAddress {    
    public int getPort();
    /** must not be null */
    public String getHost();        
}
