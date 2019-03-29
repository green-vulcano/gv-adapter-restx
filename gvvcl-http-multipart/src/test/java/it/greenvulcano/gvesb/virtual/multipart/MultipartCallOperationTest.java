package it.greenvulcano.gvesb.virtual.multipart;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Node;

import it.greenvulcano.configuration.XMLConfig;
import it.greenvulcano.configuration.XMLConfigException;
import it.greenvulcano.gvesb.buffer.GVBuffer;
import it.greenvulcano.gvesb.buffer.GVException;
import it.greenvulcano.gvesb.virtual.CallOperation;
import it.greenvulcano.gvesb.virtual.multipart.MultipartCallOperation;
import net.codestory.http.WebServer;
import net.codestory.http.payload.Payload;

public class MultipartCallOperationTest {

    private static WebServer webserver = null;

    @BeforeClass
    public static void init() {

        XMLConfig.setBaseConfigPath(MultipartCallOperationTest.class.getClassLoader().getResource(".").getPath());
        webserver = new WebServer();
        webserver.configure(routes -> {
            routes.post("/testbytearraypart", (context) -> {
                if (context.request().parts().get(0).fileName().equals("bla.zip") && context.request().parts().get(0).contentType().equals("application/zip")
                    && context.request().parts().get(0).isFile() == true) {
                    return new Payload("Test MultipartByteArray ok");
                }
                return new Payload("NOK");
            });
            routes.post("/testfilepart", (context) -> {
                if (context.request().parts().get(0).fileName().equals("default.zip") && context.request().parts().get(0).contentType().equals("application/zip")
                    && context.request().parts().get(0).isFile() == true) {
                    return new Payload("Test MultipartFile ok");
                }
                return new Payload("NOK");
            });
            routes.post("/teststringpart", (context) -> {
                if (context.request().parts().get(0).content().equals("ProvaStringaLalalala") && context.request().parts().get(0).contentType().equals("text/plain")) {
                    return new Payload("Test MultipartString ok");
                }
                return new Payload("NOK");
            });
            routes.post("/testformpart", (context) -> {

                if (context.request().parts().get(0).content().contains("Ajeje")
                    && context.request().parts().get(0).contentType().startsWith("application/x-www-form-urlencoded")) {
                    return new Payload("Test MultipartForm ok");
                }
                return new Payload("NOK");
            });
        }).start(8888);
    }

    @Test
    public void testMultipartByteArray() throws XMLConfigException, GVException, InterruptedException, IOException, URISyntaxException {

        Node node = XMLConfig.getNode("GVCore.xml", "//*[@name='testByteArrayPart']");
        CallOperation callOperation = new MultipartCallOperation();
        callOperation.init(node);

        Path path = Paths.get(getClass().getClassLoader().getResource("default.zip").toURI());
        byte[] gvBufferFile = Files.readAllBytes(path);
        GVBuffer gvbuffer = new GVBuffer("Multipart", "testMultipart");
        gvbuffer.setObject(gvBufferFile);

        callOperation.perform(gvbuffer);
        Assert.assertEquals("Test MultipartByteArray ok", new String((byte[]) gvbuffer.getObject()));
    }

    @Test
    public void testMultipartFile() throws XMLConfigException, GVException, InterruptedException, URISyntaxException {

        Node node = XMLConfig.getNode("GVCore.xml", "//*[@name='testFilePart']");
        CallOperation callOperation = new MultipartCallOperation();
        callOperation.init(node);

        GVBuffer gvbuffer = new GVBuffer("Multipart", "testMultipart");
        Path path = Paths.get(getClass().getClassLoader().getResource("default.zip").toURI());
        gvbuffer.setProperty("DIR", path.toString());

        System.out.println("test property" + gvbuffer.getProperty("DIR"));
        Assert.assertNull(gvbuffer.getObject());
        callOperation.perform(gvbuffer);
        Assert.assertEquals("Test MultipartFile ok", new String((byte[]) gvbuffer.getObject()));

    }

    @Test
    public void testMultipartString() throws XMLConfigException, GVException, InterruptedException {

        Node node = XMLConfig.getNode("GVCore.xml", "//*[@name='testStringPart']");
        CallOperation callOperation = new MultipartCallOperation();
        callOperation.init(node);

        GVBuffer gvbuffer = new GVBuffer("Multipart", "testMultipart");

        Assert.assertNull(gvbuffer.getObject());
        callOperation.perform(gvbuffer);
        Assert.assertEquals("Test MultipartString ok", new String((byte[]) gvbuffer.getObject()));

    }

    @Test
    public void testMultipartForm() throws XMLConfigException, GVException, InterruptedException {

        Node node = XMLConfig.getNode("GVCore.xml", "//*[@name='testFormPart']");
        CallOperation callOperation = new MultipartCallOperation();
        callOperation.init(node);

        GVBuffer gvbuffer = new GVBuffer("Multipart", "testMultipart");

        Assert.assertNull(gvbuffer.getObject());
        callOperation.perform(gvbuffer);
        Assert.assertEquals("Test MultipartForm ok", new String((byte[]) gvbuffer.getObject()));

    }

    @AfterClass
    public static void finish() {

        webserver.stop();
    }

}