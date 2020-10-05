package io.github.bennofs.wdumper.zenodo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class MultipartUploadTest {
    @Test
    public void jsonParsesCorrectly() throws IOException {
        final ObjectMapper mapper = ZenodoApi.createObjectMapper();
        final InputStream data = getClass().getResourceAsStream("/zenodo-api/multipart-upload.json");
        final MultipartUpload upload = mapper.readValue(data, MultipartUpload.class);

        assertEquals("e5f855a5-ba1c-438d-8f82-251fae45c395", upload.id);
        assertEquals(Instant.parse("2020-10-05T19:11:29.507705+00:00"), upload.created);
        assertEquals(Instant.parse("2020-10-05T19:39:05.155796+00:00"), upload.updated);
        assertFalse(upload.completed);
        assertEquals("test-data", upload.key);
        assertEquals("28fd69ac-7903-4ef9-88ef-f11d0c9943cc", upload.bucket);
        assertEquals(11534336, upload.size);
        assertEquals(6000000, upload.partSize);
        assertEquals(1, upload.lastPartNumber);
        assertEquals(5534336, upload.lastPartSize);
        assertEquals(1, upload.parts.size());

        assertEquals(URI.create("https://sandbox.zenodo.org/api/files/28fd69ac-7903-4ef9-88ef-f11d0c9943cc/test-data"),
                upload.links.object);
        assertEquals(URI.create("https://sandbox.zenodo.org/api/files/28fd69ac-7903-4ef9-88ef-f11d0c9943cc"),
                upload.links.bucket);
        assertEquals(URI.create("https://sandbox.zenodo.org/api/files/28fd69ac-7903-4ef9-88ef-f11d0c9943cc/test-data?uploadId=e5f855a5-ba1c-438d-8f82-251fae45c395"),
                upload.links.self);

        final MultipartUpload.Part part0 = upload.parts.get(0);
        assertEquals(Instant.parse("2020-10-05T19:45:08.972649+00:00"), part0.updated);
        assertEquals(Instant.parse("2020-10-05T19:39:05.159963+00:00"), part0.created);
        assertEquals("md5:2794699a710a75aa01d8cb0232dd0a90", part0.checksum);
        assertEquals(0, part0.partNumber);
        assertEquals(6000000, part0.endByte);
        assertEquals(0, part0.startByte);
    }
}
