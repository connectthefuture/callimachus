/*
 * Portions Copyright (c) 2009-10 Zepheira LLC and James Leigh, Some Rights Reserved
 * Portions Copyright (c) 2010-11 Talis Inc, Some Rights Reserved
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
package org.callimachusproject.behaviours;

import info.aduna.net.ParsedURI;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.callimachusproject.concepts.Page;
import org.callimachusproject.traits.Realm;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.object.ObjectFactory;
import org.openrdf.repository.object.RDFObject;

/**
 * Includes common realm methods that read properties from the RDF store.
 * 
 * @author James Leigh
 * 
 */
public abstract class RealmSupport implements Realm, RDFObject {

	private static final ProtocolVersion HTTP11 = new ProtocolVersion("HTTP",
			1, 1);
	private static final BasicStatusLine _401 = new BasicStatusLine(HTTP11,
			401, "Unauthorized");
	private static final BasicStatusLine _403 = new BasicStatusLine(HTTP11,
			403, "Forbidden");
	private static Set<String> local;
	static {
		Set<InetAddress> addresses = getAllLocalAddresses();
		local = new HashSet<String>(addresses.size() * 3);
		for (InetAddress addr : addresses) {
			local.add(addr.getCanonicalHostName());
			local.add(addr.getHostAddress());
			local.add(addr.getHostName());
		}
	}

	private static Set<InetAddress> getAllLocalAddresses() {
		Set<InetAddress> result = new HashSet<InetAddress>();
		try {
			result.addAll(Arrays.asList(InetAddress.getAllByName(null)));
		} catch (UnknownHostException e) {
			// no loop back device
		}
		try {
			InetAddress local = InetAddress.getLocalHost();
			result.add(local);
			try {
				result.addAll(Arrays.asList(InetAddress.getAllByName(local
						.getCanonicalHostName())));
			} catch (UnknownHostException e) {
				// no canonical name
			}
		} catch (UnknownHostException e) {
			// no network
		}
		try {
			Enumeration<NetworkInterface> interfaces;
			interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces != null && interfaces.hasMoreElements()) {
				NetworkInterface iface = interfaces.nextElement();
				Enumeration<InetAddress> addrs = iface.getInetAddresses();
				while (addrs != null && addrs.hasMoreElements()) {
					result.add(addrs.nextElement());
				}
			}
		} catch (SocketException e) {
			// broken network configuration
		}
		return result;
	}

	@Override
	public String protectionDomain() {
		StringBuilder sb = new StringBuilder();
		for (Object domain : getCalliDomains()) {
			if (sb.length() > 0) {
				sb.append(" ");
			}
			sb.append(domain.toString());
		}
		if (sb.length() < 1)
			return null;
		return sb.toString();
	}

	@Override
	public String allowOrigin() {
		StringBuilder sb = new StringBuilder();
		for (Object origin : getCalliOrigins()) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			String str = origin.toString();
			if (str.equals("*"))
				return str;
			sb.append(str);
		}
		if (sb.length() < 1)
			return null;
		return sb.toString();
	}

	@Override
	public boolean withAgentCredentials(String origin) {
		for (Object script : getCalliOrigins()) {
			String ao = script.toString();
			// must be explicitly listed ('*' does not qualify)
			if (origin.startsWith(ao) || ao.startsWith(origin)
					&& ao.charAt(origin.length()) == '/')
				return true;
		}
		return false;
	}

	@Override
	public Object authenticateRequest(String method, Object resource,
			Map<String, String[]> request) throws RepositoryException {
		String[] vias = request.get("via");
		if (vias == null || vias.length < 1)
			return null;
		String host = findRemoteHost(vias);
		if (host == null)
			return null;
		ObjectFactory of = getObjectConnection().getObjectFactory();
		return of.createObject("dns:" + host);

	}

	@Override
	public HttpMessage authenticationInfo(String method, Object resource,
			Map<String, String[]> request) {
		return null;
	}

	@Override
	public HttpResponse unauthorized(String method, Object resource,
			Map<String, String[]> request) throws Exception {
		Page unauthorized = getCalliUnauthorized();
		if (unauthorized == null)
			return null;
		String url = request.get("request-target")[0];
		ParsedURI parsed = new ParsedURI(url);
		String query = parsed.getQuery();
		String html = unauthorized.calliConstructHTML(resource, query);
		StringEntity entity = new StringEntity(html, "UTF-8");
		entity.setContentType("text/html;charset=\"UTF-8\"");
		HttpResponse resp = new BasicHttpResponse(_401);
		resp.setHeader("Cache-Control", "no-store");
		resp.setEntity(entity);
		return resp;
	}

	@Override
	public HttpResponse forbidden(String method, Object resource,
			Map<String, String[]> request) throws Exception {
		Page forbidden = getCalliForbidden();
		if (forbidden == null)
			return null;
		String url = request.get("request-target")[0];
		ParsedURI parsed = new ParsedURI(url);
		String query = parsed.getQuery();
		String html = forbidden.calliConstructHTML(resource, query);
		StringEntity entity = new StringEntity(html, "UTF-8");
		entity.setContentType("text/html;charset=\"UTF-8\"");
		HttpResponse resp = new BasicHttpResponse(_403);
		resp.setHeader("Cache-Control", "no-store");
		resp.setEntity(entity);
		return resp;
	}

	/**
	 * 
	 * @param via
	 *            host with optional port number
	 * @return true if this host resolves to the local machine
	 */
	protected boolean isLocal(String via) {
		int idx = via.lastIndexOf(':');
		if (idx > 0 && local.contains(via.substring(0, idx)))
			return true;
		return local.contains(via);
	}

	/**
	 * Finds the nearest remote agent or (if no remote hosts) the farthest local
	 * agent.
	 * 
	 * @return the host and optional port number or a pseudonym
	 */
	private String findRemoteHost(String[] vias) {
		String via = null;
		for (int i = vias.length - 1; i >= 0; i--) {
			via = vias[i];
			int idx = via.lastIndexOf(' ');
			if (idx > 0 && idx < via.length() - 1) {
				via = via.substring(idx + 1);
				idx = via.lastIndexOf(' ');
				if (idx > 0 && idx < via.length()) {
					via = via.substring(0, idx);
				}
			}
			if (!isLocal(via))
				break;
		}
		assert via != null;
		return via;
	}

}
