package org.bm3k.abboe.common;

import org.bm3k.abboe.objects.BusinessObject;
import org.bm3k.abboe.objects.BusinessObjectImpl;
import org.bm3k.abboe.objects.IBusinessObjectFactory;
import org.bm3k.abboe.objects.LegacyBusinessObject;
import util.collections.Pair;

import com.google.common.net.MediaType;

public class BusinessObjectFactory {

	private static final String IMPL_SANE = "sane";
	private static final String IMPL_LEGACY = "legacy";
	private static final String DEFAULT_IMPL = IMPL_LEGACY;
	
	private static IBusinessObjectFactory impl;
	
	@SuppressWarnings("deprecation")
	private static IBusinessObjectFactory getImpl() {
		if (impl == null) {
			String implName = System.getenv("BUSINESS_OBJECT_IMPL");
			if (implName == null) {
				implName = DEFAULT_IMPL;
			}
			System.err.println("Using business object implName: "+implName);
			
			switch(implName) {
				case IMPL_SANE: 
					impl = new BusinessObjectImpl.Factory();
					break;
				case IMPL_LEGACY: 					
					impl = new LegacyBusinessObject.Factory();
					break;
				default:
				    throw new RuntimeException("No such business objects impl: "+implName);
			}						
		}
		
		return impl;
	}
	
	public static BusinessObject makeEvent(BusinessObjectEventType eventType) {
		return getImpl().makeEvent(eventType);
	}
	
    public static BusinessObject makeObject(MediaType type, byte[] payload) {
    	return getImpl().makeObject(type, payload);
    }
    
    public static BusinessObject makeObject(Pair<BusinessObjectMetadata, byte[]> data) {
    	return getImpl().makeObject(data);
    }
	
    public static BusinessObject makePlainTextObject(String text) {
    	return getImpl().makePlainTextObject(text);
    }
    public static BusinessObject makePlainTextObject(String text, BusinessObjectEventType eventType) {
    	return getImpl().makePlainTextObject(text, eventType);
    }
	
}
