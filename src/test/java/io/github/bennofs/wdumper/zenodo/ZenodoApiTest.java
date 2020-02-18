package io.github.bennofs.wdumper.zenodo;

import io.github.bennofs.wdumper.Integration;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;


@Integration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ZenodoApiTest {
    private ZenodoApi api;
    private Deposit deposit;

    @BeforeAll
    private void createApi() throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        // this integration test requires a zenodo sandbox api token
        final String token = System.getenv("ZENODO_SANDBOX_TOKEN");
        final CloseableHttpClient client = HttpClientBuilder.create()
                .setConnectionManager(new BasicHttpClientConnectionManager())
                .build();

        this.api = new ZenodoApi(client, ZenodoApi.SANDBOX_URI, token);
    }

    @Test
    @Order(1)
    void testCreateGet() throws IOException, ZenodoApiException {
        deposit = api.createDeposit();
        assertEquals(deposit.id, api.getDeposit(deposit.id).id);
    }

    @Test
    @Order(2)
    void testModifyMetadata() throws IOException, ZenodoApiException {
        final String doi = deposit.metadata.prereserveDoi().doi;
        assertNotNull(doi, "deposit should have preallocated doi");

        final Creator c = Creator.builder().name("John Doe").build();

        deposit.metadata = deposit.metadata.toBuilder()
                .title("Test title")
                .description("Test description")
                .uploadType("dataset")
                .accessRight("open")
                .license("cc-zero")
                .creators(Collections.singletonList(c))
                .build();
        deposit = api.updateDeposit(deposit);
        final Deposit get = api.getDeposit(deposit.id);

        assertEquals(deposit.id, get.id);
        assertEquals("Test title", deposit.metadata.title());
        assertArrayEquals(new Creator[] {c}, deposit.metadata.creators().toArray());
    }

    @Test
    @Order(3)
    void testAddFileInMemory() throws IOException, ZenodoApiException {
        api.addFile(deposit, "test", "hello world", (done, total) -> {});
        final Deposit.DepositFile[] files = api.getFiles(deposit);

        assertEquals(1, files.length);
        assertEquals("test", files[0].filename);
    }

    @Test
    @Order(4)
    void testAddFileLargeStream() throws IOException, ZenodoApiException {
        // generate some data to upload
        // we use a large amount of data (> 2GB) since there were issues at that threshold before
        final long size = (1L<<30) / 2 * 5; // ~ 2.5 GB
        final InputStream source = new InputStream() {
            private long remaining = size;

            @Override
            public int read() {
                if (remaining == 0) return -1;

                remaining -= 1;
                // data doesn't matter, let's at least return something that has a little variance
                return (int)(remaining & 0xFF);
            }
        };

        // perform upload
        final int[] progressCount = { 0 };
        final long[] remaining = { 0 };
        api.addFile(deposit, "foobar", source, size, (done, total) -> {
            assertTrue(done <= total, String.format("%d < %d", done, total));
            progressCount[0] += 1;
            remaining[0] = total - done;
        });
        assertTrue(progressCount[0] > 0, "progress handler was called at least once");
        assertEquals(0, remaining[0]);

        // there should be two files (the one uploaded in the previous test and this one)
        final Deposit.DepositFile[] files = api.getFiles(deposit);
        assertEquals(2, files.length);
        assertEquals("foobar", files[0].filename); // sorted alphabetically
    }

    @Test
    @Order(5)
    void testDeleteGet() throws IOException, ZenodoApiException {
        api.deleteDeposit(deposit.id);
        try {
            api.getDeposit(deposit.id);
            fail("deposit should be deleted");
        } catch (ZenodoApiException e) {
            assertEquals(410, e.getResponseCode(), "deleted should return 404 status code");
        }
    }
}