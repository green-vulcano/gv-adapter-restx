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
package it.greenvulcano.gvesb.virtual.google;

import it.greenvulcano.configuration.XMLConfig;
import it.greenvulcano.gvesb.buffer.GVBuffer;
import it.greenvulcano.gvesb.virtual.CallException;
import it.greenvulcano.gvesb.virtual.CallOperation;
import it.greenvulcano.gvesb.virtual.ConnectionException;
import it.greenvulcano.gvesb.virtual.InitializationException;
import it.greenvulcano.gvesb.virtual.InvalidDataException;
import it.greenvulcano.gvesb.virtual.OperationKey;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;

/**
 * 
 * @version 4.0 07/august/2016
 * @author GreenVulcano Developer Team
 */
public class GoogleAuthenticationCallOperation implements CallOperation {
    
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(GoogleAuthenticationCallOperation.class);    
    private OperationKey key = null;
    
    protected String name;
    
    private Path certificatePath = null;
    private String outputProperty = null;
    private final Set<String> scopes = new LinkedHashSet<>();
       
    @Override
    public void init(Node node) throws InitializationException  {
        logger.debug("Init start");
        try {            
            name =  XMLConfig.get(node, "@name");
            outputProperty = XMLConfig.get(node, "@property", "*OBJECT");
            certificatePath = Paths.get(XMLConfig.get(node, "@certificate"));
                        
            NodeList configuredScopes = XMLConfig.getNodeList(node, "./scopes/scope");
            
            IntStream.range(0, configuredScopes.getLength())
			    	.mapToObj(configuredScopes::item)
			    	.forEach(scopeNode->{
			    		try {
			    			scopes.add(scopeNode.getTextContent());
			    		} catch (Exception e) {
			    			logger.error("Fail to read configuration", e);
			    		}
			    	});
            
        	     
        } catch (Exception exc) {
            throw new InitializationException("GV_INIT_SERVICE_ERROR", new String[][]{{"message", exc.getMessage()}},
                    exc);
        }

    }
           

    @Override
    public GVBuffer perform(GVBuffer gvBuffer) throws ConnectionException, CallException, InvalidDataException {
       
        try {               
        	
        	 GoogleCredential googleCredential = GoogleCredential
       		      .fromStream(Files.newInputStream(certificatePath, StandardOpenOption.READ))
       		      .createScoped(scopes);
       		  googleCredential.refreshToken();
       		  
       		  String accessToken = googleCredential.getAccessToken();
       		  
       		  gvBuffer.setProperty(outputProperty, accessToken);
           
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
