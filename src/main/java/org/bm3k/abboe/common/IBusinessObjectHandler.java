package org.bm3k.abboe.common;

import org.bm3k.abboe.objects.BusinessObject;

/** Simplest common denominator for business object-receiving entitæ */
public interface IBusinessObjectHandler {
    public void handleObject(BusinessObject bo);
}
