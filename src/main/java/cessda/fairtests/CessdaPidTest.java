/*
 * Copyright © 2025 CESSDA ERIC (${email})
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
 */

 package cessda.fairtests;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test for use of approved PID type (ARK, DOI, Handle, URN)
 * 
 * Metric to test if the metadata resource has a PID of the approved type. 
 * This is done by comparing the PID to the patterns (by regexp) of approved 
 * PID schemas, as specified in the CessdaPersistentIdentifierTypes vocabulary 
 * ( https://vocabularies.cessda.eu/vocabulary/CessdaPersistentIdentifierTypes)
 * 
 */ 

public class CessdaPidTest {
    
    // Constants
    private static final String HARVESTER_VERSION = Harvester.getVersion(); // Assuming this exists
    private static final String TEST_VERSION = "1.0.0";
    
    /**
     * Returns metadata for the fc_unique_identifier test
     */
    private static Map<String, Object> fcUniqueIdentifierMeta() {
        var meta = new HashMap<String, Object>();
        
        meta.put("testversion", HARVESTER_VERSION + ":" + TEST_VERSION);
        meta.put("testname", "FAIR Champion: Unique Identifier");
        meta.put("testid", "fc_unique_identifier");
        meta.put("description", 
            "Metric to test if the metadata resource has a PID of the approved type. "
            + "This is done by comparing the PID to the patterns (by regexp) of approved "
            + "PID schemas, as specified in the CessdaPersistentIdentifierTypes vocabulary "
            + "( https://vocabularies.cessda.eu/vocabulary/CessdaPersistentIdentifierTypes)"
        );
        meta.put("metric", "https://doi.org/10.25504/FAIRsharing.r49beq");
        meta.put("indicators", "https://w3id.org/fair/principles/latest/F1");
        meta.put("type", "http://edamontology.org/operation_2428");
        meta.put("license", "https://creativecommons.org/publicdomain/zero/1.0/");
        meta.put("keywords", List.of("FAIR Assessment", "FAIR Principles"));
        meta.put("themes", List.of("http://edamontology.org/topic_4012"));
        meta.put("organization", "OSTrails Project");
        meta.put("org_url", "https://cessda.eu/");
        meta.put("responsible_developer", "John W Shepherdson");
        meta.put("email", "john.shepherdson@cessda.eu");
        meta.put("response_description", "The response is \"pass\", \"fail\" or \"indeterminate\"");
        
        // Schemas
        var schemas = new HashMap<String, Object>();
        schemas.put("subject", List.of("string", "the GUID being tested"));
        meta.put("schemas", schemas);
        
        // Organizations
        var organizations = List.of(
            Map.of("name", "OSTrails Project", "url", "https://ostrails.eu/")
        );
        meta.put("organizations", organizations);
        
        // Individuals
        var individuals = List.of(
            Map.of("name", "John W Shepherdson", "email", "john.shepherdson@cessda.eu")
        );
        meta.put("individuals", individuals);
        
        meta.put("creator", "https://orcid.org/0000-0002-4402-9644");
        
        // Environment variables with defaults
        meta.put("protocol", System.getenv().getOrDefault("TEST_PROTOCOL", "https"));
        meta.put("host", System.getenv().getOrDefault("TEST_HOST", "localhost"));
        meta.put("basePath", System.getenv().getOrDefault("TEST_PATH", "/tests"));
        
        return meta;
    }
    
    /**
     * Main test method for unique identifier validation
     * 
     * @param guid The GUID to test
     * @return Evaluation response
     */
    public static EvaluationResponse fcUniqueIdentifier(String guid) {
        // Clear previous comments (assuming this is a static method)
        FairChampionOutput.clearComments();
        
        // Create output object
        var output = new FairChampionOutput.Builder()
            .testedGUID(guid)
            .meta(fcUniqueIdentifierMeta())
            .build();
        
        // Add version info comment
        var testVersion = fcUniqueIdentifierMeta().get("testversion");
        output.addComment("INFO: TEST VERSION '%s'%n".formatted(testVersion));
        
        // This is where the magic happens! - Type detection
        String type = Harvester.typeIt(guid);
        
        if (type == null) {
            output.setScore("indeterminate");
            output.addComment(
                "INDETERMINATE: The identifier %s did not match any known globally unique identifier system.%n"
                .formatted(guid)
            );
            return output.createEvaluationResponse();
        }
        
        // Success case
        output.addComment("SUCCESS: Found an identifier of type '%s'%n".formatted(type));
        output.setScore("pass");
        
        return output.createEvaluationResponse();
    }
    
    /**
     * Returns OpenAPI specification for the test
     */
    public static String fcUniqueIdentifierApi() {
        var api = new OpenAPI.Builder()
            .meta(fcUniqueIdentifierMeta())
            .build();
        return api.getApi();
    }
    
    /**
     * Returns DCAT record for the test
     */
    public static String fcUniqueIdentifierAbout() {
        var dcat = new ChampionDCAT.DCATRecord.Builder()
            .meta(fcUniqueIdentifierMeta())
            .build();
        return dcat.getDcat();
    }
    
    // Supporting classes that would need to be implemented
    
    /**
     * Output class for FAIR Champion evaluations
     */
    public static class FairChampionOutput {
        private String testedGUID;
        private Map<String, Object> meta;
        private String score;
        private final StringBuilder comments = new StringBuilder();
        
        // Static method to clear comments (if needed globally)
        public static void clearComments() {
            // Implementation depends on your architecture
            // This might clear a global comment buffer
        }
        
        public static class Builder {
            private final FairChampionOutput output = new FairChampionOutput();
            
            public Builder testedGUID(String guid) {
                output.testedGUID = guid;
                return this;
            }
            
            public Builder meta(Map<String, Object> meta) {
                output.meta = meta;
                return this;
            }
            
            public FairChampionOutput build() {
                return output;
            }
        }
        
        public void addComment(String comment) {
            comments.append(comment);
        }
        
        public void setScore(String score) {
            this.score = score;
        }
        
        public EvaluationResponse createEvaluationResponse() {
            return new EvaluationResponse(testedGUID, score, comments.toString(), meta);
        }
    }
    
    /**
     * Evaluation response class (replacing record for Java 11 compatibility)
     */
    public static class EvaluationResponse {
        private final String testedGUID;
        private final String score;
        private final String comments;
        private final Map<String, Object> meta;

        public EvaluationResponse(String testedGUID, String score, String comments, Map<String, Object> meta) {
            this.testedGUID = testedGUID;
            this.score = score;
            this.comments = comments;
            this.meta = meta;
        }

        public String getTestedGUID() {
            return testedGUID;
        }

        public String getScore() {
            return score;
        }

        public String getComments() {
            return comments;
        }

        public Map<String, Object> getMeta() {
            return meta;
        }
    }
    
    /**
     * Placeholder for Harvester class
     */
    private static class Harvester {
        public static String getVersion() {
            return "Hvst-1.4.2"; // Replace with actual version retrieval
        }
        
        public static String typeIt(String guid) {
            // Implementation of GUID type detection logic
            // This would contain the regex patterns for different GUID types
            return detectGuidType(guid);
        }
        
        private static String detectGuidType(String guid) {
            if (guid == null || guid.trim().isEmpty()) {
                return null;
            }
            
            // Example patterns - expand based on your requirements
            if (guid.matches("^https?://.*")) {
                return "URL";
            }
            if (guid.matches("^10\\.\\d+/.*")) {
                return "DOI";
            }
            if (guid.matches("^urn:.*")) {
                return "URN";
            }
            // Add more patterns as needed
            
            return null; // No match found
        }
    }
    
    /**
     * Placeholder for OpenAPI class
     */
    public static class OpenAPI {
        private final Map<String, Object> meta;
        
        public static class Builder {
            private Map<String, Object> meta;
            
            public Builder meta(Map<String, Object> meta) {
                this.meta = meta;
                return this;
            }
            
            public OpenAPI build() {
                return new OpenAPI(meta);
            }
        }
        
        private OpenAPI(Map<String, Object> meta) {
            this.meta = meta;
        }
        
        public String getApi() {
            // Generate OpenAPI specification based on meta
            return "OpenAPI specification JSON"; // Implement actual generation
        }
    }
    
    /**
     * Placeholder for ChampionDCAT class
     */
    public static class ChampionDCAT {
        public static class DCATRecord {
            private final Map<String, Object> meta;
            
            public static class Builder {
                private Map<String, Object> meta;
                
                public Builder meta(Map<String, Object> meta) {
                    this.meta = meta;
                    return this;
                }
                
                public DCATRecord build() {
                    return new DCATRecord(meta);
                }
            }
            
            private DCATRecord(Map<String, Object> meta) {
                this.meta = meta;
            }
            
            public String getDcat() {
                // Generate DCAT record based on meta
                return "DCAT record"; // Implement actual generation
            }
        }
    }
}

