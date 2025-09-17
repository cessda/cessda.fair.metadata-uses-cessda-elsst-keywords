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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


public class CessdaElsstKeywordTest {
    private static final String[] MARKUP_EXTENSIONS = {
        ".md", ".markdown", ".mdown", ".mkdn", ".mkd", ".mdwn", ".mdtxt", ".mdtext",
        ".rst", ".txt", ".asciidoc", ".adoc", ".asc", ".textile", ".rdoc", ".org",
        ".creole", ".mediawiki", ".wiki", ".pod"
    };

    /** Logger. */
    private static final Logger LOGGER =
    LogManager.getLogger(CessdaElsstKeywordTest.class);

    static {
    // Set log level to INFO programmatically
    org.apache.logging.log4j.core.config.Configurator.setRootLevel(org.apache.logging.log4j.Level.INFO);
    }

    /** ObjectMapper for JSON parsing. */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** List to store details of occurences of search string, if any. */
    private List<String> findings = new ArrayList<>();

    /** Placeholder for Logger messages. */
    private static String logMessage = null;
    
    /**
     * Main method to run the GitHub Markup Scanner.
     * Usage: java GitHubMarkupScanner github-repo-url
     * Example: java GitHubMarkupScanner
     * 
     * @param args Command line arguments where the first argument is the GitHub repository URL.
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            args = new String[] { "https://github.com/john-shepherdson/eosc.node-registry.demo", "api.eu.badgr.io" };
            
            logMessage =  new String(args[0] + " and " + args[1] + "\n");
    
            LOGGER.error("Invalid arguments. Please provide the GitHub repository URL and the search substring.");
            LOGGER.info("Example: java eoscbeyond.eu.GitHubMarkupScanner https://github.com/john-shepherdson/eosc.node-registry.demo.git api.eu.badgr.io");
            LOGGER.info("Using default values: {}", logMessage);
        }
        
        String repoUrl = args[0];
        String searchSubstring = args[1];
        
        try {
            CessdaElsstKeywordTest scanner = new CessdaElsstKeywordTest();
            scanner.scanRepository(repoUrl,searchSubstring);
        } catch (Exception e) {
            LOGGER.info("Invalid URL format: {} ", e.getMessage());
        }

    }

    /**
     * Default constructor for GitHubMarkupScanner.
     * @return List of findings.
     */
    public List<String> getFindings() {
        return findings;
    }
    
    /**
     * Scans the specified GitHub repository for markup files and searches for the defined substring.
     * 
     * @param repoUrl The URL of the GitHub repository to scan.
     * @param searchSubstring The substring to search for in the markup files.
     * @throws Exception If an info occurs while fetching or processing the repository contents.
     */
    public void scanRepository(String repoUrlArg, String searchSubstringArg) throws Exception {
        // Extract owner and repo name from GitHub URL
        String[] repoInfo = parseGitHubUrl(repoUrlArg);
        String owner = repoInfo[0];
        String repo = repoInfo[1];
        
        // Get repository contents from root directory
        String apiUrl = String.format("https://api.github.com/repos/%s/%s/contents", owner, repo);
        List<FileInfo> files = getRepositoryContents(apiUrl);
        
        // Filter for markup files
        List<FileInfo> markupFiles = filterMarkupFiles(files);
        
        if (markupFiles.isEmpty()) {
            LOGGER.info("No markup files found in the root directory.\n");
            return;
        }
        
        logMessage = (markupFiles.size() + " markup files in " + apiUrl + " for " + searchSubstringArg + "\n");
        LOGGER.info("Scanning {}", logMessage);
        
        // Scan each markup file
        boolean found = false;
        for (FileInfo file : markupFiles) {
            if (scanFile(file, searchSubstringArg)) {
                // Set found to true if any file contains the substring
                found = true;
            }
        }
        
        if (!found) {
            logMessage = (searchSubstringArg + " found in markup files.");
            LOGGER.info("\n No occurrences of {}", logMessage);
        }
        else {
            logMessage = (findings.size() + " occurrence(s) of " + searchSubstringArg + " found in markup files.\n");
            LOGGER.info("\n {}", logMessage);
            for (String finding : findings) {
                LOGGER.info(finding);
            }
        }   
    }

    /**
     * Parses the GitHub repository URL to extract the owner and repository name.
     * 
     * @param url The GitHub repository URL.
     * @return An array containing the owner and repository name.
     * @throws IllegalArgumentException If the URL format is invalid.
     */
    public String[] parseGitHubUrl(String url) throws IllegalArgumentException {
        // Handle different GitHub URL formats
        Pattern pattern = Pattern.compile("https://github\\.com/([^/]+)/([^/]+)/?(?:\\.git)?");
        Matcher matcher = pattern.matcher(url);
        
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid GitHub repository URL format. Expected: https://github.com/owner/repo");
        }
        
        return new String[]{matcher.group(1), matcher.group(2)};
    }
    
    /**
     * Fetches the contents of the specified GitHub repository using the GitHub API.
     * 
     * @param apiUrl The API URL to fetch repository contents.
     * @return A list of FileInfo objects representing files in the repository.
     * @throws IllegalArgumentException If an error occurs while fetching or parsing the repository contents.
     */
    public List<FileInfo> getRepositoryContents(String apiUrl) throws Exception {
        @SuppressWarnings("deprecation")
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
        connection.setRequestProperty("User-Agent", "GitHubMarkupScanner/1.0");
        
        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("Failed to fetch repository contents. HTTP " + responseCode + 
                              ". Check if the repository exists and is public.");
        }
        
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        
        // Parse JSON response
        JsonNode jsonArray = objectMapper.readTree(response.toString());
        List<FileInfo> files = new ArrayList<>();
        
        for (JsonNode item : jsonArray) {
            if ("file".equals(item.get("type").asText())) {
                files.add(new FileInfo(
                    item.get("name").asText(),
                    item.get("download_url").asText()
                ));
            }
        }
        
        return files;
    }
    
    /**
     * Filters the list of files to include only those with markup file extensions.
     * 
     * @param files The list of FileInfo objects to filter.
     * @return A list of FileInfo objects that are markup files.
     */
    public List<FileInfo> filterMarkupFiles(List<FileInfo> files) {
        List<FileInfo> markupFiles = new ArrayList<>();
        
        for (FileInfo file : files) {
            String fileName = file.name.toLowerCase();
            for (String extension : MARKUP_EXTENSIONS) {
                if (fileName.endsWith(extension)) {
                    markupFiles.add(file);
                    break;
                }
            }
        }
        
        return markupFiles;
    }
    
    /**
     * Scans a single markup file for the specified substring and prints matching lines.
     * 
     * @param file The FileInfo object representing the file to scan.
     * @return true if the substring was found in the file, false otherwise.
     * @throws Exception If an error occurs while reading the file.
     */
    public boolean scanFile(FileInfo file, String searchSubstring) throws Exception {
        LOGGER.info("Scanning: {}", file.name);
        @SuppressWarnings("deprecation")
        HttpURLConnection connection = (HttpURLConnection) new URL(file.downloadUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "GitHubMarkupScanner/1.0");
        
        boolean foundInFile = false;
        int lineNumber = 0;
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            String details;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.contains(searchSubstring)) {
                    details = "File: " + file.name + ", Line " + lineNumber + ": " + line.trim();
                    foundInFile = true;
                    // Store the finding
                    findings.add(details);
                }
            }
        } 
        
        // Close the connection
        connection.disconnect();

        // Return true if the substring was found in this file
        if (!foundInFile) {
           logMessage =(searchSubstring + " found in " + file.name + "\n");
            LOGGER.info(" No occurrences of {}" , logMessage);
        }
    
        return foundInFile;
    }
    
    /**
     * Represents a file in the GitHub repository with its name and download URL.
     */
    static class FileInfo {
        final String name;
        final String downloadUrl;
        
        FileInfo(String name, String downloadUrl) {
            this.name = name;
            this.downloadUrl = downloadUrl;
        }
    }
}