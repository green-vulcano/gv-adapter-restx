package it.greenvulcano.gvesb.virtual.multipart;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Node;

import it.greenvulcano.configuration.XMLConfig;
import it.greenvulcano.configuration.XMLConfigException;
import it.greenvulcano.gvesb.buffer.GVBuffer;
import it.greenvulcano.gvesb.buffer.GVException;
import it.greenvulcano.gvesb.virtual.CallOperation;
import it.greenvulcano.gvesb.virtual.gv_multipart.MultipartCallOperation;
import net.codestory.http.WebServer;
import net.codestory.http.payload.Payload;

public class MultipartCallOperationTest {
	
	private WebServer webserver = null;
	private Payload payloadFile;
	private Payload payloadByteArray;
	private Payload payloadString;
	private Payload payloadForm;
	
	@Before	
	public void init() {
		   XMLConfig.setBaseConfigPath(getClass().getClassLoader().getResource(".").getPath());
			webserver = new WebServer();
			webserver.configure(routes -> {
				routes.post("/testbytearraypart", (context) -> {
						if(context.request().parts().get(0).fileName().equals("bla.zip")
								&& context.request().parts().get(0).contentType().equals("application/zip")
								&& context.request().parts().get(0).isFile() == true){
							payloadByteArray =  new Payload("Test MultipartByteArray ok");
						}
					return payloadByteArray;
				});
				routes.post("/testfilepart", (context) -> {
					if(context.request().parts().get(0).fileName().equals("blaf_20171004140932.zip")
							&& context.request().parts().get(0).contentType().equals("application/zip")
							&& context.request().parts().get(0).isFile() == true){
						payloadFile =  new Payload("Test MultipartFile ok");
					}
					return payloadFile;
				});
				routes.post("/teststringpart", (context) -> {
					if(context.request().parts().get(0).content().equals("ProvaStringaLalalala")
							&& context.request().parts().get(0).contentType().equals("text/plain")){
						payloadString = new Payload("Test MultipartString ok");
					}
					return payloadString;
				});
				routes.post("/testformpart", (context) -> {
					if(context.request().content().contains("Name")
							&& context.request().content().contains("Ajeje")
							&& context.request().content().contains("Surname")
							&& context.request().content().contains("Brazorf")){
						payloadForm = new Payload("Test MultipartForm ok");
					}
					return payloadForm;
				});
			}).start(8888);
			
			
	}
	
	@Test
	public void testMultipartByteArray() throws XMLConfigException, GVException, InterruptedException, IOException, URISyntaxException{
		
		Node node = XMLConfig.getNode("GVSystems.xml", "//*[@name='testByteArrayPart']");		
		CallOperation callOperation = new MultipartCallOperation();
		callOperation.init(node);
		
		Path path = Paths.get(getClass().getClassLoader().getResource("default.zip").toURI());
		byte[] gvBufferFile = Files.readAllBytes(path);
		GVBuffer gvbuffer = new GVBuffer("Multipart", "testMultipart");
		gvbuffer.setObject(gvBufferFile);
		
		callOperation.perform(gvbuffer);
		Assert.assertEquals("Test MultipartByteArray ok", payloadByteArray.rawContent());
	}
	
	// TODO: da aggiustare @Test
	public void testMultipartFile() {
		try {
			Node node = XMLConfig.getNode("GVSystems.xml", "//*[@name='testFilePart']");		
			CallOperation callOperation = new MultipartCallOperation();
			callOperation.init(node);
			
			GVBuffer gvbuffer = new GVBuffer("Multipart", "testMultipart");
            gvbuffer.setProperty("DIR", getClass().getClassLoader().getResource(".").getPath());					
			Assert.assertNull(gvbuffer.getObject());		
			callOperation.perform(gvbuffer);
			Assert.assertEquals("Test MultipartFile ok", payloadFile.rawContent());
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
		
	}

	@Test
	public void testMultipartString() throws XMLConfigException, GVException, InterruptedException{
		Node node = XMLConfig.getNode("GVSystems.xml", "//*[@name='testStringPart']");		
		CallOperation callOperation = new MultipartCallOperation();
		callOperation.init(node);
		
		GVBuffer gvbuffer = new GVBuffer("Multipart", "testMultipart");
				
		Assert.assertNull(gvbuffer.getObject());		
		callOperation.perform(gvbuffer);
		Assert.assertEquals("Test MultipartString ok", payloadString.rawContent());
		
	}

	@Test
	public void testMultipartForm() throws XMLConfigException, GVException, InterruptedException{
		Node node = XMLConfig.getNode("GVSystems.xml", "//*[@name='testFormPart']");		
		CallOperation callOperation = new MultipartCallOperation();
		callOperation.init(node);
		
		GVBuffer gvbuffer = new GVBuffer("Multipart", "testMultipart");
				
		Assert.assertNull(gvbuffer.getObject());		
		callOperation.perform(gvbuffer);
		Assert.assertEquals("Test MultipartForm ok", payloadForm.rawContent());
		
	}
	
	
	@After
	public void finish(){
		webserver.stop();
	}

}
