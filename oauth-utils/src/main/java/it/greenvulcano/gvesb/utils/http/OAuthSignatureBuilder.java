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
package it.greenvulcano.gvesb.utils.http;

import java.net.URLEncoder;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class OAuthSignatureBuilder {
	
	public static OAuthSignature build(String method, String baseURL){
		OAuthSignature signature = new OAuthSignature(method, baseURL);	
				
		UUID uuid = UUID.randomUUID();	
		
		signature.getParams().put("oauth_version", "1.0");
		signature.getParams().put("oauth_signature_method", "HMAC-SHA1");
		signature.getParams().put("oauth_nonce", uuid.toString());		
		signature.getParams().put("oauth_timestamp", Integer.valueOf(Math.round(System.currentTimeMillis()/1000)).toString());
				
		return signature;
	}
	
	public static OAuthSignature build(String method, String baseURL, String consumerSecret, String tokenSecret){
					
		return build(method, baseURL)
				.setConsumerSecret(consumerSecret)
				.setTokenSecret(tokenSecret);
	}	
	
	public static class OAuthSignature {
		
		private final Map<String, String> params;
		private final String method, baseURL;
		
		private String  consumerSecret, tokenSecret; 
				
		private OAuthSignature(String method, String baseURL) {
			this.method = method;
			this.baseURL = baseURL;
			this.params = new LinkedHashMap<>();

		}
		
		Map<String, String> getParams() {
			return params;
		}
		
		public OAuthSignature addParam(String key, String value) {
			params.put(key, value);
			return this;
		}
		
		public String getParam(String key) {
			return params.get(key);
		}
		
		public OAuthSignature setConsumerSecret(String consumerSecret) {
			this.consumerSecret = consumerSecret;
			return this;
		}
		
		public OAuthSignature setTokenSecret(String tokenSecret) {
			this.tokenSecret = tokenSecret;
			return this;
		}
		
		public String getSignature() {
			try {
				
				
				String parameterString = params.entrySet().stream()
						.map(e-> { 
							try {	
								return URLEncoder.encode(e.getKey(), "UTF-8").concat("=").concat(URLEncoder.encode(e.getValue(), "UTF-8")).replace("+", "%20");
							} catch (Exception ex) {
								return "";
							} 
						})
						.sorted()
						.collect(java.util.stream.Collectors.joining("&"));
		
				String signatureBase = method.toUpperCase() + "&" +
						URLEncoder.encode(baseURL, "UTF-8") + "&" + URLEncoder.encode(parameterString, "UTF-8");						
		    
				Mac mac = Mac.getInstance("HmacSHA1");
		        String signatureKey = URLEncoder.encode(consumerSecret, "UTF-8") + "&" + URLEncoder.encode(tokenSecret, "UTF-8");
		        SecretKeySpec spec = new SecretKeySpec(signatureKey.getBytes(),"HmacSHA1");
		
			    mac.init(spec);
			    byte[] byteHMAC = mac.doFinal(signatureBase.getBytes());
		
			    return URLEncoder.encode(Base64.getEncoder().encodeToString(byteHMAC), "UTF-8").replace("+", "%20");
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			return null;
		}	
	
		public String getAuthorization() {
			return 	"OAuth "+
				    "oauth_consumer_key=" + "\"" + getParam("oauth_consumer_key") + "\"," + 
				    "oauth_nonce=" + "\"" + getParam("oauth_nonce")+ "\"," + 
				    "oauth_signature=" + "\"" + getSignature() + "\"," + 
				    "oauth_signature_method=" + "\"" + getParam("oauth_signature_method") + "\"," +  
				    "oauth_timestamp=" + "\"" + getParam("oauth_timestamp") + "\"," + 
				    "oauth_token=" + "\"" + getParam("oauth_token") + "\"," + 
				    "oauth_version=" + "\"" + getParam("oauth_version")+"\"";
		}

	}

}
