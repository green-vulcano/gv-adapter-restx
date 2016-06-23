/*
 * Copyright (c) 2009-2015 GreenVulcano ESB Open Source Project. All rights
 * reserved.
 * 
 * This file is part of GreenVulcano ESB.
 * 
 * GreenVulcano ESB is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * 
 * GreenVulcano ESB is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with GreenVulcano ESB. If not, see <http://www.gnu.org/licenses/>.
 */
package it.greenvulcano.gvesb.virtual.rest;

import it.greenvulcano.configuration.XMLConfig;
import it.greenvulcano.gvesb.buffer.GVBuffer;
import it.greenvulcano.gvesb.internal.data.GVBufferPropertiesHelper;
import it.greenvulcano.gvesb.virtual.CallException;
import it.greenvulcano.gvesb.virtual.CallOperation;
import it.greenvulcano.gvesb.virtual.ConnectionException;
import it.greenvulcano.gvesb.virtual.InitializationException;
import it.greenvulcano.gvesb.virtual.InvalidDataException;
import it.greenvulcano.gvesb.virtual.OperationKey;
import it.greenvulcano.util.metadata.PropertiesHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    private OperationKey key = null;      
    private String url = null;
    private String method = null;
    
    private int connectionTimeout,readTimeout;
    
    private Map<String, String> headers = new LinkedHashMap<>();
    private Map<String, String> params = new LinkedHashMap<>();
    
    private String body;
   
    @Override
    public void init(Node node) throws InitializationException
    {
        logger.debug("Init start");
        try {            
                	
        	String host = XMLConfig.get(node.getParentNode(), "@endpoint");
            String uri = XMLConfig.get(node, "@request-uri");
            method = XMLConfig.get(node, "@method");
            url = host.concat(uri);        
            connectionTimeout = XMLConfig.getInteger(node, "@conn-timeout", 3000);
            readTimeout = XMLConfig.getInteger(node, "@so-timeout", 6000);
            
            fillMap(XMLConfig.getNodeList(node, "./headers/header"), headers);
            fillMap(XMLConfig.getNodeList(node, "./parameters/param"), params);
            
            Node bodyNode =  XMLConfig.getNode(node, "./body");
            if (Objects.nonNull(bodyNode)) { 
            	body = bodyNode.getTextContent();
            } else {
            	body = null;
            }
            
            logger.debug("init - loaded parameters: url= " + url + " - method= " + method );
            logger.debug("Init stop");
      
        } catch (Exception exc) {
            throw new InitializationException("GV_INIT_SERVICE_ERROR", new String[][]{{"message", exc.getMessage()}},
                    exc);
        }

    }

    /*
     * (non-Javadoc)
     *
     * @see
     * it.greenvulcano.gvesb.virtual.CallOperation#perform(it.greenvulcano.gvesb
     * .buffer.GVBuffer)
     */
    @Override
    public GVBuffer perform(GVBuffer gvBuffer) throws ConnectionException, CallException, InvalidDataException {
       
        try {
	          final GVBufferPropertyFormatter formatter = new GVBufferPropertyFormatter(gvBuffer);
	           
	          String expandedUrl = formatter.format(url);
     
			  String querystring = params.entrySet().stream()
	           		 .map(e -> formatter.formatAndEncode(e.getKey()) + "=" + formatter.formatAndEncode(e.getValue()))	           		
	           		 .collect(Collectors.joining("&", "?", ""));
	           logger.debug("Calling url "+expandedUrl+querystring); 
	           URL requestUrl = new URL(expandedUrl+querystring);
	           
	           HttpURLConnection httpURLConnection = (HttpURLConnection) requestUrl.openConnection();
	           httpURLConnection.setRequestMethod(method);
	           httpURLConnection.setConnectTimeout(connectionTimeout);
	           httpURLConnection.setReadTimeout(readTimeout);
	           
	          	           
	           for (Entry<String,String> header : headers.entrySet()) {
					String k = formatter.format(header.getKey());
					String v = formatter.format(header.getValue());
					httpURLConnection.setRequestProperty(k, v);
					
					if ("content-type".equalsIgnoreCase(k) && 
						"application/x-www-form-urlencoded".equalsIgnoreCase(v)) {
						body = querystring.substring(0);
					}
					
	           }
	                     
	           if (Objects.nonNull(body) && body.length()>0) {       
	        	   
	        	   String expandedBody = formatter.format(body);
	        	   httpURLConnection.setDoOutput(true);
	        	   
	        	   OutputStreamWriter outputStreamWriter = new OutputStreamWriter(httpURLConnection.getOutputStream());
	           	   outputStreamWriter.write(expandedBody);
	           	   outputStreamWriter.flush();
	           	   outputStreamWriter.close();
	        	   
	           }
	           
	           httpURLConnection.connect();	          
	           
	           InputStream responseStream = null;
	           
	           try {
	        	   httpURLConnection.getResponseCode();
	        	   responseStream = httpURLConnection.getInputStream();
	           } catch (IOException connectionFail) {
	        	   responseStream = httpURLConnection.getErrorStream();
	           }           
	           
	           InputStreamReader contentReader = new InputStreamReader(responseStream, "UTF-8");	       	   
        	   BufferedReader bufferedReader = new BufferedReader(contentReader);
	     
	       	   String response = bufferedReader.lines().collect(Collectors.joining("\n"));	       		       	   
	       	   gvBuffer.setObject(response);	           
	           
	           gvBuffer.setProperty(RESPONSE_STATUS, "" + httpURLConnection.getResponseCode());
	           gvBuffer.setProperty(RESPONSE_MESSAGE, httpURLConnection.getResponseMessage());
	           
	           for (Entry<String, List<String>> header : httpURLConnection.getHeaderFields().entrySet()){
	           	   if(Objects.nonNull(header.getKey()) &&  Objects.nonNull(header.getValue())) {
		        	   gvBuffer.setProperty(RESPONSE_HEADER_PREFIX.concat(header.getKey().toUpperCase()), 
		           							header.getValue().stream().collect(Collectors.joining(";")));
	           	   }
	           }	           
	           
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
        
    private void fillMap(NodeList sourceNodeList, Map<String,String> destinationMap) {
    	        
        IntStream.range(0, sourceNodeList.getLength())
        	.mapToObj(sourceNodeList::item)
        	.forEach(node->{
        		try {
        			destinationMap.put(XMLConfig.get(node, "@name"), XMLConfig.get(node, "@value"));
        		} catch (Exception e) {
        			logger.error("Error configuring headers", e);
        		}
        	});
    }

    /*
     * (non-Javadoc)
     *
     * @see it.greenvulcano.gvesb.virtual.Operation#cleanUp()
     */
    @Override
    public void cleanUp()
    {
        // do nothing
    }

    /*
     * (non-Javadoc)
     *
     * @see it.greenvulcano.gvesb.virtual.Operation#destroy()
     */
    @Override
    public void destroy()
    {
        // do nothing
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * it.greenvulcano.gvesb.virtual.Operation#getServiceAlias(it.greenvulcano
     * .gvesb.buffer.GVBuffer)
     */
    @Override
    public String getServiceAlias(GVBuffer gvBuffer)
    {
        return gvBuffer.getService();
    }


    /**
     * @see it.greenvulcano.gvesb.virtual.Operation#setKey(it.greenvulcano.gvesb.virtual.OperationKey)
     */
    @Override
    public void setKey(OperationKey key)
    {
        this.key = key;
    }

    /**
     * @see it.greenvulcano.gvesb.virtual.Operation#getKey()
     */
    @Override
    public OperationKey getKey()
    {
        return key;
    }
}
