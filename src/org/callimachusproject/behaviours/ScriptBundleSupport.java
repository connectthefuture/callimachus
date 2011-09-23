package org.callimachusproject.behaviours;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.message.BasicHttpRequest;
import org.callimachusproject.concepts.ScriptBundle;
import org.openrdf.http.object.client.HTTPObjectClient;
import org.openrdf.http.object.exceptions.GatewayTimeout;
import org.openrdf.http.object.exceptions.InternalServerError;
import org.openrdf.http.object.exceptions.ResponseException;
import org.openrdf.repository.object.annotations.sparql;

import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.JSSourceFile;
import com.google.javascript.jscomp.Result;

public abstract class ScriptBundleSupport implements ScriptBundle {

	@Override
	public String calliGetBundleSource() throws GatewayTimeout, IOException {
		List<JSSourceFile> scripts = new ArrayList<JSSourceFile>();
		for (Object ext : getCalliScriptsAsList()) {
			String url = ext.toString();
			String code = getJavaScriptCode(url);
			scripts.add(JSSourceFile.fromCode(url, code));
		}

		int minification = getMinification();
		if (minification < 1) {
			StringBuilder sb = new StringBuilder();
			for (JSSourceFile script : scripts) {
				sb.append(script.getCode()).append("\n");
			}
			return sb.toString();
		}

		Compiler compiler = new Compiler();
		CompilerOptions options = new CompilerOptions();
		getCompilationLevel(minification).setOptionsForCompilationLevel(options);

		List<JSSourceFile> externals = CommandLineRunner.getDefaultExterns();

		Result result = compiler.compile(externals, scripts, options);
		if (result.errors != null && result.errors.length > 0) {
			throw new InternalServerError(result.errors[0].toString());
		}
		return compiler.toSource();
	}

	@sparql("PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#>\n"
			+ "SELECT DISTINCT ?script\n"
			+ "WHERE { {$this ?one ?script FILTER (regex(str(?one), \"#_\\\\d$\"))}\n"
			+ "UNION {$this ?two ?script FILTER (regex(str(?two), \"#_\\\\d\\\\d$\"))}\n"
			+ "UNION {$this ?three ?script FILTER (regex(str(?three), \"#_\\\\d\\\\d\\\\d+$\"))}\n"
			+ "UNION {?member rdfs:member ?script FILTER (?member = $this)}\n"
			+ "} ORDER BY ?member ?three ?two ?one")
	protected abstract List<?> getCalliScriptsAsList();

	private int getMinification() {
		int result = Integer.MAX_VALUE;
		for (Number number : getCalliMinified()) {
			if (number.intValue() < result) {
				result = number.intValue();
			}
		}
		if (result == Integer.MAX_VALUE)
			return 2;
		return result;
	}

	private CompilationLevel getCompilationLevel(int minification) {
		if (minification == 1)
			return CompilationLevel.WHITESPACE_ONLY;
		if (minification == 2)
			return CompilationLevel.SIMPLE_OPTIMIZATIONS;
		return CompilationLevel.ADVANCED_OPTIMIZATIONS;
	}

	private String getJavaScriptCode(String url) throws IOException {
		HTTPObjectClient client = HTTPObjectClient.getInstance();
		Reader reader = openJavaScriptReader(url, 10, client);
		try {
			StringWriter writer = new StringWriter();
			int read;
			char[] cbuf = new char[1024];
			while ((read = reader.read(cbuf)) >= 0) {
				writer.write(cbuf, 0, read);
			}
			return writer.toString();
		} finally {
			reader.close();
		}
	}

	private Reader openJavaScriptReader(String url, int max,
			HTTPObjectClient client) throws IOException {
		BasicHttpRequest req = new BasicHttpRequest("GET", url);
		req.addHeader("Accept", "text/javascript;charset=UTF-8");
		HttpResponse resp = client.service(req);
		int code = resp.getStatusLine().getStatusCode();
		HttpEntity entity = resp.getEntity();
		if (code < 300) {
			return new InputStreamReader(entity.getContent(), "UTF-8");
		} else if (code < 400 && resp.containsHeader("Location") && max > 0) {
			entity.consumeContent();
			String location = resp.getHeaders("Location")[0].getValue();
			return openJavaScriptReader(location, max--, client);
		} else {
			throw ResponseException.create(resp);
		}
	}

}
