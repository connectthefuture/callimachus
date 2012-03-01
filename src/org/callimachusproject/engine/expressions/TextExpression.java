package org.callimachusproject.engine.expressions;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.xml.stream.Location;

import org.callimachusproject.engine.events.RDFEvent;
import org.callimachusproject.engine.model.Node;
import org.openrdf.model.Value;

public class TextExpression implements Expression {
	private String text;

	public TextExpression(String text) {
		this.text = text;
	}

	public String getTemplate() {
		return text;
	}

	public String bind(Map<String,Value> variables) {
		return text;
	}

	public List<RDFEvent> pattern(Node subject, Location location) {
		return Collections.emptyList();
	}
}
