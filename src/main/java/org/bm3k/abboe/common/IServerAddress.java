package org.bm3k.abboe.common;

public interface IServerAddress {    
    public int getPort();
    /** must not be null */
    public String getHost();        
}
