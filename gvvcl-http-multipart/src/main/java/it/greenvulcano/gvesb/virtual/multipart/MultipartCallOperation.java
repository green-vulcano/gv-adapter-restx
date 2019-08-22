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
package it.greenvulcano.gvesb.virtual.multipart;

import java.io.File;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.FormBodyPartBuilder;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import it.greenvulcano.configuration.XMLConfig;
import it.greenvulcano.gvesb.buffer.GVBuffer;
import it.greenvulcano.gvesb.virtual.CallException;
import it.greenvulcano.gvesb.virtual.CallOperation;
import it.greenvulcano.gvesb.virtual.ConnectionException;
import it.greenvulcano.gvesb.virtual.InitializationException;
import it.greenvulcano.gvesb.virtual.InvalidDataException;
import it.greenvulcano.gvesb.virtual.OperationKey;
import it.greenvulcano.util.metadata.PropertiesHandler;

/**
 * 
 * @version 4.1 march/2019
 * @author GreenVulcano Developer Team
 */
public class MultipartCallOperation implements CallOperation {

    private static final Logger logger = LoggerFactory.getLogger(MultipartCallOperation.class);

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
     * The map of call the headers
     */
    private Map<String, String> headers = new LinkedHashMap<>();

    /**
     * The map of call the parameters
     */
    private Map<String, String> params = new LinkedHashMap<>();

    private static final String RESPONSE_STATUS = "GVHTTP_RESPONSE_STATUS";
    private static final String RESPONSE_MESSAGE = "GVHTTP_RESPONSE_MESSAGE";
    private static final String RESPONSE_HEADER_PREFIX = "GVHTTP_RESPONSE_HEADER_";

    private NodeList parts;

    /**
     * 
     * @param node
     * The configuration node containing all informations.
     * 
     * @see it.greenvulcano.gvesb.virtual.Operation#init(org.w3c.dom.Node)
     */
    @Override
    public void init(Node node) throws InitializationException {

        logger.debug("Init MultipartCallOperation node");

        try {
            name = XMLConfig.get(node, "@name");
            String host = XMLConfig.get(node.getParentNode(), "@endpoint");
            String uri = XMLConfig.get(node, "@request-uri");
            url = host.concat(uri);
            connectionTimeout = XMLConfig.getInteger(node, "@conn-timeout", 3000);
            readTimeout = XMLConfig.getInteger(node, "@so-timeout", 6000);

            if (XMLConfig.exists(node, "./headers")) {
                fillMap(XMLConfig.getNodeList(node, "./headers/header"), headers);

            }

            if (XMLConfig.exists(node, "./parameters")) {
                fillMap(XMLConfig.getNodeList(node, "./parameters/param"), params);
            }

            parts = XMLConfig.getNodeList(node, "./parts/*");

        } catch (Exception exc) {
            throw new InitializationException("GV_INIT_SERVICE_ERROR", new String[][] { { "message", exc.getMessage() } }, exc);
        }

    }

    /**
     * Adds on a Map the key and the value the given NodeList
     * 
     * @param sourceNodeList
     * @param destinationMap
     */
    private void fillMap(NodeList sourceNodeList, Map<String, String> destinationMap) {

        if (sourceNodeList.getLength() == 0) {
            destinationMap.clear();
        } else {
            IntStream.range(0, sourceNodeList.getLength()).mapToObj(sourceNodeList::item).forEach(node -> {
                try {
                    destinationMap.put(XMLConfig.get(node, "@name"), XMLConfig.get(node, "@value"));
                } catch (Exception e) {
                    logger.error("Fail to read configuration", e);
                }
            });
        }
    }

    /**
     * 
     * @param gvBuffer
     * for transport data in GreenVulcano
     * @return the GVBuffer
     * 
     * @see it.greenvulcano.gvesb.virtual.CallOperation#perform(it.greenvulcano.gvesb.buffer.GVBuffer)
     */
    @Override
    public GVBuffer perform(GVBuffer gvBuffer) throws ConnectionException, CallException, InvalidDataException {

        try {
            String actualURL = PropertiesHandler.expand(url, gvBuffer);

            StringBuffer callDump = new StringBuffer();
            callDump.append("Performing RestCallOperation " + name).append("\n        ").append("URL: ").append(actualURL);

            MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();

            for (int i = 0; i < parts.getLength(); i++) {

                Node partNode = parts.item(i);
                String name = PropertiesHandler.expand(XMLConfig.get(partNode, "@name"), gvBuffer);

                
                FormBodyPartBuilder partBuilder = FormBodyPartBuilder.create();
                partBuilder.setName(name);
                               
                if (XMLConfig.exists(partNode, "./headers")) {
                    Map<String, String> partHeaders = new LinkedHashMap<>();
                    
                    fillMap(XMLConfig.getNodeList(partNode, "./headers/header"), partHeaders);
                    
                    for (Entry<String, String> header : partHeaders.entrySet()) {
                        String key = PropertiesHandler.expand(header.getKey(), gvBuffer);
                        String value = PropertiesHandler.expand(header.getValue(), gvBuffer);
                        
                        partBuilder.setField(key, value);
                        
                    }

                }
                
                switch (partNode.getNodeName()) {

                    case "filePart": {
    
                        String filePath = PropertiesHandler.expand(XMLConfig.get(partNode, "@filepath"), gvBuffer);
                        String fileName = PropertiesHandler.expand(XMLConfig.get(partNode, "@filename"), gvBuffer);
                        String fileContentType = PropertiesHandler.expand(XMLConfig.get(partNode, "@contenttype"), gvBuffer);
    
                        FileBody filePart = new FileBody(new File(filePath), ContentType.parse(fileContentType), fileName);                        
                        partBuilder.setBody(filePart);
                        
                        break;
                    }
    
                    case "stringPart": {
                        String stringContentType = PropertiesHandler.expand(XMLConfig.get(partNode, "@contenttype"), gvBuffer);
                        String stringContent = PropertiesHandler.expand(partNode.getTextContent(), gvBuffer);
    
                        StringBody stringPart = new StringBody(stringContent, ContentType.parse(stringContentType));                        
                        partBuilder.setBody(stringPart);
                        
                        break;
                    }
    
                    case "formPart": {
    
                        NodeList formParams = XMLConfig.getNodeList(partNode, "./param");
                        Set<String> params = new LinkedHashSet<>();
                        for (int n = 0; n < formParams.getLength(); n++) {
                            Node paramNode = formParams.item(n);
                            String key = PropertiesHandler.expand(XMLConfig.get(paramNode, "@name"), gvBuffer);
                            String value = PropertiesHandler.expand(XMLConfig.get(paramNode, "@value"), gvBuffer);
    
                            params.add(URLEncoder.encode(key, "UTF-8").concat("=").concat(URLEncoder.encode(value, "UTF-8")));
                        }
    
                        StringBody formPart = new StringBody(params.stream().collect(Collectors.joining("&")), ContentType.APPLICATION_FORM_URLENCODED);                        
                        partBuilder.setBody(formPart);
                        
                        break;
                    }
    
                    case "byteArrayPart": {
                        String binaryPartfileName = PropertiesHandler.expand(XMLConfig.get(partNode, "@filename"), gvBuffer);
                        String binaryPartContentType = PropertiesHandler.expand(XMLConfig.get(partNode, "@contenttype"), gvBuffer);
    
                        byte[] requestData;
                        if (gvBuffer.getObject() instanceof byte[]) {
                            requestData = (byte[]) gvBuffer.getObject();
                        } else {
                            requestData = gvBuffer.getObject().toString().getBytes();
                        }
    
                        ByteArrayBody byteArrayPart = new ByteArrayBody(requestData, ContentType.parse(binaryPartContentType), binaryPartfileName);
                        partBuilder.setBody(byteArrayPart);
    
                        break;
    
                    }

                }
                
                
                multipartEntityBuilder.addPart(partBuilder.build());

            }

            HttpPost httpPost = new HttpPost(actualURL);

            callDump.append("\n        ").append("Method: POST");

            callDump.append("\n        ").append("Connection timeout: " + connectionTimeout);
            callDump.append("\n        ").append("Read timeout: " + readTimeout);

            RequestConfig config = RequestConfig.custom().setConnectTimeout(connectionTimeout).setSocketTimeout(readTimeout).build();

            for (Entry<String, String> header : headers.entrySet()) {
                String key = PropertiesHandler.expand(header.getKey(), gvBuffer);
                String value = PropertiesHandler.expand(header.getValue(), gvBuffer);
                httpPost.setHeader(key, value);
                callDump.append("\n        ").append("Header: " + key + "=" + value);
            }

            HttpEntity httpEntity = multipartEntityBuilder.build();
            httpPost.setEntity(httpEntity);

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(config).build();
                    CloseableHttpResponse response = httpClient.execute(httpPost)) {

                gvBuffer.setObject(EntityUtils.toByteArray(response.getEntity()));

                for (Header header : response.getAllHeaders()) {
                    gvBuffer.setProperty(RESPONSE_HEADER_PREFIX.concat(header.getName().toUpperCase()), header.getValue());
                }

                gvBuffer.setProperty(RESPONSE_STATUS, String.valueOf(response.getStatusLine()));
                gvBuffer.setProperty(RESPONSE_MESSAGE, String.valueOf(response));

                callDump.append("\n " + gvBuffer);

                response.close();
            }

            logger.debug(callDump.toString());
        } catch (Exception exc) {
            throw new CallException("GV_CALL_SERVICE_ERROR",
                                    new String[][] { { "service", gvBuffer.getService() },
                                                     { "system", gvBuffer.getSystem() },
                                                     { "tid", gvBuffer.getId().toString() },
                                                     { "message", exc.getMessage() } },
                                    exc);
        }
        return gvBuffer;
    }

    @Override
    public void cleanUp() {

        // do nothing
    }

    @Override
    public void destroy() {

        // do nothing
    }

    @Override
    public String getServiceAlias(GVBuffer gvBuffer) {

        return gvBuffer.getService();
    }

    @Override
    public void setKey(OperationKey key) {

        this.key = key;
    }

    @Override
    public OperationKey getKey() {

        return key;
    }
}