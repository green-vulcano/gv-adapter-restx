/*******************************************************************************
 * Copyright (c) 2009, 2017 GreenVulcano ESB Open Source Project.
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
package it.greenvulcano.gvesb.virtual.gv_multipart;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.FormBodyPartBuilder;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import it.greenvulcano.configuration.XMLConfig;
import it.greenvulcano.configuration.XMLConfigException;
import it.greenvulcano.gvesb.buffer.GVBuffer;
import it.greenvulcano.gvesb.virtual.CallException;
import it.greenvulcano.gvesb.virtual.CallOperation;
import it.greenvulcano.gvesb.virtual.ConnectionException;
import it.greenvulcano.gvesb.virtual.InitializationException;
import it.greenvulcano.gvesb.virtual.InvalidDataException;
import it.greenvulcano.gvesb.virtual.OperationKey;



/**
 * 
 * @version 4.0 november/2017
 * @author GreenVulcano Developer Team
 */
public class MultipartCallOperation implements CallOperation {
    
    private static final Logger logger 	= LoggerFactory.getLogger(MultipartCallOperation.class);

    /**
     * The configured operation key
     */    
    private OperationKey key = null;
    
    /**
     * The name of the multipat-call
     */
    private String name;
    
    /**
     * The URL called by the multipart call 
     */
    private String url = null;
    
    /**
     * The connection timeout
     */
    private int connectionTimeout;
    
    /**
     * the read timeout
     */
    private int readTimeout;
    
    /**
     * The body of the call
     */
    private String body;
    
    /**
     * The map of call the headers
     */
    private Map<String, String> headers = new LinkedHashMap<>();
    
    /**
     * The map of call the parameters
     */
    private Map<String, String> params = new LinkedHashMap<>();
    
    /**
     * The multipart entity builder
     */
    MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
    
    /**
     * The http entity
     */
    HttpEntity httpEntity = null;
    


    private ContentType contentType;
    
    private static final String RESPONSE_STATUS        = "GVHTTP_RESPONSE_STATUS";
    private static final String RESPONSE_MESSAGE       = "GVHTTP_RESPONSE_MESSAGE";
    private static final String RESPONSE_HEADER_PREFIX = "GVHTTP_RESPONSE_HEADER_";
    
	private FormBodyPart formBodyPart;
	private FormBodyPartBuilder formBodyBuilder;
    private File file;
	private String fileName;
	private boolean isByteArray = false;
	
    /**
     * 
     * @param node
     * 			The configuration node containing all informations. 
     * 
     * @see it.greenvulcano.gvesb.virtual.Operation#init(org.w3c.dom.Node)
     */
    @Override
    public void init(Node node) throws InitializationException  {
        logger.debug("Init start");
        
        try {
        	name =  XMLConfig.get(node, "@name");
        	String host = XMLConfig.get(node.getParentNode(), "@endpoint");
            String uri = XMLConfig.get(node, "@request-uri");
            url = host.concat(uri);
            connectionTimeout = XMLConfig.getInteger(node, "@conn-timeout", 3000);
            readTimeout = XMLConfig.getInteger(node, "@so-timeout", 6000);

            readMultipartCallConfiguration(node);
            readMultipartCallParts(node);
        	
        	logger.debug("Init stop");
        } catch (Exception exc) {
            throw new InitializationException("GV_INIT_SERVICE_ERROR", new String[][]{{"message", exc.getMessage()}},
                    exc);
        }

    }
    
    /**
     * 
     * Reads the configuration of the Multipart node
     * 
     * @param node
     * @throws XMLConfigException
     */
    private void readMultipartCallConfiguration(Node node) throws XMLConfigException {	
    		
	    	if (XMLConfig.exists(node, "./headers")) {
	    		fillMap(XMLConfig.getNodeList(node, "./headers/header"), headers);
	    		if(headers.get("Content-Type") != null){
	    			multipartEntityBuilder.setContentType(getContentType(headers.get("Content-Type")));
	    			headers.remove("Content-Type");
	    		}
	    		if(headers.get("charset") != null){
	    			multipartEntityBuilder.setCharset(Charset.forName(headers.get("charset")));
	    			headers.remove("charset");
	    		}
	    	}
	    	
	    	if (XMLConfig.exists(node, "./parameters")) {
	    		fillMap(XMLConfig.getNodeList(node, "./parameters/param"), params);
	    	}        
	        
	        Node bodyNode =  XMLConfig.getNode(node, "./body");
	        //TODO:da vedere se serve
//	        if (Objects.nonNull(bodyNode)) { 
//	        	
//	        	sendGVBufferObject = Boolean.valueOf(XMLConfig.get(bodyNode, "@gvbuffer-object", "false"));
//	        	
//	        	body = bodyNode.getTextContent();
//	        } else {
//	        	body = null;
//	        }
    }
    
    /**
     * 
     * Reads the parts of the Multipart node
     * 
     * @param node
     * @throws XMLConfigException
     */
    private void readMultipartCallParts(Node node) throws XMLConfigException {
    	if (XMLConfig.exists(node, "./parts")) {
    		if (XMLConfig.exists(node, "./parts/byteArrayPart")) {
    				createByteArrayPart(XMLConfig.getNodeList(node, "./parts/byteArrayPart/part"), 
    									XMLConfig.get(node, "./parts/byteArrayPart/@name"),
    									XMLConfig.get(node, "./parts/byteArrayPart/@contenttype"),
    									XMLConfig.get(node, "./parts/byteArrayPart/@filename"));
    		}
    		if (XMLConfig.exists(node, "./parts/filePart")) {
				createFilePart(XMLConfig.get(node, "./parts/filePart/@name"), 
						XMLConfig.get(node, "./parts/filePart/@filepath"), 
						XMLConfig.get(node, "./parts/filePart/@filename"), 
						XMLConfig.get(node, "./parts/filePart/@contenttype")
							   	);
    		}
    		if (XMLConfig.exists(node, "./parts/stringPart")) {
				createStringPart(XMLConfig.getNodeList(node, "./parts/stringPart"), 
									XMLConfig.get(node, "./parts/stringPart/@name"),
									XMLConfig.get(node, "./parts/stringPart/@contenttype"));
    		}
    		if (XMLConfig.exists(node, "./parts/formPart")) {
				createFormPart(XMLConfig.getNodeList(node, "./parts/formPart/param"), 
								XMLConfig.get(node, "./parts/formPart/@name"),
								XMLConfig.get(node, "./parts/formPart/@contenttype"));
    		}
    	}
    }
    
    /**
     * Get the text from a CDATA
     * 
     * @param e
     * @return
     */
    public static String getCharacterDataFromElement(Element element) {
        NodeList list = element.getChildNodes();
        String data;

        for(int index = 0; index < list.getLength(); index++){
            if(list.item(index) instanceof CharacterData){
                CharacterData child = (CharacterData) list.item(index);
                data = child.getData();
                if(data != null && data.trim().length() > 0)
                    return child.getData();
            }
        }
        return "";
    }

	/**
	 * Adds a Byte Array Part on the Multipart call
	 * 
	 * @param nodeList
	 * @param name
	 */
    private void createByteArrayPart(NodeList nodeList, String name, String contentType, String fileName) {
    	
		this.contentType = getContentType(contentType);
		this.fileName = fileName;
    	isByteArray = true;
	}

	/**
	 * Adds a File Part on the Multipart call
	 * 
	 * @param nodeList
	 * @param name
	 */
    private void createFilePart(String PartName, String filePath, String fileName, String contentType) {
    	this.contentType = getContentType(contentType);
    	file = new File(filePath);
    	FileBody filePart = new FileBody(file, this.contentType, fileName);
    	multipartEntityBuilder.addPart(PartName, filePart);
	}

	/**
	 * Adds a String Part on the Multipart call
	 * 
	 * @param nodeList
	 * @param name
	 */
    private void createStringPart(NodeList nodeList, String name, String contentType) {
		this.contentType = getContentType(contentType);
	        Element element = (Element) nodeList.item(0);

//    	StringBody stringPart = new StringBody(getCharacterDataFromElement(element), this.contentType);
    	multipartEntityBuilder.addTextBody(name, getCharacterDataFromElement(element),this.contentType);  		
	 }
    
	/**
	 * Adds a Form Part on the Multipart call
	 * 
	 * @param nodeList
	 * @param name
	 */
    private void createFormPart(NodeList nodeList, String name, String contentType) {
   
		this.contentType = getContentType(contentType);
    	StringBody stringBody = new StringBody(name, this.contentType);
		formBodyBuilder = FormBodyPartBuilder.create(name, stringBody);

    	IntStream.range(0, nodeList.getLength())
			.mapToObj(nodeList::item)
			.forEach(node->{
			    	try {
						if (!("Content-Type".equals(XMLConfig.get(node, "@name")))) {
				    		formBodyBuilder.addField(XMLConfig.get(node, "@name"), XMLConfig.get(node, "@value"));
						}
					}
					catch (XMLConfigException e) {
						e.printStackTrace();
					}
			});
    	formBodyPart = formBodyBuilder.setName(name).build();
    	multipartEntityBuilder.addPart(formBodyPart);
	}

	/**
     * Adds on a Map the key and the value the given NodeList
     * 
     * @param sourceNodeList
     * @param destinationMap
     */
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

    /**
     * creates a Content-Type element
     * 
     * @param contentType
     * @return created contentType
     */
    private ContentType getContentType(String contentType) {
    	
		return ContentType.create(contentType);
	}
           
    /**
     * 
     * @param gvBuffer 
     * 			for transport data in GreenVulcano
     * @return the GVBuffer
     * 
     * @see it.greenvulcano.gvesb.virtual.CallOperation#perform(it.greenvulcano.gvesb.buffer.GVBuffer)
     */
    @Override
    public GVBuffer perform(GVBuffer gvBuffer) throws ConnectionException, CallException, InvalidDataException {

        StringBuffer callDump = new StringBuffer();
        callDump.append("Performing RestCallOperation "+name)
                .append("\n        ")
                .append("URL: ")
                .append(url);

		if (isByteArray == true && gvBuffer.getObject()!=null) {
			byte[] requestData;
			if (gvBuffer.getObject() instanceof byte[]) {
			   requestData = (byte[]) gvBuffer.getObject();
			} else {
			   requestData = gvBuffer.getObject().toString().getBytes();
			}	   	        	   
			callDump.append("\n        ").append("Content-Length: "+requestData.length);
        	ByteArrayBody byteArrayPart = new ByteArrayBody(requestData, contentType, fileName);
        	multipartEntityBuilder.addPart(name, byteArrayPart);
		}

        try {
        	CloseableHttpClient httpClient = HttpClients.createDefault();
        	HttpPost httpPost = new HttpPost(url);
        	httpEntity = multipartEntityBuilder.build();
            String responseString = EntityUtils.toString(httpEntity);
        	httpPost.setEntity(multipartEntityBuilder.build());
        	for (Map.Entry<String, String> header : headers.entrySet()) {
        	    String key = header.getKey();
        	    String value = header.getValue();
        	    httpPost.setHeader(key, value);
        	}
        	CloseableHttpResponse response = httpClient.execute(httpPost);
            HttpEntity responseEntity = response.getEntity();
            Header[] responseHeaders = response.getAllHeaders();
            
            Header contentType = responseEntity.getContentType();

	           InputStream responseStream = null;
	           
	        	   response.getStatusLine();
	        	   responseStream = responseEntity.getContent();
	           
	           for (Header header : response.getAllHeaders()){
	           	   if(Objects.nonNull(header)) {
		        	   gvBuffer.setProperty(header.getName(), header.getValue());
	           	   }
	           }
	          
	           if (responseStream!=null) {
	        	   
	        	   byte[] responseData = IOUtils.toByteArray(responseStream);
		           String responseContentType = Optional.ofNullable(gvBuffer.getProperty(RESPONSE_HEADER_PREFIX.concat("CONTENT-TYPE"))).orElse("");
		           
		           if (responseContentType.startsWith("application/json") || responseContentType.startsWith("application/javascript") ) {
		        	   gvBuffer.setObject(new String(responseData, "UTF-8"));
		           } else {
		        	   gvBuffer.setObject(responseData);   
		           }
				
	           } else { // No content
	        	   gvBuffer.setObject(null);
	           }
	           
	           gvBuffer.setProperty(RESPONSE_STATUS, String.valueOf(response.getStatusLine()));	           
	           gvBuffer.setProperty(RESPONSE_MESSAGE, String.valueOf(response));    
	           
	           callDump.append("\n " +gvBuffer);

//      	   byte[] responseData = IOUtils.toByteArray(response.getEntity().toString());
// 	           String responseContentType = Optional.ofNullable(gvBuffer.getProperty(RESPONSE_HEADER_PREFIX.concat("CONTENT-TYPE"))).orElse("");
// 	           
// 	           if (responseContentType.startsWith("application/json") || responseContentType.startsWith("application/javascript") ) {
// 	        	   gvBuffer.setObject(new String(responseData, "UTF-8"));
// 	           } else {
// 	        	   gvBuffer.setObject(response);   
// 	           }
//
//	           gvBuffer.setProperty(RESPONSE_STATUS, String.valueOf(response.getStatusLine()));	           
//	           gvBuffer.setProperty(RESPONSE_MESSAGE, Optional.ofNullable(httpURLConnection.getResponseMessage()).orElse("NULL"));          	           
        	
        	
        	response.close();

        	
        	
        	
     	   	logger.debug(callDump.toString());
        } catch (Exception exc) {
            throw new CallException("GV_CALL_SERVICE_ERROR", new String[][]{{"service", gvBuffer.getService()},
                    {"system", gvBuffer.getSystem()}, {"tid", gvBuffer.getId().toString()},
                    {"message", exc.getMessage()}}, exc);
        }
        return gvBuffer;
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
