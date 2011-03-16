/*
 * Portions Copyright (c) 2009-10 Zepheira LLC, Some Rights Reserved
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
package org.callimachusproject;

import static java.lang.Integer.toHexString;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.activation.MimeTypeParseException;
import javax.activation.MimetypesFileTypeMap;

import org.apache.commons.codec.binary.Base64;
import org.callimachusproject.webapps.BootListener;
import org.callimachusproject.webapps.ConciseListener;
import org.callimachusproject.webapps.ObjectDatesetListener;
import org.callimachusproject.webapps.Uploader;
import org.openrdf.http.object.ConnectionBean;
import org.openrdf.http.object.HTTPObjectAgentMXBean;
import org.openrdf.http.object.HTTPObjectServer;
import org.openrdf.http.object.client.HTTPObjectClient;
import org.openrdf.http.object.util.FileUtil;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.config.RepositoryConfigException;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.repository.object.ObjectRepository;
import org.openrdf.repository.object.config.ObjectRepositoryConfig;
import org.openrdf.repository.object.config.ObjectRepositoryFactory;
import org.openrdf.rio.RDFFormat;
import org.openrdf.sail.auditing.vocabulary.Audit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CallimachusServer implements HTTPObjectAgentMXBean {
	private static final String ENVELOPE_TYPE = "message/x-response";
	private static final String IDENTITY_PATH = "/diverted;";
	private static final String ERROR_XSLT_PATH = "/layout/error.xsl";
	Logger logger = LoggerFactory.getLogger(CallimachusServer.class);
	private Uploader uploader;
	private String authority;
	private ObjectRepository repository;
	private HTTPObjectServer server;
	private boolean conditional = true;
	private BootListener boot;

	public CallimachusServer(Repository repository, File webapps, File dataDir)
			throws Exception {
		File webappsDir = webapps.getCanonicalFile();
		this.repository = importJars(webappsDir, repository);
		// compile schema not in an explicit named graph
		this.repository.addSchemaGraphType(Audit.TRANSACTION);
		ObjectConnection con = this.repository.getConnection();
		try {
			ClassLoader cl = con.getObjectFactory().getClassLoader();
			try {
				Thread.currentThread().setContextClassLoader(cl);
			} catch (SecurityException e) {
				logger.warn(e.toString(), e);
			}
			MimetypesFileTypeMap mimetypes = createMimetypesMap();
			uploader = new Uploader(mimetypes, dataDir);
			uploader.setWebappsDir(webappsDir);
			// compile schema in webapps named graphs
			uploader.addListener(new ObjectDatesetListener(this.repository));
		} finally {
			con.close();
		}
		String basic = "boot:" + generatePassword();
		byte[] decoded = basic.getBytes();
		String cred = new String(Base64.encodeBase64(decoded), "8859_1");
		uploader.setAuthorization("Basic " + cred);
		uploader.addListener(boot = new BootListener());
		boot.setAuthorization("Basic " + cred);
		server = createServer(dataDir, basic, this.repository);
	}

	public void printStatus(PrintStream out) {
		uploader.addListener(new ConciseListener(out));
	}

	public String getAuthority() {
		return authority;
	}

	public void setAuthority(String authority) {
		this.authority = authority;
		String prefix = "http://" + authority + IDENTITY_PATH;
		server.setIdentityPrefix(new String[] { prefix });
		server.setErrorXSLT("http://" + authority + ERROR_XSLT_PATH);
		uploader.setProxy(getAuthorityAddress());
		boot.setProxy(getAuthorityAddress());
	}

	public String getServerName() {
		return server.getName();
	}

	public void setServerName(String serverName) {
		server.setName(serverName);
	}

	public File getWebappsDir() {
		return uploader.getWebappsDir();
	}

	public ObjectRepository getRepository() {
		return repository;
	}

	public boolean isConditionalRequests() {
		return conditional;
	}

	public void setConditionalRequests(boolean conditional) {
		this.conditional = conditional;
	}

	public int getCacheSize() {
		return server.getCacheSize();
	}

	public int getCacheCapacity() {
		return server.getCacheCapacity();
	}

	public void setCacheCapacity(int capacity) {
		server.setCacheCapacity(capacity);
	}

	public String getFrom() {
		return server.getFrom();
	}

	public void setFrom(String from) {
		server.setFrom(from);
	}

	public String getName() {
		return server.getName();
	}

	public void setName(String serverName) {
		server.setName(serverName);
	}

	public boolean isCacheAggressive() {
		return server.isCacheAggressive();
	}

	public void setCacheAggressive(boolean cacheAggressive) {
		server.setCacheAggressive(cacheAggressive);
	}

	public boolean isCacheDisconnected() {
		return server.isCacheDisconnected();
	}

	public void setCacheDisconnected(boolean cacheDisconnected) {
		server.setCacheDisconnected(cacheDisconnected);
	}

	public boolean isCacheEnabled() {
		return server.isCacheEnabled();
	}

	public void setCacheEnabled(boolean cacheEnabled) {
		server.setCacheEnabled(cacheEnabled);
	}

	public void invalidateCache() throws IOException, InterruptedException {
		server.invalidateCache();
	}

	public void resetCache() throws IOException, InterruptedException {
		server.resetCache();
	}

	public ConnectionBean[] getConnections() {
		return server.getConnections();
	}

	public void resetConnections() throws IOException {
		server.resetConnections();
	}

	public void poke() {
		server.poke();
	}

	public void listen(int... ports) throws Exception {
		assert ports != null && ports.length > 0;
		if (authority == null) {
			setAuthority(getAuthority(ports[0]));
		}
		uploader.setOrigin("http://" + authority + "/");
		server.listen(ports);
	}

	public void start() throws Exception {
		logger.info("Callimachus is binding to {}", uploader.getOrigin());
		InetSocketAddress host = getAuthorityAddress();
		HTTPObjectClient.getInstance().setProxy(host, server);
		boolean empty = false;
		if (!conditional || (empty = isEmpty(repository))) {
			repository.setCompileRepository(false);
		}
		uploader.uploadWebapps(conditional && !empty);
		repository.setCompileRepository(true);
		server.start();
		uploader.started();
		System.gc();
		long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
		logger.info("Callimachus started in {} seconds", uptime / 1000.0);
	}

	public String getStatus() {
		return server.getStatus();
	}

	public boolean isRunning() {
		return server.isRunning();
	}

	public void stop() throws Exception {
		uploader.stopping();
		InetSocketAddress host = getAuthorityAddress();
		HTTPObjectClient.getInstance().removeProxy(host, server);
		uploader.stop();
		server.stop();
	}

	public void destroy() throws Exception {
		server.destroy();
	}

	private InetSocketAddress getAuthorityAddress() {
		InetSocketAddress host;
		if (authority.contains(":")) {
			int idx = authority.indexOf(':');
			int port = Integer.parseInt(authority.substring(idx + 1));
			host = new InetSocketAddress(authority.substring(0, idx), port);
		} else {
			host = new InetSocketAddress(authority, 80);
		}
		return host;
	}

	private boolean isEmpty(ObjectRepository repository)
			throws RepositoryException {
		ObjectConnection con = repository.getConnection();
		try {
			return con.isEmpty();
		} finally {
			con.close();
		}
	}

	private String getAuthority(int port) throws IOException {
		String hostname;
		try {
			// attempt for the host canonical host name
			hostname = InetAddress.getLocalHost().getCanonicalHostName();
		} catch (UnknownHostException uhe) {
			try {
				// attempt to get the loop back address
				hostname = InetAddress.getByName(null).getCanonicalHostName();
			} catch (UnknownHostException uhe2) {
				// default to a standard loop back IP
				hostname = "127.0.0.1";
			}
		}
		if (port == 80)
			return hostname;
		return hostname + ":" + port;
	}

	private String generatePassword() {
		Random rand = new Random(System.nanoTime());
		return Long.toHexString(rand.nextLong()) + Long.toHexString(rand.nextLong());
	}

	private MimetypesFileTypeMap createMimetypesMap() {
		MimetypesFileTypeMap mimetypes = new MimetypesFileTypeMap();
		for (RDFFormat format : RDFFormat.values()) {
			boolean extensionPresent = false;
			StringBuilder sb = new StringBuilder();
			sb.append(format.getDefaultMIMEType());
			for (String ext : format.getFileExtensions()) {
				if ("application/octet-stream".equals(mimetypes
						.getContentType("file." + ext))) {
					extensionPresent = true;
					sb.append(" ").append(ext);
				}
			}
			if (extensionPresent) {
				mimetypes.addMimeTypes(sb.toString());
			}
		}
		return mimetypes;
	}

	private HTTPObjectServer createServer(File dir, String basic,
			ObjectRepository or) throws IOException, MimeTypeParseException {
		File wwwDir = new File(dir, "www");
		File cacheDir = new File(dir, "cache");
		FileUtil.deleteOnExit(cacheDir);
		File out = new File(cacheDir, "server");
		HTTPObjectServer server = new HTTPObjectServer(or, wwwDir, out, basic);
		server.setEnvelopeType(ENVELOPE_TYPE);
		HTTPObjectClient.getInstance().setEnvelopeType(ENVELOPE_TYPE);
		return server;
	}

	private ObjectRepository importJars(File webappsDir, Repository repository)
			throws URISyntaxException, RepositoryConfigException,
			RepositoryException, IOException {
		File dataDir = repository.getDataDir();
		File lib;
		File ontologies;
		if (dataDir == null) {
			lib = File.createTempFile("lib", "");
			ontologies = File.createTempFile("ontologies", "");
			lib.delete();
			ontologies.delete();
		} else {
			lib = new File(dataDir, "lib");
			ontologies = new File(dataDir, "ontologies");
		}
		lib.mkdirs();
		ontologies.mkdirs();
		FileUtil.deleteOnExit(ontologies);
		List<URL> jars = findJars(webappsDir, false, false, false, lib,
				ontologies, new ArrayList<URL>());
		ObjectRepository or;
		if (jars.isEmpty() && repository instanceof ObjectRepository) {
			or = (ObjectRepository) repository;
		} else {
			ObjectRepositoryFactory factory = new ObjectRepositoryFactory();
			ObjectRepositoryConfig config = factory.getConfig();
			if (!jars.isEmpty()) {
				for (URL jar : jars) {
					if (jar.toExternalForm().endsWith(".jar")
							|| jar.toExternalForm().endsWith(".JAR")) {
						config.addConceptJar(jar);
					} else {
						config.addImports(jar);
					}
				}
			}
			or = factory.createRepository(config, repository);
		}
		return or;
	}

	private List<URL> findJars(File dir, boolean web, boolean inlib, boolean ont,
			File lib, File ontologies, ArrayList<URL> jars) throws IOException {
		if (Uploader.isHidden(dir))
			return jars;
		String name = dir.getName();
		if (dir.isDirectory()) {
			for (File file : dir.listFiles()) {
				boolean webinf = name.equalsIgnoreCase("WEB-INF");
				boolean eqlib = name.equalsIgnoreCase("lib");
				boolean ent = name.equalsIgnoreCase("ontologies");
				findJars(file, webinf, inlib || web && eqlib, ont || web
						&& ent, lib, ontologies, jars);
			}
			if (web && name.equalsIgnoreCase("classes")) {
				jars.add(asJar(dir, lib).toURI().toURL());
			}
		} else if (inlib && (name.endsWith(".jar") || name.endsWith(".JAR"))) {
			jars.add(asJar(dir, lib).toURI().toURL());
		} else if (ont) {
			int c = dir.getAbsolutePath().hashCode();
			String hex = toHexString(Math.abs(c));
			File dest = new File(ontologies, hex + dir.getName());
			jars.add(copy(dir, dest).toURI().toURL());
		} else if (name.endsWith(".war") || name.endsWith(".WAR")) {
			String cpath = dir.getAbsolutePath() + "!/WEB-INF/classes/";
			String code = toHexString(cpath.hashCode());
			File classesFile = new File(lib, dir.getName() + code + ".jar");
			JarOutputStream classes = null;
			ZipFile zip = new ZipFile(dir);
			try {
				Enumeration<? extends ZipEntry> entries = zip.entries();
				while (entries.hasMoreElements()) {
					ZipEntry entry = entries.nextElement();
					if (Uploader.isHidden(entry.getName()))
						continue;
					String path = entry.getName().replace('\\', '/');
					if (path.endsWith("/"))
						continue;
					while (path.startsWith("/")) {
						path = path.substring(1);
					}
					if (path.length() < 0)
						continue;
					if (path.startsWith("WEB-INF/lib/")) {
						String fullpath = dir.getAbsolutePath() + "!/" + path;
						jars.add(asJar(zip, entry, dir, fullpath, lib).toURI()
								.toURL());
					} else if (path.startsWith("WEB-INF/classes/")) {
						if (classes == null) {
							classes = new JarOutputStream(new FileOutputStream(
									classesFile));
						}
						String subentry = path.substring("WEB-INF/classes/"
								.length());
						classes.putNextEntry(new JarEntry(subentry));
						InputStream in = zip.getInputStream(entry);
						try {
							int read;
							byte[] buf = new byte[1024];
							while ((read = in.read(buf)) >= 0) {
								classes.write(buf, 0, read);
							}
							classes.closeEntry();
						} finally {
							in.close();
						}
					} else if (path.startsWith("WEB-INF/ontologies/")) {
						String en = path;
						if (en.contains("/")) {
							en = en.substring(en.lastIndexOf('/') + 1);
						}
						String fullpath = dir.getAbsolutePath() + "!/" + path;
						String hex = toHexString(Math.abs(fullpath.hashCode()));
						File dest = new File(ontologies, hex + en);
						jars.add(copy(zip, entry, dest).toURI().toURL());
					}
				}
			} finally {
				zip.close();
			}
			if (classes != null) {
				classes.close();
				jars.add(classesFile.toURI().toURL());
			}
		}
		return jars;
	}

	private File asJar(ZipFile zip, ZipEntry entry, File dir, String fullpath,
			File dest) throws IOException {
		String code = toHexString(fullpath.hashCode());
		File jar = new File(dest, dir.getName() + code + ".jar");
		return copy(zip, entry, jar);
	}

	private File copy(ZipFile zip, ZipEntry entry, File dest)
			throws IOException, FileNotFoundException {
		InputStream stream = zip.getInputStream(entry);
		ReadableByteChannel in = Channels.newChannel(stream);
		try {
			FileChannel out = new FileOutputStream(dest).getChannel();
			try {
				out.transferFrom(in, 0, entry.getSize());
			} finally {
				out.close();
			}
		} finally {
			in.close();
		}
		return dest;
	}

	private File asJar(File jar, File dir) throws IOException {
		String code = toHexString(jar.getAbsolutePath().hashCode());
		File dest = new File(dir, jar.getName() + code + ".jar");
		if (jar.isDirectory()) {
			dest = zip(jar, dest);
		} else {
			dest = copy(jar, dest);
		}
		return dest;
	}

	private File copy(File jar, File file) throws IOException {
		FileChannel in = new FileInputStream(jar).getChannel();
		try {
			FileChannel out = new FileOutputStream(file).getChannel();
			try {
				in.transferTo(0, jar.length(), out);
			} finally {
				out.close();
			}
		} finally {
			in.close();
		}
		return file;
	}

	private File zip(File dir, File zip) throws IOException {
		JarOutputStream zout = new JarOutputStream(new FileOutputStream(zip));
		try {
			addDirectory("", dir, zout);
			return zip;
		} finally {
			zout.close();
		}
	}

	private static void addDirectory(String prefix, File fileSource,
			JarOutputStream zout) throws IOException {
		File[] files = fileSource.listFiles();
		for (int i = 0; i < files.length; i++) {
			String entry = files[i].getName();
			if (prefix.length() > 0) {
				entry = prefix + "/" + entry;
			}
			if (files[i].isDirectory()) {
				addDirectory(entry, files[i], zout);
			} else {
				byte[] buffer = new byte[1024];
				FileInputStream fin = new FileInputStream(files[i]);
				try {
					zout.putNextEntry(new ZipEntry(entry));
					int length;
					while ((length = fin.read(buffer)) > 0) {
						zout.write(buffer, 0, length);
					}
					zout.closeEntry();
				} finally {
					fin.close();
				}
			}
		}
	}

}
