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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class MetadataUsesCessdaElsstKeywordsTest {

    private MetadataUsesCessdaElsstKeywords checker;
    
    private HttpClient mockHttpClient;
    private HttpResponse<byte[]> mockResponseBytes;
    private HttpResponse<String> mockResponseString;

    @BeforeEach
    void setUp() throws Exception {
        checker = new MetadataUsesCessdaElsstKeywords();

        // Inject a mocked HttpClient via reflection
        mockHttpClient = mock(HttpClient.class);
        var field = MetadataUsesCessdaElsstKeywords.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(checker, mockHttpClient);

        mockResponseBytes = mock(HttpResponse.class);
        mockResponseString = mock(HttpResponse.class);
    }

    @Test
    void testExtractRecordIdentifier_valid() {
        String id = invokePrivateExtractRecordIdentifier("https://datacatalogue.cessda.eu/detail/abc123?lang=en");
        assertEquals("abc123", id);
    }

    @Test
    void testExtractRecordIdentifier_missingDetail_throws() {
        assertThrows(RuntimeException.class, () ->
                invokePrivateExtractRecordIdentifier("https://datacatalogue.cessda.eu/view/abc123"));
    }

    @Test
    void testExtractRecordIdentifier_emptyId_throws() {
        assertThrows(RuntimeException.class, () ->
                invokePrivateExtractRecordIdentifier("https://datacatalogue.cessda.eu/detail/"));
    }

    private String invokePrivateExtractRecordIdentifier(String url) {
        try {
            var m = MetadataUsesCessdaElsstKeywords.class.getDeclaredMethod("extractRecordIdentifier", String.class);
            m.setAccessible(true);
            return (String) m.invoke(checker, url);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testExtractLanguageCodeFromUrl_valid() throws Exception {
        var method = MetadataUsesCessdaElsstKeywords.class.getDeclaredMethod("extractLanguageCodeFromUrl", String.class);
        method.setAccessible(true);
        method.invoke(checker, "https://datacatalogue.cessda.eu/detail/abc123?lang=de");
        var field = MetadataUsesCessdaElsstKeywords.class.getDeclaredField("languageCode");
        field.setAccessible(true);
        assertEquals("de", field.get(checker));
    }

    @Test
    void testExtractLanguageCodeFromUrl_invalidParam() throws Exception {
        var method = MetadataUsesCessdaElsstKeywords.class.getDeclaredMethod("extractLanguageCodeFromUrl", String.class);
        method.setAccessible(true);
        method.invoke(checker, "https://datacatalogue.cessda.eu/detail/abc123?language=de");
        var field = MetadataUsesCessdaElsstKeywords.class.getDeclaredField("languageCode");
        field.setAccessible(true);
        assertNull(field.get(checker));
    }

    @Test
    void testFetchAndParseDocument_validXML() throws Exception {
        String xml = """
                <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                    <ddi:codeBook>
                        <ddi:stdyDscr>
                            <ddi:stdyInfo>
                                <ddi:subject>
                                    <ddi:keyword>HOUSING</ddi:keyword>
                                </ddi:subject>
                            </ddi:stdyInfo>
                        </ddi:stdyDscr>
                    </ddi:codeBook>
                </OAI-PMH>
                """;

        when(mockResponseBytes.statusCode()).thenReturn(200);
        when(mockResponseBytes.body()).thenReturn(xml.getBytes(StandardCharsets.UTF_8));
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponseBytes);

        Document doc = checker.fetchAndParseDocument("https://fake.url");
        assertNotNull(doc);
        assertEquals("codeBook", doc.getDocumentElement().getLocalName());
    }

    @Test
    void testFetchAndParseDocument_noCodeBook_throws() throws Exception {
        String xml = "<root></root>";
        when(mockResponseBytes.statusCode()).thenReturn(200);
        when(mockResponseBytes.body()).thenReturn(xml.getBytes(StandardCharsets.UTF_8));
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponseBytes);

        assertThrows(IOException.class, () ->
                checker.fetchAndParseDocument("https://fake.url"));
    }

    @Test
    void testFetchAndParseDocument_non200_throws() throws Exception {
        when(mockResponseBytes.statusCode()).thenReturn(500);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponseBytes);
        assertThrows(IOException.class, () ->
                checker.fetchAndParseDocument("https://fake.url"));
    }

    @Test
    void testParseElsstKeywords_validJson() throws Exception {
        String json = """
            {"results":[{"labels":{"en":"CHILD LABOUR","de":"KINDERARBEIT"}}]}
            """;
        var method = MetadataUsesCessdaElsstKeywords.class.getDeclaredMethod("parseElsstKeywords", String.class);
        method.setAccessible(true);
        Set<String> result = (Set<String>) method.invoke(checker, json);
        assertTrue(result.contains("en:\"CHILD LABOUR\""));
        assertTrue(result.contains("de:\"KINDERARBEIT\""));
    }

    @Test
    void testValidateKeywords_directELSSTAttribute() throws Exception {
        String xml = """
            <ddi:codeBook xmlns:ddi="ddi:codebook:2_5">
                <ddi:stdyDscr><ddi:stdyInfo><ddi:subject>
                    <ddi:keyword vocab="ELSST">INCOME</ddi:keyword>
                </ddi:subject></ddi:stdyInfo></ddi:stdyDscr>
            </ddi:codeBook>
            """;

        Document doc = XmlUtil.parse(xml);
        String result = checker.validateKeywords(doc, "https://datacatalogue.cessda.eu/detail/abc123?lang=en");
        assertEquals("indeterminate", result);
    }

    @Test
    void testValidateKeywords_withVocabURI() throws Exception {
        String xml = """
            <ddi:codeBook xmlns:ddi="ddi:codebook:2_5">
                <ddi:stdyDscr><ddi:stdyInfo><ddi:subject>
                    <ddi:keyword vocabURI="https://elsst.cessda.eu/id/123">EMPLOYMENT</ddi:keyword>
                </ddi:subject></ddi:stdyInfo></ddi:stdyDscr>
            </ddi:codeBook>
            """;
        Document doc = XmlUtil.parse(xml);
        String result = checker.validateKeywords(doc, "https://datacatalogue.cessda.eu/detail/abc123?lang=en");
        assertEquals("indeterminate", result);
    }

    @Test
    void testValidateKeywords_noKeywords() throws Exception {
        String xml = "<ddi:codeBook xmlns:ddi='ddi:codebook:2_5'></ddi:codeBook>";
        Document doc = XmlUtil.parse(xml);
        String result = checker.validateKeywords(doc, "https://datacatalogue.cessda.eu/detail/abc123?lang=en");
        assertEquals("indeterminate", result);
    }

    @Test
    void testContainsElsstKeywords_invalidUrl() {
        String result = checker.containsElsstKeywords("https://datacatalogue.cessda.eu/view/abc");
        assertEquals("indeterminate", result);
    }

    @Test
    void testMain_noArgs_exitsWith1() {
        assertThrows(UnsupportedOperationException.class, () -> {
            System.setSecurityManager(new NoExitSecurityManager());
            MetadataUsesCessdaElsstKeywords.main(new String[0]);
        });
    }

    // Helper to block System.exit()
    static class NoExitSecurityManager extends SecurityManager {
        @Override
        public void checkPermission(java.security.Permission perm) {}
        @Override
        public void checkExit(int status) { throw new SecurityException("Exit called with " + status); }
    }

    // Simple XML parser helper
    static class XmlUtil {
        static Document parse(String xml) throws Exception {
            var builder = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder();
            return builder.parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        }
    }
}
