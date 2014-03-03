package org.bm3k.abboe.common;

@SuppressWarnings("serial")
public class UnknownImageTypeException extends Exception {

	public UnknownImageTypeException(String fileName) {
        super("Unknown image type for file: "+fileName);
    }
    
}
