// data-add.js
/*
   Portions Copyright (c) 2009-10 Zepheira LLC, Some Rights Reserved
   Portions Copyright (c) 2010-11 Talis Inc, Some Rights Reserved
   Licensed under the Apache License, Version 2.0, http://www.apache.org/licenses/LICENSE-2.0
*/

(function($){

$(document).ready(function () {
	var form = $("form[about]");
	initSetElements(form);
});

$(document).bind("DOMNodeInserted", function (event) {
	initSetElements(event.target);
});

function select(node, selector) {
	return $(node).find(selector).andSelf().filter(selector);
}

var iframe_counter = 0;

function initSetElements(form) {
	createAddRelButtons(form);
	var tbody = select(form, "[data-add]");
	tbody.children("[about]").each(initSetElement);
	tbody.each(function() {
		var list = $(this);
		var dragover = function(event) {
			if (!list.hasClass("drag-over")) {
				list.addClass("drag-over");
			}
			return stopPropagation(event);
		}
		var dragleave = function(event) {
			list.removeClass("drag-over");
			return stopPropagation(event);
		}
		if (this.addEventListener) {
			this.addEventListener("dragenter", dragover, false);
			this.addEventListener("dragover", dragover, false);
			this.addEventListener("dragleave", dragleave, false);
			this.addEventListener("drop", pasteURL(dragleave), false);
		} else if (this.attachEvent) {
			this.attachEvent("ondragenter", dragover);
			this.attachEvent("ondragover", dragover);
			this.attachEvent("ondragleave", dragleave);
			this.attachEvent("ondrop", pasteURL(dragleave));
		}
		list.prepend("<div style='font-style:italic;font-size:small'>Drag resource(s) here</div>");
		var url = list.attr("data-search");
		if (url && list.attr("data-prompt")) {
			var count = list.attr("id") ? list.attr("id") : (++iframe_counter);
			var id = "lookup-" + count + "-button";
			var add = $('<button type="button"/>');
			add.attr("class", "add");
			add.attr("id", id);
			add.text("»");
			var suggest = list.attr("data-prompt");
			list.append(add);
			var name = id.replace(/-button/, "-iframe");
			var searchTerms = id.replace(/-button/, "-input");
			var form = $("<form>"
				+ "<div class='lookup'>"
				+ "<input name='q' title='Lookup..' id='" + searchTerms + "' /></div>"
				+ "</form>");
			list.attr("data-dialog", name);
			var title = '';
			if (list.attr("id")) {
				title = $("label[for='" + list.attr("id") + "']").text();
			}
			var iframe = $("<iframe id='" + name + "' name='" + name + "' frameborder='0' src='about:blank' data-button='" + id + "'></iframe>");
		    add.click(function(e) {
			    var dialog = iframe.dialog({
			        title: title,
			        autoOpen: false,
			        modal: false,
			        draggable: true,
			        resizable: true,
			        autoResize: true,
					width: 320,
					height: 480
			    });
				iframe.bind("dialogclose", function(event, ui) {
					dialog.dialog("destroy");
					add.focus();
				});
				dialog.dialog("open");
				var dialogTitle = iframe.parents(".ui-dialog").find(".ui-dialog-title");
				var bottom = dialogTitle.height() + dialogTitle.offset().top - iframe.parent().offset().top;
				var left = dialogTitle.width() + dialogTitle.offset().left - iframe.parent().offset().left;
				form.css('position', "absolute");
				form.css('top', bottom - dialogTitle.height());
				form.css('left', left + 10);
				iframe.before(form);
				form.submit(function(event) {
					var searchUrl = url.replace('{searchTerms}', encodeURIComponent(document.getElementById(searchTerms).value));
					listSearchResults(searchUrl, iframe.get(0));
					return stopPropagation(event);
				});
				form.css('top', Math.max(bottom - dialogTitle.height() /2 - form.height()/2, bottom - form.height()));
				var maxLeft = iframe.parent().width() - form.width() - 30; // close button = 19px
				form.css('left', Math.min(maxLeft, left + 10));
				iframe.get(0).src = suggest;
		    });
		}
	});
}

function createAddRelButtons(form) {
	select(form, "[data-more][rel]").each(function() {
		var parent = $(this);
		var property = parent.attr("rel");
		var editable = parent.is("*[contenteditable]") || parent.parents("*[contenteditable]").size();
		var minOccurs = parent.attr("data-min-cardinality");
		var maxOccurs = parent.attr("data-max-cardinality");
		minOccurs = minOccurs ? minOccurs : parent.attr("data-cardinality");
		maxOccurs = maxOccurs ? maxOccurs : parent.attr("data-cardinality");
		var count = parent.children().size();
		var add = false;
		if (!editable && (!maxOccurs || !minOccurs || parseInt(minOccurs) != parseInt(maxOccurs))) {
			parent.children().each(initSetElement);
			add = $('<button type="button"/>');
			add.addClass("add");
			add.text("»");
			add.click(function(){
				jQuery.get(parent.attr("data-more"), function(data) {
					var input = $(data);
					add.before(input);
					input.each(initSetElement);
					updateButtonState(parent);
				});
			});
			if (this.tagName.toLowerCase() == "table") {
				var cell = $("<td/>");
				cell.append(add);
				var row = $("<tr/>");
				row.append(cell);
				var tbody = $("<tbody/>");
				tbody.append(row);
				parent.append(tbody);
			} else if (this.tagName.toLowerCase() == "tbody") {
				var cell = $("<td/>");
				cell.append(add);
				var row = $("<tr/>");
				row.append(cell);
				parent.append(row);
			} else if (this.tagName.toLowerCase() == "tr") {
				var cell = $("<td/>");
				cell.append(add);
				parent.append(cell);
			} else {
				parent.append(add);
			}
		}
		if (minOccurs) {
			var n = parseInt(minOccurs) - count;
			for (var i=0; i<n; i++) {
				jQuery.get(parent.attr("data-more"), function(data) {
					var input = $(data);
					if (add) {
						add.before(input);
					} else {
						parent.append(input);
					}
					input.each(initSetElement);
					updateButtonState(parent);
				});
			}
		}
		updateButtonState(parent);
	});
}

function updateButtonState(context) {
	var minOccurs = context.attr("data-min-cardinality");
	var maxOccurs = context.attr("data-max-cardinality");
	minOccurs = minOccurs ? minOccurs : context.attr("data-cardinality");
	maxOccurs = maxOccurs ? maxOccurs : context.attr("data-cardinality");
	var add = context.children("button[class='add']");
	var buttons;
	if (context.attr("rel")) {
		buttons = context.children().children("button[class='remove']");
	} else {
		buttons = context.children("button[class='remove']");
	}
	if (buttons.size() <= minOccurs) {
		buttons.hide();
	} else {
		buttons.show();
	}
	if (buttons.size() >= maxOccurs) {
		add.hide();
	} else {
		add.show();
	}
}

function listSearchResults(url, iframe) {
	jQuery.get(url, function(data) {
		if (data) {
			var result = $(data).children();
			var ul = $("<ul/>");
			result.each(function() {
				var about = $(this).attr("about");
				if (about && about.indexOf('[') < 0) {
					var li = $("<li/>");
					var link = $('<a data-query="view"/>');
					link.attr("href", about);
					link.append($(this).text());
					li.append(link);
					ul.append(li);
				}
			});
			var html = ul.html();
			if (html) {
				iframe.src = "about:blank";
	            var doc = iframe.contentWindow.document;
				doc.open();
				doc.write("<ul>" + html + "</ul>");
				doc.close();
			}
		}
	});
};

if (!window.calli) {
	window.calli = {};
}

window.calli.resourceCreated = function(uri, iframe) {
	setTimeout(function() {
		var list = $('#' + $(iframe).attr("data-button")).parent()
		if (list.attr("data-add")) {
			addSetItem(uri, list);
		} else if (list.attr("data-member")) {
			addListItem(uri, list);
		}
	}, 0)
};

function stopPropagation(event) {
	if (event.preventDefault) {
		event.preventDefault();
	}
	return false
}

function pasteURL(callback) {
	return function(event) {
		var target = event.target ? event.target : event.srcElement;
		if (event.preventDefault) {
			event.preventDefault();
		}
		var data = event.originalEvent ? event.originalEvent.dataTransfer : event.dataTransfer;
		if (!data) {
			data = event.clipboardData;
		}
		if (!data) {
			data = window.clipboardData;
		}
		var uris = window.calli.listResourceIRIs(data.getData('URL'));
		if (!uris.size()) {
			uris = window.calli.listResourceIRIs(data.getData("Text"));
		}
		var selector = "[data-add]";
		var script = $(target);
		if (!script.children(selector).size()) {
			$(target).parents().each(function() {
				if ($(this).is(selector)) {
					script = $(this);
				}
			})
		}
		uris.each(function() {
			addSetItem(this, script);
		})
		return callback(event);
	}
}

function addSetItem(uri, script) {
	var url = script.attr("data-add").replace("{about}", encodeURIComponent(uri));
	jQuery.get(url, function(data) {
		var input = data ? $(data) : data;
		if (input && input.is("[about='" + uri + "']") && input.text().match(/\w/)) {
			if (script.children("button.add").size()) {
				script.children("button.add").before(input);
			} else {
				script.append(input);
			}
			input.each(initSetElement);
			if (script.attr("data-dialog")) {
				$('#' + script.attr("data-dialog")).dialog('close');
			}
		} else {
			script.trigger("calliError", "Invalid Relationship");
		}
	});
}

function initSetElement() {
	var row = $(this);
	if (!row.parents("*[contenteditable]").size()) {
		var remove = $('<button type="button"/>');
		remove.addClass("remove");
		remove.text("×");
		remove.click(function(){
			var list = row.parent();
			row.remove();
			remove.remove();
			updateButtonState(row);
		})
		if (this.tagName.toLowerCase() == "tr") {
			var cell = $("<td/>");
			cell.append(remove);
			row.append(cell);
		} else {
			row.append(remove);
		}
	}
}

})(jQuery);

