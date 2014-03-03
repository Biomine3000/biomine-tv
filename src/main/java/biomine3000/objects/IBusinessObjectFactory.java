package biomine3000.objects;

import util.collections.Pair;

import com.google.common.net.MediaType;

public interface IBusinessObjectFactory {
	public IBusinessObject makeEvent(BusinessObjectEventType eventType);
    public IBusinessObject makeObject(MediaType type, byte[] payload);    	
    public IBusinessObject makeObject(Pair<BusinessObjectMetadata, byte[]> data);
    public IBusinessObject makePlainTextObject(String text);
    public IBusinessObject makePlainTextObject(String text, BusinessObjectEventType eventType);
}
