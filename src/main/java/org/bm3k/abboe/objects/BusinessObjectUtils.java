package org.bm3k.abboe.objects;

import org.bm3k.abboe.common.BusinessObjectMetadata;
import org.bm3k.abboe.common.InvalidBusinessObjectException;
import util.IOUtils;
import util.collections.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class BusinessObjectUtils {
    /**
     * @return null if no more business objects in stream. Note that payload may be null!
     * @throws InvalidBusinessObjectException when packet is not correctly formatted
     * @throws org.bm3k.abboe.common.InvalidJSONException JSON metadata is not correctly formatted json
     * @throws IOException in case of general io error.
     */
    public static Pair<BusinessObjectMetadata, byte[]> readPacket(InputStream is) throws IOException, InvalidBusinessObjectException {
        byte[] metabytes;
        try {
            metabytes = IOUtils.readBytesUntilNull(is);
            if (metabytes == null) {
                // end of stream reached
                return null;
            }
        }
        catch (IOUtils.UnexpectedEndOfStreamException e) {
            throw new InvalidBusinessObjectException("End of stream reached before reading first null byte", e);
        }

        BusinessObjectMetadata metadata = new BusinessObjectMetadata(metabytes);
        byte[] payload;
        if (metadata.hasPayload()) {
            int payloadSz = metadata.getSize();
            payload = IOUtils.readBytes(is, payloadSz);
        }
        else {
            // no payload
            payload = null;
        }
        return new Pair<BusinessObjectMetadata, byte[]>(metadata, payload);
    }

    /** Parse BusinessObjectImpl represented as raw toBytes into medatata and payload */
    static Pair<BusinessObjectMetadata, byte[]> parseBytes(byte[] data) throws InvalidBusinessObjectException {
        int i = 0;
        while (data[i] != '\0' && i < data.length) {
            i++;
        }

        if (i >= data.length) {
            throw new InvalidBusinessObjectException("No null byte in business object");
        }

        byte[] metabytes = Arrays.copyOfRange(data, 0, i);
        BusinessObjectMetadata metadata = new BusinessObjectMetadata(metabytes);
        byte[] payload;
        if (metadata.hasPayload()) {
            payload = Arrays.copyOfRange(data, i+1, data.length);
        }
        else {
            // no päyload
            payload = null;
        }

        return new Pair<BusinessObjectMetadata, byte[]>(metadata, payload);
    }
}
