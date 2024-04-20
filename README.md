[![build](https://github.com/openhtmltopdf/openhtmltopdf/workflows/build/badge.svg)](https://github.com/openhtmltopdf/openhtmltopdf/actions?query=workflow%3Abuild)

# OPEN HTML TO PDF

![PDF screenshot of OpenHTMLtoPDF](screenshot.png)

## OVERVIEW
This is a community fork of [OpenHTMLtoPDF](https://github.com/danfickle/openhtmltopdf).

Open HTML to PDF is a pure-Java library for rendering a reasonable subset of well-formed XML/XHTML (and even some HTML5)
using CSS 2.1 (and later standards) for layout and formatting, outputting to PDF or images.

Use this library to generated nice looking PDF documents. But be aware that you can not throw modern HTML5+ at
this engine and expect a great result. You must special craft the HTML document for this library and 
use it's extended CSS feature like [#31](https://github.com/danfickle/openhtmltopdf/pull/31) or
[#32](https://github.com/danfickle/openhtmltopdf/pull/32) 
to get good results. Avoid floats near page breaks and use table layouts.

## GETTING STARTED
+ [Integration guide](https://github.com/danfickle/openhtmltopdf/wiki/Integration-Guide) - get maven artifacts and code to get started.
+ [1.0.10 Online Sandbox](https://sandbox.openhtmltopdf.com/) - Now with logs!
+ [Templates for Openhtmltopdf](https://danfickle.github.io/pdf-templates/index.html) - MIT licensed templates that work with this project. Updated 2021-09-21.
+ [Showcase Document - PDF](https://openhtmltopdf.com/showcase.pdf)
+ [Documentation wiki](https://github.com/danfickle/openhtmltopdf/wiki)
+ [Template Author Guide - PDF - DEPRECATED - Prefer wiki](https://openhtmltopdf.com/template-guide.pdf) - Moving info to wiki
+ [Sample Project - Pretty Resume Generator](https://github.com/danfickle/pretty-resume)

## DIFFERENCES WITH FLYING SAUCER
+ Uses the well-maintained and open-source (LGPL compatible) PDFBOX as PDF library, rather than iText.
+ Proper support for generating accessible PDFs (Section 508, PDF/UA, WCAG 2.0).
+ Proper support for generating PDF/A standards compliant PDFs.
+ New, faster renderer means this project can be several times faster for very large documents.
+ Better support for CSS3 transforms.
+ Automatic visual regression testing of PDFs, with many end-to-end tests.
+ Ability to insert pages for cut-off content.
+ Built-in plugins for SVG and MathML.
+ Font fallback support.
+ Limited support for RTL and bi-directional documents.
+ On the negative side, no support for OpenType fonts.
+ Footnote support.
+ Much more. See changelog below.

## LICENSE
Open HTML to PDF is distributed under the LGPL.  Open HTML to PDF itself is licensed 
under the GNU Lesser General Public License, version 2.1 or later, available at
http://www.gnu.org/copyleft/lesser.html. You can use Open HTML to PDF in any
way and for any purpose you want as long as you respect the terms of the 
license. A copy of the LGPL license is included as license-lgpl-2.1.txt or license-lgpl-3.txt
in our distributions and in our source tree.

An exception to this is the pdf-a testing module, which is licensed under the GPL. This module is not distributed to Maven Central
and is for testing only.

Open HTML to PDF uses a couple of FOSS packages to get the job done. A list
of these can be found in the [dependency graph](https://github.com/openhtmltopdf/openhtmltopdf/network/dependencies).

## CREDITS
Thanks to @danfickle for starting Open HTML to PDF.

Open HTML to PDF is based on [Flying-saucer](https://github.com/flyingsaucerproject/flyingsaucer). Credit goes to the contributors of that project. Code will also be used from [neoFlyingSaucer](https://github.com/danfickle/neoflyingsaucer)

## FAQ
+ OPEN HTML TO PDF is tested with OpenJDK 8, 11, 17 and 21. It requires at least Java 8 to run.
+ No, you can not use it on Android.
+ You should be able to use it on Google App Engine (Java 8 or greater environment). [Let us know your experience](https://github.com/danfickle/openhtmltopdf/issues/179).
+ <s>Flowing columns are not implemented.</s> Implemented in RC12.
+ No, it's not a web browser. Specifically, it does not run javascript or implement many modern standards such as flex and grid layout.

## TEST CASES
Test cases, failing or working are welcome, please place them
in ````/openhtmltopdf-examples/src/main/resources/testcases/````
and run them
from ````/openhtmltopdf-examples/src/main/java/com/openhtmltopdf/testcases/TestcaseRunner.java````.

## CHANGELOG

### head - 1.1.5-SNAPSHOT
+ See commit log.


### 1.1.4 (2023-October-1)
+ [9fbf939](https://github.com/openhtmltopdf/openhtmltopdf/commit/9fbf9390b1aac5ac2b0e565611c50ec9d1bb512a) **SECURITY** Update dependencies. Thanks, @imario42.


### 1.1.3 (2023-September-15)
+ [b0a40b7](https://github.com/openhtmltopdf/openhtmltopdf/commit/b0a40b78bcadb0e54b5e116e6a3cc3197f74e7f6) prepare for release. Thanks, @imario42.


### 1.1.2 (2023-September-15)
**NOTE**: This release is only available from GitHub.

+ [729d1ce](https://github.com/openhtmltopdf/openhtmltopdf/commit/729d1ceb7b79c1880e7806a46df9c311c9def5a1) prepare for release. Thanks, @imario42.


### 1.1.1 (2023-September-15)
**NOTE**: This release is only available from GitHub.

+ [ea8a2b9](https://github.com/openhtmltopdf/openhtmltopdf/commit/ea8a2b9111b7b49c228c53b396bd9b1babb33341) prepare for release. Thanks, @imario42.


### 1.1.0 (2023-September-15)
**NOTE**: This is the first release of the community fork. This release is only available from GitHub.

+ [16b6dd5](https://github.com/openhtmltopdf/openhtmltopdf/commit/16b6dd543c49f22fa421f32e77b6316cd0274e9e) Update to PDFBOX 3. Thanks, @imario42.
+ [99eb141](https://github.com/openhtmltopdf/openhtmltopdf/commit/99eb141b820d6c3c59dcea11890e0af15ac44578) changed maven group id to at.datenwort.openhtmltopdf. Thanks, @imario42.
+ [260aabf](https://github.com/openhtmltopdf/openhtmltopdf/commit/260aabf0b974726cfc0d03b39175f4db474dfe71) prepare for release. Thanks, @imario42.


### OLDER RELEASES

[View CHANGELOG.md](CHANGELOG.md).
