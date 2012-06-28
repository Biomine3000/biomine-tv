package biomine3000.objects;

public abstract class ServerException extends Exception {
    public ServerException(String pMsg, Throwable pCause) {
        super(pMsg, pCause);
    }

    public ServerException(String pMsg) {
        super(pMsg);
    }

    public ServerException(Throwable pCause) {
        super(pCause);
    }
}
