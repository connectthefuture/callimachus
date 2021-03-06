/*
 * Copyright (c) 2014 3 Round Stones Inc., Some Rights Reserved
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
package org.callimachusproject.webdriver.pages;

import org.callimachusproject.webdriver.helpers.WebBrowserDriver;
import org.junit.Assert;
import org.openqa.selenium.By;

public class FolderView extends CalliPage {

	public FolderView(WebBrowserDriver driver) {
		super(driver);
	}

	public DocEditor openArticleCreate() {
		browser.click(By.id("create-menu"));
		browser.click(By.linkText("Article"));
		return page(DocEditor.class);
	}

	public ConceptCreate openConceptCreate() {
		browser.click(By.id("create-menu"));
		browser.click(By.linkText("Concept"));
		return page(ConceptCreate.class);
	}

	public FolderCreate openFolderCreate() {
		browser.click(By.id("create-menu"));
		browser.click(By.linkText("Folder"));
		return page(FolderCreate.class);
	}

	public PurlCreate openPurlCreate() {
		browser.click(By.id("create-menu"));
		browser.click(By.linkText("PURL"));
		return page(PurlCreate.class);
	}

	public ClassEdit openClassCreate() {
		return openCreateHref("Class", ClassEdit.class);
	}

	public DomainCreate openDomainCreate() {
		return openCreateHref("Domain", DomainCreate.class);
	}

	public GroupCreate openGroupCreate() {
		return openCreateHref("Group", GroupCreate.class);
	}

	public DatasourceCreate openDatasourceCreate() {
		return openCreateHref("RdfDatasource", DatasourceCreate.class);
	}

	public TextEditor openTextCreate(String hrefEndsWith) {
		return openCreateHref(hrefEndsWith, TextEditor.class);
	}

	public <T> T openCreateHref(String hrefEndsWith, Class<T> type) {
		browser.click(By.id("create-menu"));
		browser.clickHiddenLink(".dropdown-menu a[href$=\""+ hrefEndsWith + "\"]");
		return page(type);
	}

	public FolderView waitUntilFolderOpen(String folderName) {
		browser.focusInTopWindow();
		browser.waitUntilTextPresent(By.tagName("h3"), folderName);
		return this;
	}

	public FolderEdit openEdit() {
		return openEdit(FolderEdit.class);
	}

	public ImportPage openImportDialogue() {
		browser.focusInTopWindow();
		browser.click(By.cssSelector("button.navbar-btn.dropdown-toggle"));
		browser.click(By.linkText("Import folder contents"));
		browser.focusInModalFrame("import-folder-contents");
		final FolderView folder = this;
		return new ImportPage(browser) {
			@Override
			public FolderView importCar() {
				super.importCar();
				browser.waitForFrameToClose("import-folder-contents");
				return folder;
			}
		};
	}

	public void assertLayout() {
		int bottom = browser.getPositionBottom(By.linkText("Callimachus"));
		int cogTop = browser.getPositionTop(By.cssSelector("button.navbar-btn.dropdown-toggle"));
		Assert.assertTrue(bottom > cogTop);
		Assert.assertEquals(browser.getText(By.id("totalEntries")), browser.getText(By.id("totalResults")));
		for (String text : browser.getTextOfElements(By.cssSelector("table tbody tr td:last-child"))) {
			if (!"super".equals(text)) {
				Assert.assertEquals("admin", text);
			}
		}
	}

}
