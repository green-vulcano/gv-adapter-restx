package it.greenvulcano.gvesb.virtual.rest;

import java.util.UUID;

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
import net.codestory.http.WebServer;

public class RestCallOperationTest {
	
	private WebServer webserver = null;
	
	@Before
	public void init() {
		XMLConfig.setBaseConfigPath(getClass().getClassLoader().getResource(".").getPath());
		
		webserver = new WebServer();
				
		webserver.configure(routes -> {
		
			routes.get("/test", "Test response");
			routes.get("/testparams?to=:to&from=:from", (context, to, from) -> "Test params to:" + to + " from:" + from);
			routes.get("/testdefault?token=:token", (context, token)-> "Default param:"+token+" Default header:"+context.header("X-custom-header"));
		}).start(8888);
		
		
	}
	
	@Test
	public void testSimpleGet() throws XMLConfigException, GVException, InterruptedException {
		
		Node node = XMLConfig.getNode("GVSystems.xml", "//*[@name='simple-call']");		
		CallOperation callOperation = new RestCallOperation();
		callOperation.init(node);
		
		GVBuffer gvbuffer = new GVBuffer("test", "test-rest-call");
				
		Assert.assertNull(gvbuffer.getObject());		
		callOperation.perform(gvbuffer);
		Assert.assertEquals("Test response", gvbuffer.getObject());
		
	}
	
	
	@Test
	public void testGetWithParams() throws XMLConfigException, GVException, InterruptedException {
		
		Node node = XMLConfig.getNode("GVSystems.xml", "//*[@name='params-call']");		
		CallOperation callOperation = new RestCallOperation();
		callOperation.init(node);
		
		String param1 = "value1";
		String param2 = "value2";
		
		GVBuffer gvbuffer = new GVBuffer("test", "test-rest-call");
		gvbuffer.setProperty("PARAM_1", param1 );
		gvbuffer.setProperty("PARAM_2", param2 );
		
		Assert.assertNull(gvbuffer.getObject());		
		callOperation.perform(gvbuffer);
		Assert.assertEquals("Test params to:value1 from:value2", gvbuffer.getObject());
		
	}
	
	@Test
	public void testDefaultSettings() throws XMLConfigException, GVException, InterruptedException {
		
		Node node = XMLConfig.getNode("GVSystems.xml", "//*[@name='simple-default']");		
		CallOperation callOperation = new RestCallOperation();
		callOperation.init(node);
		
		String token = UUID.randomUUID().toString();
				
		GVBuffer gvbuffer = new GVBuffer("test", "test-rest-call");
		gvbuffer.setProperty("PARAM_TOKEN", token);
				
		Assert.assertNull(gvbuffer.getObject());		
		callOperation.perform(gvbuffer);
		
		Assert.assertEquals("Default param:"+token+" Default header:custom", gvbuffer.getObject());
		
		
	}
	
	@Test
	public void testOverrideDefaultSettings() throws XMLConfigException, GVException, InterruptedException {
		
		Node node = XMLConfig.getNode("GVSystems.xml", "//*[@name='simple-override']");		
		CallOperation callOperation = new RestCallOperation();
		callOperation.init(node);
		
		String token = UUID.randomUUID().toString();
				
		GVBuffer gvbuffer = new GVBuffer("test", "test-rest-call");
		gvbuffer.setProperty("PARAM_TOKEN", token);
				
		Assert.assertNull(gvbuffer.getObject());		
		callOperation.perform(gvbuffer);
		Assert.assertEquals("Default param:null Default header:null", gvbuffer.getObject());
		
	}
	
	@After
	public void finish(){
		webserver.stop();
	}

}
