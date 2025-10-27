# MetadataUsesCessdaElsstKeywords

## High-level description

This class provides a utility for checking whether a **CESSDA data catalogue record**  
(DDI 2.5 metadata delivered via the CESSDA OAI-PMH endpoint) contains keywords  
that belong to the **ELSST controlled vocabulary**.  

It supports multiple detection strategies:

1. **Direct vocabulary declaration in the DDI keyword element**
   - Checks the `vocab` attribute for the literal `"ELSST"`.
2. **Declared vocabulary URI**
   - Checks the `vocabURI` attribute for the substring `"elsst.cessda.eu"`.
3. **Fuzzy matching via the ELSST API**
   - Queries the ELSST Topics API for candidate labels in the requested language  
     and compares keyword text (case-insensitive) against returned labels.

## Primary usage

Call `containsElsstKeywords(String url)` with a CESSDA catalogue *detail page URL*.  
The method will:

1. Extract the record identifier from the provided URL (expects a `/detail/{id}` path segment).  
2. Optionally extract a two-letter language code from the URL query parameter `lang`.  
3. Fetch the record via the configured OAI-PMH **GetRecord** endpoint (`metadataPrefix=oai_ddi25`).  
4. Parse the returned XML and isolate the DDI `<codeBook>` element for further XPath evaluation.  
5. Evaluate the configured XPath expression to obtain DDI keyword elements and apply  
   the detection strategies described above.

## Return values

- **`"pass"`** — One or more keywords were positively identified as ELSST (by attribute or API match).  
- **`"fail"`** — Keywords were found but none matched ELSST via API lookups.  
- **`"indeterminate"`** — No keywords were present, or an error/exception occurred that prevents  
  a definitive determination (e.g. missing language, HTTP/parse error).

## Networking and timeouts

- Uses a shared [`java.net.http.HttpClient`](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html)  
  configured with reasonable connect and request timeouts.  
- OAI-PMH XML fetch has a **30-second request timeout**;  
  ELSST API calls also use a 30-second timeout.  
- HTTP requests include `Accept` and `User-Agent` headers to influence server responses.

## XML processing

- Uses a namespace-aware [`DocumentBuilderFactory`](https://docs.oracle.com/en/java/javase/21/docs/api/java.xml/javax/xml/parsers/DocumentBuilderFactory.html)  
  and `XPath` with a simple [`NamespaceContext`](https://docs.oracle.com/en/java/javase/21/docs/api/java.xml/javax/xml/namespace/NamespaceContext.html)  
  that maps the `ddi` prefix to the DDI 2.5 namespace.  
- Only the first top-level `//ddi:codeBook` node is extracted and used to build a minimal XML document.  
- The DDI keyword search XPath is configurable via a class constant:  
`//ddi:codeBook/ddi:stdyDscr/ddi:stdyInfo/ddi:subject/ddi:keyword`

## ELSST API integration

- If direct DDI metadata does not identify ELSST membership, the class performs label-based lookups  
against the **ELSST Topics API**.  
- Each keyword triggers a search query combining the label and language.  
- Responses are parsed as JSON; expected structure:
- Top-level `"results"` array
- Each result containing a `"labels"` object mapping language codes to label text  
- Parsed labels are cached in a **volatile Set** to avoid repeated network calls.  
- Labels are normalized in the form `"lang:\"label\""` and compared case-insensitively  
against DDI keyword text.

## Concurrency and resource usage

- Uses **virtual threads** (`Executors.newVirtualThreadPerTaskExecutor()`)  
to perform parallel ELSST API queries when multiple keywords are present.  
- Each task returns a set of parsed labels.  
- The cache is guarded by a volatile `Set` reference, populated once.  
- Handles `InterruptedException` by restoring thread status and logging appropriately.

## Error handling and logging

- Errors while fetching or parsing OAI-PMH or ELSST API responses are logged at **SEVERE** level.  
- These typically cause the method to return `"indeterminate"`.  
- Non-200 HTTP responses from the ELSST API are logged and treated as empty results.  
- XML parse failures include a truncated preview of the response body for debugging.

## Input expectations and validation

- Catalogue URL must include `/detail/{identifier}`.  
If missing or empty, an `IllegalArgumentException` is thrown.  
- Language extraction is optional but required for ELSST API lookups:  
A query parameter `lang=xx` (two-letter code, case-insensitive) is recognized.  
- If no language is available and API matching is required,  
the result is `"indeterminate"`.

## Main entry point and exit codes

- A `main()` method is provided for CLI usage.  
It accepts a single URL argument and prints/logs the final result.  
- JVM exits with:
- Code **0** → when result is `"pass"`  
- Code **1** → otherwise

## Constants and customization

- Behaviour-determining constants:
- DDI namespace  
- OAI-PMH base URL  
- ELSST API base URL  
- XPath for keywords  
- Vocabulary/URI matching strings  
- Result token strings  
- Adjusting these constants allows adapting the tool for different endpoints or DDI variants.

## Thread safety

- Instances are mostly thread-safe after initial cache population  
(uses thread-safe `HttpClient` and volatile cache reference).  
- Concurrent invocations that trigger simultaneous cache initialization may  
result in redundant network calls but no data corruption.

## Notes and limitations

- Assumes the OAI-PMH provider returns valid DDI 2.5 XML containing a `<codeBook>` node.  
- Keyword matching via the ELSST API relies on exact label equality  
(no fuzzy or tokenized similarity matching).  
- JSON parsing expects `"results"` and `"labels"` objects;  
unexpected structures result in empty parsed results.
