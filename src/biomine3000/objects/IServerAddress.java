package biomine3000.objects;

public interface IServerAddress {
    public int getPort();

    /**
     * must not be null
     */
    public String getHost();
}
