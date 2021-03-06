/*
 * Copyright (c) 2013 3 Round Stones Inc., Some Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.callimachusproject.server;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import org.callimachusproject.annotations.rel;
import org.callimachusproject.annotations.requires;
import org.callimachusproject.server.base.MetadataServerTestCase;
import org.callimachusproject.server.behaviours.PUTSupport;
import org.callimachusproject.server.concepts.HTTPFileObject;
import org.openrdf.annotations.Method;
import org.openrdf.annotations.Path;
import org.openrdf.annotations.Type;
import org.openrdf.model.Literal;
import org.openrdf.model.Model;
import org.openrdf.model.URI;
import org.openrdf.model.impl.LinkedHashModel;
import org.openrdf.model.impl.LiteralImpl;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.model.vocabulary.RDFS;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public class ContentNegotiationTest extends MetadataServerTestCase {

	public static class Alternate {
		@Method("GET")
		@rel("alternate")
		@Path("?boolean")
		@requires("urn:test:grant")
		@Type("application/sparql-results+xml")
		public boolean getBoolean() {
			return true;
		}

		@Method("GET")
		@rel("alternate")
		@Path("?rdf")
		@requires("urn:test:grant")
		@Type("application/rdf+xml")
		public Model getModel() {
			return new LinkedHashModel();
		}

		@Method("GET")
		@Path("?my")
		@requires("urn:test:grant")
		@Type({"application/rdf+xml", "text/turtle", "application/x-turtle"})
		public Model getMyModel() {
			LinkedHashModel model = new LinkedHashModel();
			URI uri = new URIImpl("urn:root");
			Literal lit = new LiteralImpl(new Date().toString());
			model.add(uri, uri, lit);
			return model;
		}

		@Method("PUT")
		@Path("?my")
		@requires("urn:test:grant")
		public void setMyModel(@Type("application/rdf+xml") Model model) {
		}

		@Method("GET")
		@Path("?my")
		@requires("urn:test:grant")
		@Type({"application/sparql-results+xml", "text/plain"})
		public boolean getMyBoolean() {
			return true;
		}

		@Method("PUT")
		@Path("?my")
		@requires("urn:test:grant")
		public void setMyBoolean(@Type("application/sparql-results+xml") boolean bool) {
		}

		@Method("POST")
		@Path("?my")
		@requires("urn:test:grant")
		public String postRDF(@Type("application/rdf+xml") InputStream in) {
			return "rdf+xml";
		}

		@Method("POST")
		@Path("?my")
		@requires("urn:test:grant")
		@Type("text/plain")
		public String postSPARQL(
				@Type("application/sparql-results+xml") InputStream in) {
			return "sparql-results+xml";
		}
	}

	public static abstract class RDFXMLFile implements HTTPFileObject {
		@Method("GET")
		@Path("$")
		@requires("urn:test:grant")
		@Type("application/rdf+xml")
		public InputStream getInputStream() throws IOException {
			return openInputStream();
		}
	}

	public void setUp() throws Exception {
		config.addBehaviour(RDFXMLFile.class, "urn:mimetype:application/rdf+xml");
		config.addBehaviour(Alternate.class, RDFS.RESOURCE);
		config.addBehaviour(PUTSupport.class);
		super.setUp();
	}

	public void testAlternate() throws Exception {
		WebResource web = client.path("/");
		web.accept("application/rdf+xml").get(Model.class);
		web.accept("application/sparql-results+xml").get(String.class);
	}

	public void testGetOperation() throws Exception {
		WebResource web = client.path("/").queryParam("my", "");
		web.accept("application/rdf+xml").get(Model.class);
		web.accept("application/sparql-results+xml").get(String.class);
	}

	public void testPutOperation() throws Exception {
		WebResource web = client.path("/").queryParam("my", "");
		web.type("application/rdf+xml").put(new LinkedHashModel());
		String str = web.accept("application/sparql-results+xml").get(String.class);
		web.type("application/sparql-results+xml").put(str);
	}

	public void testEntityTag() throws Exception {
		WebResource root = client.path("/");
		root.put("resource");
		WebResource web = root.queryParam("my", "");
		String rdf = web.accept("application/rdf+xml").get(ClientResponse.class).getEntityTag().toString();
		String ttl = web.accept("application/x-turtle").get(ClientResponse.class).getEntityTag().toString();
		assertFalse(rdf.equals(ttl));
		assertFalse(web.accept("application/rdf+xml").get(ClientResponse.class).getHeaders().getFirst("ETag").contains(","));
		assertFalse(web.accept("application/x-turtle").get(ClientResponse.class).getHeaders().getFirst("ETag").contains(","));
	}

	public void testPutEntityTag() throws Exception {
		WebResource web = client.path("/");
		ClientResponse resp = web.type("application/rdf+xml").put(ClientResponse.class, new LinkedHashModel());
		String put = resp.getEntityTag().toString();
		String head = web.accept("application/rdf+xml").head().getEntityTag().toString();
		assertEquals(put, head);
		assertFalse(web.accept("application/rdf+xml").head().getHeaders().getFirst("ETag").contains(","));
	}

	public void testIfNoneMatch() throws Exception {
		WebResource root = client.path("/");
		root.put("resource");
		WebResource web = root.queryParam("my", "");
		Model rdf = web.accept("application/rdf+xml").get(Model.class);
		Thread.sleep(1000);
		Model ttl = web.accept("application/rdf+xml,application/x-turtle;q=.2").get(Model.class);
		assertEquals(rdf, ttl);
	}

	public void testRequestBody() throws Exception {
		WebResource web = client.path("/").queryParam("my", "");
		assertEquals("rdf+xml", web.type("application/rdf+xml").post(String.class, "<RDF/>"));
		assertEquals("sparql-results+xml", web.type("application/sparql-results+xml").post(String.class, "<sparql/>"));
	}
}
