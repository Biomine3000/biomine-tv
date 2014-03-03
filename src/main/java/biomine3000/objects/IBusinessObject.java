package biomine3000.objects;


/** hopefully temporary interface to alleviate the transition of BusinessObject => BusinessObject2 */
public interface IBusinessObject {
	public BusinessObjectMetadata getMetadata();
	public byte[] getPayload();
	public boolean isEvent();
	public byte[] bytes();
	public boolean hasPayload();
	public void setEvent(String type);
	public void setEvent(BusinessObjectEventType type);
}
