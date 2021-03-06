<?xml version="1.0" encoding="UTF-8" ?>
<!--
  - Copyright (c) 2014 3 Round Stones Inc., Some Rights Reserved
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -
  -->
<p:pipeline version="1.0"
        xmlns:p=     "http://www.w3.org/ns/xproc"
        xmlns:c=     "http://www.w3.org/ns/xproc-step"
        xmlns:l     ="http://xproc.org/library"
        xmlns:xhtml ="http://www.w3.org/1999/xhtml"
        xmlns:calli ="http://callimachusproject.org/rdf/2009/framework#">

    <p:serialization port="result" media-type="text/html" method="html" doctype-system="about:legacy-compat" />

    <p:option name="target" select="resolve-uri('/')" />
    <p:option name="query" select="''" />

    <p:import href="page-layout-html.xpl" />

    <p:choose>
        <p:when test="/c:data[starts-with(@content-type,'text/html')]">
            <p:unescape-markup content-type="text/html" />
            <p:unwrap match="/c:data" />
        </p:when>
        <p:otherwise>
            <p:identity />
        </p:otherwise>
    </p:choose>

    <p:identity name="page" />

    <p:identity>
        <p:input port="source">
            <p:inline>
                <html xmlns="http://www.w3.org/1999/xhtml">
                    <head>
                        <title></title>
                    </head>
                    <body>
                        <div class="container">
                            <h1></h1>
                            <pre></pre>
                        </div>
                    </body>
                </html>
            </p:inline>
        </p:input>
    </p:identity>

    <p:choose>
        <p:xpath-context>
            <p:pipe step="page" port="result" />
        </p:xpath-context>
        <p:when test="//xhtml:pre and //xhtml:h1 and //xhtml:title">
            <p:replace match="xhtml:title">
                <p:input port="replacement" select="//xhtml:title">
                    <p:pipe step="page" port="result" />
                </p:input>
            </p:replace>
            <p:replace match="xhtml:h1">
                <p:input port="replacement" select="//xhtml:h1">
                    <p:pipe step="page" port="result" />
                </p:input>
            </p:replace>
            <p:replace match="xhtml:pre">
                <p:input port="replacement" select="//xhtml:pre">
                    <p:pipe step="page" port="result" />
                </p:input>
            </p:replace>
        </p:when>
        <p:when test="//xhtml:h1 and //xhtml:title">
            <p:replace match="xhtml:title">
                <p:input port="replacement" select="//xhtml:title">
                    <p:pipe step="page" port="result" />
                </p:input>
            </p:replace>
            <p:replace match="xhtml:h1">
                <p:input port="replacement" select="//xhtml:h1">
                    <p:pipe step="page" port="result" />
                </p:input>
            </p:replace>
            <p:delete match="xhtml:pre" />
        </p:when>
        <p:when test="//xhtml:title">
            <p:replace match="xhtml:title">
                <p:input port="replacement" select="//xhtml:title">
                    <p:pipe step="page" port="result" />
                </p:input>
            </p:replace>
            <p:replace match="xhtml:h1">
                <p:input port="replacement" select="//xhtml:title">
                    <p:pipe step="page" port="result" />
                </p:input>
            </p:replace>
            <p:delete match="xhtml:pre" />
        </p:when>
        <p:otherwise>
            <p:identity>
                <p:input port="source">
                    <p:pipe step="page" port="result" />
                </p:input>
            </p:identity>
        </p:otherwise>
    </p:choose>

    <calli:page-layout-html>
        <p:with-option name="target"  select="$target" />
        <p:with-option name="query" select="$query" />
    </calli:page-layout-html>
</p:pipeline>
