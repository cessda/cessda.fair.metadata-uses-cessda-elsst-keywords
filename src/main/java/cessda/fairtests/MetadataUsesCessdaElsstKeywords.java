/*
 * SPDX-FileCopyrightText: 2025 CESSDA ERIC (support@cessda.eu)
 * 
 * SPDX-License-Identifier: Apache-2.0
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package cessda.fairtests;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * MetadataUsesCessdaElsstKeywords
 *
 * <p>High-level description:
 * This class provides a utility for checking whether a CESSDA data catalogue record
 * (DDI 2.5 metadata delivered via the CESSDA OAI-PMH endpoint) contains keywords
 * that belong to the ELSST controlled vocabulary. It supports multiple detection
 * strategies:
 *
 * <ol>
 *   <li>Direct vocabulary declaration in the DDI keyword element:
 *       - checks the "vocab" attribute for the literal "ELSST"</li>
 *   <li>Declared vocabulary URI:
 *       - checks the "vocabURI" attribute for the substring "elsst.cessda.eu"</li>
 *   <li>Fuzzy matching via the ELSST API:
 *       - queries the ELSST topics API for candidate labels in the requested language
 *         and compares keyword text (case-insensitive) against returned labels.</li>
 * </ol>
 *
 * <p>Primary usage:
 * - Call {@code containsElsstKeywords(String url)} with a CESSDA catalogue "detail" page URL.
 *   The method will:
 *     1. Extract the record identifier from the provided URL (expects a "/detail/{id}" path segment).
 *     2. Optionally extract a two-letter language code from the URL query parameter "lang".
 *     3. Fetch the record via the configured OAI-PMH GetRecord endpoint (metadataPrefix=oai_ddi25).
 *     4. Parse the returned XML and isolate the DDI <codeBook> element for further XPath evaluation.
 *     5. Evaluate the configured XPath expression to obtain DDI keyword elements and apply
 *        the detection strategies described above.
 *
 * <p>Return values:
 * - {@code "pass"}: one or more keywords were positively identified as ELSST (by attribute or API match).
 * - {@code "fail"}: keywords were found but none matched ELSST via API lookups.
 * - {@code "indeterminate"}: no keywords were present or an error/exception occurred that prevents a
 *   definitive determination (e.g. missing language, HTTP/parse error).
 */
public class MetadataUsesCessdaElsstKeywords {

    private static final String DDI_NAMESPACE = "ddi:codebook:2_5";
    private static final String ELSST_API_BASE = "https://skg-if-openapi.cessda.eu/api/topics";
    private static final String OAI_PMH_BASE = "https://datacatalogue.cessda.eu/oai-pmh/v0/oai?verb=GetRecord&metadataPrefix=oai_ddi25&identifier=";
    private static final String DDI_SEARCH_PATH = "//ddi:codeBook/ddi:stdyDscr/ddi:stdyInfo/ddi:subject/ddi:keyword";
    private static final String RESULT_PASS = "pass";
    private static final String RESULT_FAIL = "fail";
    private static final String RESULT_INDETERMINATE = "indeterminate";
    private static final String ELSST_VOCAB_NAME = "ELSST";
    private static final String ELSST_URI_SUBSTRING = "elsst.cessda.eu";

    private String languageCode;
    private volatile Set<String> cachedElsstKeywords;
    private final HttpClient httpClient;
    private final DocumentBuilderFactory documentBuilderFactory;
    private final XPathFactory xPathFactory;
    private static final Logger logger = Logger.getLogger(MetadataUsesCessdaElsstKeywords.class.getName());

    public MetadataUsesCessdaElsstKeywords() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.documentBuilderFactory.setNamespaceAware(true);
        this.xPathFactory = XPathFactory.newInstance();
        logger.setLevel(Level.INFO);
    }

    /** 
     * Get the identifier from the URL provided and use it build a call to the CDC OAI-PMH endpoint.
     * Extract the language code from the CDC URL.
     * Parse the returned DDI document to find keywords.
     * Build a query string for each keyword to call the ELSST API and include the language code.
     * Check the returned keywords from the ELSST API to see if any match the keywords in the DDI document. 
     * 
     * @param url - the CESSDA catalogue detail page URL
     * @return String - "pass", "fail" or "indeterminate"
     */
    public String containsElsstKeywords(String url) {
        try {
            String recordIdentifier = extractRecordIdentifier(url);
            logInfo("Extracted record identifier: " + recordIdentifier);

            extractLanguageCodeFromUrl(url);
             logInfo("Extracted language code: " + languageCode);

            Document doc = fetchAndParseDocument(OAI_PMH_BASE + recordIdentifier);
            return validateKeywords(doc, url);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logSevere("Error processing document: " + e.getMessage());
        } catch (Exception e) {
            logSevere("Error: " + e.getMessage());
        }
        return RESULT_INDETERMINATE;
    }

    /** 
     * Extract the record identifier from the provided URL.
     * 
     * @param url - the CESSDA catalogue detail page URL
     * @return String - the record identifier (to be used in the call to the CDC OAI-PMH endpoint)
     */
    private String extractRecordIdentifier(String url) {
        String cleanUrl = url.replaceAll("\\s+", "");
        int queryIndex = cleanUrl.indexOf('?');
        if (queryIndex != -1) cleanUrl = cleanUrl.substring(0, queryIndex);
        int detailIndex = cleanUrl.indexOf("/detail/");
        
        if (detailIndex == -1) throw new IllegalArgumentException("URL must contain '/detail/': " + url);
        String id = cleanUrl.substring(detailIndex + 8);
        
        if (id.isEmpty()) throw new IllegalArgumentException("No identifier in URL: " + url);
    
        return id;
    }

    /** 
     * Extract the language code from the provided URL.
     * 
     * @param url - the CESSDA catalogue detail page URL
     */
    private void extractLanguageCodeFromUrl(String url) {
        try {
            URI uri = URI.create(url);
            String query = uri.getQuery();
            if (query == null) return;
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && kv[0].equalsIgnoreCase("lang") && kv[1].matches("^[a-zA-Z]{2}$")) {
                    languageCode = kv[1].toLowerCase();
                    return;
                }
            }
        } catch (Exception e) {
            logSevere("URL Exception: " + e.getMessage());
        }
    }

    /** 
     * Fetch the OAI-PMH GetRecord XML and parse to extract the DDI codeBook element.
     * 
     * @param url - the OAI-PMH GetRecord URL
     * @return Document - the parsed DDI document
     * @throws IOException - if an I/O error occurs
     * @throws InterruptedException - if the operation is interrupted
     */
    public Document fetchAndParseDocument(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/xml, text/xml, */*")
                .header("User-Agent", "Java-HttpClient")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) throw new IOException("Failed to fetch document: HTTP " + response.statusCode());
        if (response.body() == null || response.body().length == 0) throw new IOException("Empty response body");

        try {
            logInfo("Parsing XML response from OAI-PMH endpoint at: " + url);
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document oaiDoc = builder.parse(new ByteArrayInputStream(response.body()));

            XPath xpath = createXPath();
            Node codeBookNode = (Node) xpath.evaluate("//ddi:codeBook", oaiDoc, XPathConstants.NODE);
            if (codeBookNode == null) throw new IllegalArgumentException("No DDI codeBook found");

            Document ddiDoc = builder.newDocument();
            ddiDoc.appendChild(ddiDoc.importNode(codeBookNode, true));
            return ddiDoc;

        } catch (Exception e) {
            logSevere("Failed to parse XML. Preview: " + new String(response.body(), 0, Math.min(500, response.body().length), StandardCharsets.UTF_8));
            throw new IOException("Failed to parse XML response", e);
        }
    }

    /** 
     * Create an XPath instance with DDI namespace context.
     * 
     * @return XPath - the configured XPath with DDI namespace context
     */
    private XPath createXPath() {
        XPath xpath = xPathFactory.newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {
            public String getNamespaceURI(String prefix) { return "ddi".equals(prefix) ? DDI_NAMESPACE : null; }
            public String getPrefix(String namespaceURI) { return null; }
            public Iterator<String> getPrefixes(String namespaceURI) { return null; }
        });
        return xpath;
    }

    /** 
     * Validate the keywords found in the DDI document against the ELSST vocabulary.
     * 
     * @param doc - the DDI document
     * @param url - the original CESSDA catalogue detail page URL
     * @return String - "pass", "fail" or "indeterminate"
     */
    public String validateKeywords(Document doc, String url) {
        try {
            XPath xpath = createXPath();
            NodeList nodes = (NodeList) xpath.evaluate(DDI_SEARCH_PATH, doc, XPathConstants.NODESET);

            if (nodes.getLength() == 0) {
                logInfo("No keywords found");
                return RESULT_FAIL;
            }

            List<String> keywordTexts = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (!(node instanceof Element e)) continue;

                String vocabAttr = e.getAttribute("vocab");
                if (ELSST_VOCAB_NAME.equals(vocabAttr)) {
                    logInfo("Found ELSST vocabulary declaration in 'vocab' attribute");
                    return RESULT_PASS;
                }
                   
                String vocabURI = e.getAttribute("vocabURI");
                if (vocabURI != null && vocabURI.contains(ELSST_URI_SUBSTRING)) {
                    logInfo("Found ELSST vocabulary declaration in 'vocabURI' attribute");
                    return RESULT_PASS;
                }
            
                String text = e.getTextContent();
                if (text != null && !text.trim().isEmpty()) keywordTexts.add(text.trim());
            }

            logInfo("Unable to determine from attributes, checking " + keywordTexts.size() + " keywords via ELSST API");
            return keywordTexts.isEmpty() ? RESULT_INDETERMINATE : validateAgainstElsstApi(keywordTexts, url);

        } catch (XPathExpressionException e) {
            logSevere("XPath evaluation error: " + e.getMessage());
            return RESULT_INDETERMINATE;
        } catch (Exception e) {
            logSevere("Error validating keywords: " + e.getMessage());
            return RESULT_INDETERMINATE; 
        }
    }

    /** 
     * Validate keywords against the ELSST API.
     * 
     * @param keywords - list of keywords to check
     * @param urlToCheck - the original CESSDA catalogue detail page URL
     * @return String - "pass", "fail" or "indeterminate"
     */
    private String validateAgainstElsstApi(List<String> keywords, String urlToCheck) {
        try {
            if (languageCode == null) extractLanguageCodeFromUrl(urlToCheck);
            if (languageCode == null) return RESULT_INDETERMINATE;

            Set<String> elsstKeywords = fetchElsstKeywords(keywords, languageCode);

            Set<String> elsstSet = elsstKeywords.stream()
                    .filter(k -> k.startsWith(languageCode + ":"))
                    .map(k -> k.substring(languageCode.length() + 2).replace("\"", "").trim().toUpperCase())
                    .collect(Collectors.toSet());

            long matches = keywords.stream().map(String::toUpperCase).filter(elsstSet::contains).count();
            if (matches >= 1) {
                logInfo(RESULT_PASS + ": Found " + matches + " keyword(s) matching ELSST vocabulary");
                return RESULT_PASS;
            } else {
                logInfo(RESULT_FAIL + ": No keywords match ELSST vocabulary");
                return RESULT_FAIL;
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logSevere("Failed to fetch ELSST keywords: " + e.getMessage());
            return RESULT_INDETERMINATE;
        }
    }

    /** 
     * For each keyword in the list, call the ELSST API to fetch matching keywords
     * 
     * @param keywords - list of keywords to check
     * @param langCode - the language code
     * @return Set<String> - set of ELSST keywords found
     * @throws InterruptedException - if the operation is interrupted
     */
    private Set<String> fetchElsstKeywords(List<String> keywords, String langCode) throws InterruptedException {
        if (cachedElsstKeywords != null) return cachedElsstKeywords;
        String encodedLangCode = URLEncoder.encode(langCode, StandardCharsets.UTF_8);

        List<String> queryUrls = keywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(k -> ELSST_API_BASE
                        + "?filter=cf.search.labels:" + URLEncoder.encode(k, StandardCharsets.UTF_8)
                        + ",cf.search.language:" + encodedLangCode)
                .toList();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Set<String>>> tasks = queryUrls.stream()
                    .map(url -> (Callable<Set<String>>) () -> {
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .header("Accept", "application/json")
                                .timeout(Duration.ofSeconds(30))
                                .GET().build();
                        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() != 200) {
                            logSevere("ELSST API returned " + response.statusCode() + " for: " + url);
                            return Set.of();
                        }
                        return parseElsstKeywords(response.body());
                    }).toList();

            cachedElsstKeywords = executor.invokeAll(tasks).stream()
                    .flatMap(f -> {
                        try {
                            return f.get().stream();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            logSevere("Interrupted while fetching ELSST keywords");
                        } catch (ExecutionException e) {
                            logSevere("Execution exception: " + e.getMessage());
                        }
                        return Stream.empty();
                    }).collect(Collectors.toSet());
        }

        logInfo("Number of cachedElsstKeywords: " + cachedElsstKeywords.size());
        return cachedElsstKeywords;
    }

    /** 
     * Parse the JSON returned by calling the ELSST keyword API
     * 
     * @param json - the JSON response string
     * @return Set<String> - set of ELSST keywords found
     * @throws IOException - if an I/O error occurs
     */
    private Set<String> parseElsstKeywords(String json) throws IOException {
        Set<String> keywords = new HashSet<>();
        JsonNode results = new ObjectMapper().readTree(json).path("results");
        if (results.isArray()) {
            for (JsonNode r : results) {
                JsonNode labels = r.path("labels");
                if (labels.isObject()) {
                    labels.fields().forEachRemaining(e -> keywords.add(e.getKey() + ":\"" + e.getValue().asText() + "\""));
                }
            }
        }
        return keywords;
    }

    /** 
     * Log info messages
     * 
     * @param msg - message to log
     */
    static void logInfo(String msg) {
        if (logger.isLoggable(Level.INFO)) logger.info(msg.replace("%", "%%"));
    }

    /** 
     * Log error messages
     * 
     * @param msg - message to log
     */
    static void logSevere(String msg) {
        if (logger.isLoggable(Level.SEVERE)) logger.severe(msg.replace("%", "%%"));
    }

    /** 
     * Main method
     * 
     * @param args
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            logSevere("Usage: java MetadataUsesCessdaElsstKeywords <url>");
            System.exit(1);
        }
        String url = args[0];
        logInfo("URL to check: " + url);
        MetadataUsesCessdaElsstKeywords test = new MetadataUsesCessdaElsstKeywords();
        String result = test.containsElsstKeywords(url);
        logInfo("\nResult: " + result);
        System.exit(result.equals(RESULT_PASS) ? 0 : 1);
    }
}
