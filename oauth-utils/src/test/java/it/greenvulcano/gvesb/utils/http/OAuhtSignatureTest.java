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

import org.junit.Assert;
import org.junit.Test;


public class OAuhtSignatureTest {
	
	@Test
	public void testGenerate() {
		//from https://developer.twitter.com/en/docs/basics/authentication/guides/creating-a-signature.htm
		String signature = 	
				OAuthSignatureBuilder.build("POST", "https://api.twitter.com/1.1/statuses/update.json", "kAcSOqF21Fu85e7zjz7ZN2U4ZRhfV3WpwPAoE3Z7kBw", "LswwdoUaIvS8ltyTt5jkRh4J50vUPVVHtR2YPi5kE")
								.addParam("status", "Hello Ladies + Gentlemen, a signed OAuth request!")
								.addParam("include_entities", "true")
								.addParam("oauth_consumer_key", "xvz1evFS4wEEPTGEFPHBog")
								.addParam("oauth_nonce", "kYjzVBB8Y0ZFabxSWbWovY3uYSQ2pTgmZeNu2VS4cg")
								.addParam("oauth_signature_method", "HMAC-SHA1")
								.addParam("oauth_timestamp", "1318622958")
								.addParam("oauth_token", "370773112-GmHxMAgYyLbNEtIKZeRNFsMKPR9EyMZeS9weJAEb")
								.addParam("oauth_version", "1.0")
								.getSignature();
		
		Assert.assertEquals("hCtSmYh%2BiHYCEqBWrE7C7hYmtUk%3D", signature);
		
		
		signature = 	
				OAuthSignatureBuilder.build("POST", "https://api.twitter.com/1.1/statuses/update.json")
										.setConsumerSecret("kAcSOqF21Fu85e7zjz7ZN2U4ZRhfV3WpwPAoE3Z7kBw")
										.setTokenSecret("LswwdoUaIvS8ltyTt5jkRh4J50vUPVVHtR2YPi5kE")
										.addParam("status", "Hello Ladies + Gentlemen, a signed OAuth request!")
										.addParam("include_entities", "true")
										.addParam("oauth_consumer_key", "xvz1evFS4wEEPTGEFPHBog")
										.addParam("oauth_nonce", "kYjzVBB8Y0ZFabxSWbWovY3uYSQ2pTgmZeNu2VS4cg")
										.addParam("oauth_signature_method", "HMAC-SHA1")
										.addParam("oauth_timestamp", "1318622958")
										.addParam("oauth_token", "370773112-GmHxMAgYyLbNEtIKZeRNFsMKPR9EyMZeS9weJAEb")
										.addParam("oauth_version", "1.0")
										.getSignature();
				
		Assert.assertEquals("hCtSmYh%2BiHYCEqBWrE7C7hYmtUk%3D", signature);

		OAuthSignatureBuilder.OAuthSignature s = OAuthSignatureBuilder.build("POST", "https://api.twitter.com/1.1/statuses/update.json", "kAcSOqF21Fu85e7zjz7ZN2U4ZRhfV3WpwPAoE3Z7kBw", "LswwdoUaIvS8ltyTt5jkRh4J50vUPVVHtR2YPi5kE")
				.addParam("status", "Hello Ladies + Gentlemen, a signed OAuth request!")
				.addParam("include_entities", "true")
				.addParam("oauth_consumer_key", "xvz1evFS4wEEPTGEFPHBog")				
				.addParam("oauth_token", "370773112-GmHxMAgYyLbNEtIKZeRNFsMKPR9EyMZeS9weJAEb");
		
		Assert.assertNotNull(s.getParam("oauth_timestamp"));
		Assert.assertNotNull(s.getParam("oauth_nonce"));
		Assert.assertEquals("HMAC-SHA1", s.getParam("oauth_signature_method"));
		Assert.assertEquals("1.0", s.getParam("oauth_version"));
	}

}
