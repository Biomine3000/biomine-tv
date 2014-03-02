package biomine3000.objects;

/** 
 * An exception after only one client is lost, and it is still possible to 
 * accept new clients. TODO: define how closing of connection with 
 * client is ensured!
 */
@SuppressWarnings("serial")
public class RecoverableServerException extends ServerException {

    public RecoverableServerException(String pMsg, Throwable pCause) {
        super(pMsg, pCause);        
    }

    public RecoverableServerException(String pMsg) {
        super(pMsg);       
    }
    
    public RecoverableServerException(Throwable pCause) {
        super(pCause);          
    }
}