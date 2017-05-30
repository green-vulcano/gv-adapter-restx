/*******************************************************************************
 * Copyright (c) 2009, 2016 GreenVulcano ESB Open Source Project.
 * All rights reserved.
 *
 * This file is part of GreenVulcano ESB.
 *
 * GreenVulcano ESB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GreenVulcano ESB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with GreenVulcano ESB. If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package it.greenvulcano.gvesb.virtual.rest;

import it.greenvulcano.configuration.XMLConfig;
import it.greenvulcano.configuration.XMLConfigException;
import it.greenvulcano.gvesb.buffer.GVBuffer;
import it.greenvulcano.gvesb.internal.data.GVBufferPropertiesHelper;
import it.greenvulcano.gvesb.virtual.CallException;
import it.greenvulcano.gvesb.virtual.CallOperation;
import it.greenvulcano.gvesb.virtual.ConnectionException;
import it.greenvulcano.gvesb.virtual.InitializationException;
import it.greenvulcano.gvesb.virtual.InvalidDataException;
import it.greenvulcano.gvesb.virtual.OperationKey;
import it.greenvulcano.util.metadata.PropertiesHandler;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


/**
 * 
 * @version 4.0 29/april/2016
 * @author GreenVulcano Developer Team
 */
public class RestCallOperation implements CallOperation {
    
    private static final String RESPONSE_STATUS        = "GVHTTP_RESPONSE_STATUS";
    private static final String RESPONSE_MESSAGE       = "GVHTTP_RESPONSE_MESSAGE";
    private static final String RESPONSE_HEADER_PREFIX = "GVHTTP_RESPONSE_HEADER_";
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(RestCallOperation.class);
    private String name;
    private OperationKey key = null;      
    private String url = null;
    private String method = null;
    
    private int connectionTimeout,readTimeout;
    
    private String truststorePath = null;
    private String truststorePassword = null;
    private String truststoreAlgorithm = null;
       
    private Map<String, String> headers = new LinkedHashMap<>();
    private Map<String, String> params = new LinkedHashMap<>();
    
    private String body;
    
    private boolean sendGVBufferObject = false;
   
    @Override
    public void init(Node node) throws InitializationException
    {
        logger.debug("Init start");
        try {            
            name =  XMLConfig.get(node, "@name");  	
        	
            String host = XMLConfig.get(node.getParentNode(), "@endpoint");
            String uri = XMLConfig.get(node, "@request-uri");
            method = XMLConfig.get(node, "@method");
            url = host.concat(uri);        
            connectionTimeout = XMLConfig.getInteger(node, "@conn-timeout", 3000);
            readTimeout = XMLConfig.getInteger(node, "@so-timeout", 6000);
                             
            Node trustStore = XMLConfig.getNode(node.getParentNode(), "./truststore");
            if (Objects.nonNull(trustStore)) { 
            	truststorePath = XMLConfig.get(trustStore, "@path");
            	truststorePassword = XMLConfig.getDecrypted(trustStore, "@password", null);
            	truststoreAlgorithm = XMLConfig.get(trustStore, "@algorithm", null);            	            	
            }
            
            Node defaults = XMLConfig.getNode(node.getParentNode(), "./rest-call-defaults");
            if (Objects.nonNull(defaults)){
            	readRestCallConfiguration(defaults);
            }            
            
            readRestCallConfiguration(node);                        
            
            logger.debug("init - loaded parameters: url= " + url + " - method= " + method );
            logger.debug("Init stop");
      
        } catch (Exception exc) {
            throw new InitializationException("GV_INIT_SERVICE_ERROR", new String[][]{{"message", exc.getMessage()}},
                    exc);
        }

    }
    
    private void readRestCallConfiguration(Node node) throws XMLConfigException {
    	if (XMLConfig.exists(node, "./headers")) {
    		fillMap(XMLConfig.getNodeList(node, "./headers/header"), headers);
    	}
    	
    	if (XMLConfig.exists(node, "./parameters")) {
    		fillMap(XMLConfig.getNodeList(node, "./parameters/param"), params);
    	}        
        
        Node bodyNode =  XMLConfig.getNode(node, "./body");
        if (Objects.nonNull(bodyNode)) { 
        	
        	sendGVBufferObject = Boolean.valueOf(XMLConfig.get(bodyNode, "@gvbuffer-object", "false"));
        	
        	body = bodyNode.getTextContent();
        } else {
        	body = null;
        }
    }   
    
    private void fillMap(NodeList sourceNodeList, Map<String,String> destinationMap) {
        
    	if (sourceNodeList.getLength()==0) {
    		destinationMap.clear();
    	} else {    	
		    IntStream.range(0, sourceNodeList.getLength())
		    	.mapToObj(sourceNodeList::item)
		    	.forEach(node->{
		    		try {
		    			destinationMap.put(XMLConfig.get(node, "@name"), XMLConfig.get(node, "@value"));
		    		} catch (Exception e) {
		    			logger.error("Fail to read configuration", e);
		    		}
		    	});
    	}
    }

    @Override
    public GVBuffer perform(GVBuffer gvBuffer) throws ConnectionException, CallException, InvalidDataException {
       
        try {
	           final GVBufferPropertyFormatter formatter = new GVBufferPropertyFormatter(gvBuffer);
	           
	           String expandedUrl = formatter.format(url);
	           String querystring = "";
	           
	           if (!params.isEmpty()) {

				    querystring =  params.entrySet().stream()
		           		  .map(e -> formatter.formatAndEncode(e.getKey()) + "=" + formatter.formatAndEncode(e.getValue()))	           		
		           		  .collect(Collectors.joining("&"));	
	        	   
	        	   expandedUrl = expandedUrl.concat("?").concat(querystring);
	           } 
	        	  
	           StringBuffer callDump = new StringBuffer();
	           callDump.append("Perfoming RestCallOperation "+name)
	                   .append("\n        ")
	                   .append("URL: ")
	                   .append(expandedUrl);
	           
	           URL requestUrl = new URL(expandedUrl);	           
	           
	           HttpURLConnection httpURLConnection;
	           if (truststorePath!=null && expandedUrl.startsWith("https://")) {
	        	   httpURLConnection  = openSecureConnection(requestUrl);
	           } else {
	        	   httpURLConnection  = (HttpURLConnection) requestUrl.openConnection();
	           }
	           callDump.append("\n        ").append("Method: "+method);
	           
	           callDump.append("\n        ").append("Connection timeout: "+connectionTimeout);
	           callDump.append("\n        ").append("Read timeout: "+readTimeout);
	           
	           httpURLConnection.setRequestMethod(method);
	           httpURLConnection.setConnectTimeout(connectionTimeout);
	           httpURLConnection.setReadTimeout(readTimeout);
	           
	          	           
	           for (Entry<String,String> header : headers.entrySet()) {
					String k = formatter.format(header.getKey());
					String v = formatter.format(header.getValue());
					httpURLConnection.setRequestProperty(k, v);
					callDump.append("\n        ").append("Header: "+k+"="+v);
					if ("content-type".equalsIgnoreCase(k) && 
						"application/x-www-form-urlencoded".equalsIgnoreCase(v)) {
						body = querystring;
					}
					
	           }
	                     
	           if (sendGVBufferObject && gvBuffer.getObject()!=null) {
	        	   byte[] requestData;
	        	   if (gvBuffer.getObject() instanceof byte[]) {
	        		   requestData = (byte[]) gvBuffer.getObject();
	        	   } else {
	        		   requestData = gvBuffer.getObject().toString().getBytes();
	        		   
	        	   }	        	   
	        	   httpURLConnection.setRequestProperty("Content-Length", Integer.toString(requestData.length));
	        	   callDump.append("\n        ").append("Content-Length: "+requestData.length);
	        	   
	        	   httpURLConnection.setDoOutput(true);
	        	   
	        	   DataOutputStream dataOutputStream = new DataOutputStream(httpURLConnection.getOutputStream());        	 
	        	   dataOutputStream.write(requestData);
	        	   
	        	   dataOutputStream.flush();
	        	   dataOutputStream.close();
	        	   
	        	   callDump.append("\n        ").append("Request body: binary");
	           } else if (Objects.nonNull(body) && body.length()>0) {       
	        	   
	        	   String expandedBody = formatter.format(body);
	        	   httpURLConnection.setDoOutput(true);
	        	   
	        	   OutputStreamWriter outputStreamWriter = new OutputStreamWriter(httpURLConnection.getOutputStream());
	           	   outputStreamWriter.write(expandedBody);
	           	   outputStreamWriter.flush();
	           	   outputStreamWriter.close();
	           	   callDump.append("\n        ").append("Request body: "+expandedBody);
	           }
	           
	           
	           logger.debug(callDump.toString());
	           
	           httpURLConnection.connect();	          
	           
	           InputStream responseStream = null;
	           
	           try {
	        	   httpURLConnection.getResponseCode();
	        	   responseStream = httpURLConnection.getInputStream();
	           } catch (IOException connectionFail) {
	        	   responseStream = httpURLConnection.getErrorStream();
	           }           
	           
	           for (Entry<String, List<String>> header : httpURLConnection.getHeaderFields().entrySet()){
	           	   if(Objects.nonNull(header.getKey()) &&  Objects.nonNull(header.getValue())) {
		        	   gvBuffer.setProperty(RESPONSE_HEADER_PREFIX.concat(header.getKey().toUpperCase()), 
		           							header.getValue().stream().collect(Collectors.joining(";")));
	           	   }
	           }
	           
	       	   byte[] responseData = IOUtils.toByteArray(responseStream);
	           String responseContentType = Optional.ofNullable(gvBuffer.getProperty(RESPONSE_HEADER_PREFIX.concat("CONTENT-TYPE"))).orElse("");
	           if (responseContentType.startsWith("application/json")) {
	        	   gvBuffer.setObject(new String(responseData, "UTF-8"));
	           } else {
	        	   gvBuffer.setObject(responseData);   
	           }	       	   	           
	           
	           gvBuffer.setProperty(RESPONSE_STATUS, "" + httpURLConnection.getResponseCode());
	           gvBuffer.setProperty(RESPONSE_MESSAGE, httpURLConnection.getResponseMessage());
	           
	          	           
	           
	           httpURLConnection.disconnect();       	   
           
        } catch (Exception exc) {
            throw new CallException("GV_CALL_SERVICE_ERROR", new String[][]{{"service", gvBuffer.getService()},
                    {"system", gvBuffer.getSystem()}, {"tid", gvBuffer.getId().toString()},
                    {"message", exc.getMessage()}}, exc);
        }
        return gvBuffer;
    }    
    
    private class GVBufferPropertyFormatter {
    	
    	private final GVBuffer gvBuffer;
    	private Map<String, Object> params;
    	
    	public GVBufferPropertyFormatter(GVBuffer gvBuffer) {			
			this.gvBuffer = gvBuffer;
		}
    	
    	
    	String format(String entry){
    		
    		try {
    			if (Objects.isNull(params)) {
    				params = GVBufferPropertiesHelper.getPropertiesMapSO(gvBuffer, true);
    			}
    			
    			return PropertiesHandler.expand(entry, params, gvBuffer.getObject());
    		} catch (Exception exception) {
    			logger.error("Error formatting value: "+entry, exception);
    		}
        	return entry;
        }
    	
    	String formatAndEncode(String entry){
    		String value =  format(entry);
    		try {    			
    			return URLEncoder.encode(value, "UTF-8");
    		} catch (Exception exception) {
    			logger.error("Error encoding value: "+entry, exception);
    		}
        	return entry;
    	}
    	

    }        
    
    private HttpsURLConnection openSecureConnection(URL url) throws Exception{
				
		InputStream keyStream = new FileInputStream(truststorePath);
			
		KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());		
		keystore.load(keyStream, Optional.ofNullable(truststorePassword).orElse("").toCharArray());
		
		TrustManagerFactory trustFactory =	TrustManagerFactory.getInstance(Optional.ofNullable(truststoreAlgorithm)
															   						.orElseGet(TrustManagerFactory::getDefaultAlgorithm));
		trustFactory.init(keystore);
					
		SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustFactory.getTrustManagers(), null);					
		
        HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();        
        
        httpsURLConnection.setSSLSocketFactory(context.getSocketFactory());
        
        httpsURLConnection.setHostnameVerifier(new HostnameVerifier() {      
		    public boolean verify(String hostname, SSLSession session) {
		        return true;
		    }
		});
        
        return httpsURLConnection;
    }   

    @Override
    public void cleanUp(){
        // do nothing
    }
    
    @Override
    public void destroy(){
        // do nothing
    }

    @Override
    public String getServiceAlias(GVBuffer gvBuffer){
        return gvBuffer.getService();
    }

    @Override
    public void setKey(OperationKey key){
        this.key = key;
    }
    
    @Override
    public OperationKey getKey(){
        return key;
    }
}
