package com.projectjam.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectjam.model.JiraTask;
import com.projectjam.model.ProjectInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class JiraService {
    
    @Value("${jira.url}")
    private String jiraUrl;
    
    @Value("${jira.username}")
    private String username;
    
    @Value("${jira.password}")
    private String password;
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    public JiraService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    public List<JiraTask> getProjectTasks(String projectKey) {
        try {
            String jql = String.format("project = %s AND status != Closed ORDER BY created DESC", projectKey);
            String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            
            String response = webClient.get()
                    .uri(jiraUrl + "/rest/api/2/search?jql=" + jql + "&maxResults=1000")
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            JsonNode root = objectMapper.readTree(response);
            JsonNode issues = root.get("issues");
            
            List<JiraTask> tasks = new ArrayList<>();
            for (JsonNode issue : issues) {
                tasks.add(convertToJiraTask(issue));
            }
            
            return tasks;
        } catch (Exception e) {
            log.error("Error fetching tasks from Jira for project: {}", projectKey, e);
            throw new RuntimeException("Failed to fetch tasks from Jira", e);
        }
    }
    
    public List<JiraTask> getProjectTasksLightweight(String projectKey) {
        try {
            String jql = String.format("project = %s AND status != Closed ORDER BY created DESC", projectKey);
            String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            
            // 필요한 필드만 요청하여 응답 크기 줄이기
            String fieldsParam = "key,summary";
            String response = webClient.get()
                    .uri(jiraUrl + "/rest/api/2/search?jql=" + jql + "&fields=" + fieldsParam + "&maxResults=1000")
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            JsonNode root = objectMapper.readTree(response);
            JsonNode issues = root.get("issues");
            
            List<JiraTask> tasks = new ArrayList<>();
            for (JsonNode issue : issues) {
                JsonNode fields = issue.get("fields");
                tasks.add(JiraTask.builder()
                        .key(issue.get("key").asText())
                        .summary(fields.get("summary") != null ? fields.get("summary").asText() : null)
                        .build());
            }
            
            log.info("Successfully fetched {} lightweight tasks for project: {}", tasks.size(), projectKey);
            return tasks;
        } catch (Exception e) {
            log.error("Error fetching lightweight tasks from Jira for project: {}", projectKey, e);
            throw new RuntimeException("Failed to fetch lightweight tasks from Jira", e);
        }
    }
    
    public List<ProjectInfo> searchProjects(String searchTerm) {
        try {
            String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            
            // 검색어가 없으면 빈 리스트 반환
            if (searchTerm == null || searchTerm.trim().isEmpty()) {
                return new ArrayList<>();
            }
            
            String response = webClient.get()
                    .uri(jiraUrl + "/rest/api/2/project?maxResults=50")
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            JsonNode projects = objectMapper.readTree(response);
            List<ProjectInfo> matchingProjects = new ArrayList<>();
            
            String searchLower = searchTerm.toLowerCase();
            
            for (JsonNode project : projects) {
                String key = project.get("key").asText();
                String name = project.get("name").asText();
                
                // 프로젝트 키나 이름에 검색어가 포함되어 있는지 확인
                if (key.toLowerCase().contains(searchLower) || 
                    name.toLowerCase().contains(searchLower)) {
                    matchingProjects.add(new ProjectInfo(key, name));
                }
            }
            
            log.info("Found {} projects matching '{}'", matchingProjects.size(), searchTerm);
            return matchingProjects;
        } catch (Exception e) {
            log.error("Error searching projects from Jira", e);
            throw new RuntimeException("Failed to search projects from Jira", e);
        }
    }
    
    public List<String> getAvailableProjects() {
        try {
            String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            
            String response = webClient.get()
                    .uri(jiraUrl + "/rest/api/2/project?maxResults=1000")
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            JsonNode projects = objectMapper.readTree(response);
            List<String> projectKeys = new ArrayList<>();
            
            for (JsonNode project : projects) {
                projectKeys.add(project.get("key").asText());
            }
            
            log.info("Successfully fetched {} projects from Jira", projectKeys.size());
            return projectKeys;
        } catch (Exception e) {
            log.error("Error fetching projects from Jira", e);
            throw new RuntimeException("Failed to fetch projects from Jira", e);
        }
    }
    
    private JiraTask convertToJiraTask(JsonNode issue) {
        JsonNode fields = issue.get("fields");
        
        return JiraTask.builder()
                .id(issue.get("id").asText())
                .key(issue.get("key").asText())
                .summary(fields.get("summary") != null ? fields.get("summary").asText() : null)
                .description(fields.get("description") != null ? fields.get("description").asText() : null)
                .status(fields.get("status") != null ? fields.get("status").get("name").asText() : null)
                .assignee(fields.get("assignee") != null ? fields.get("assignee").get("name").asText() : null)
                .priority(fields.get("priority") != null ? fields.get("priority").get("name").asText() : null)
                .created(parseDateTime(fields.get("created")))
                .updated(parseDateTime(fields.get("updated")))
                .dueDate(parseDateTime(fields.get("duedate")))
                .originalEstimate(parseTimeTracking(fields.get("timetracking"), "originalEstimateSeconds"))
                .timeSpent(parseTimeTracking(fields.get("timetracking"), "timeSpentSeconds"))
                .remainingEstimate(parseTimeTracking(fields.get("timetracking"), "remainingEstimateSeconds"))
                .issueType(fields.get("issuetype") != null ? fields.get("issuetype").get("name").asText() : null)
                .build();
    }
    
    private LocalDateTime parseDateTime(JsonNode dateNode) {
        if (dateNode == null || dateNode.isNull()) {
            return null;
        }
        String dateStr = dateNode.asText();
        
        // 타임존 정보가 포함된 경우 제거 (예: 2025-07-16T10:56:00.000+0900 -> 2025-07-16T10:56:00.000)
        if (dateStr.contains("+") || dateStr.contains("Z")) {
            // + 또는 Z 이전까지만 사용
            int timezoneIndex = dateStr.indexOf("+");
            if (timezoneIndex == -1) {
                timezoneIndex = dateStr.indexOf("Z");
            }
            if (timezoneIndex != -1) {
                dateStr = dateStr.substring(0, timezoneIndex);
            }
        }
        
        return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
    
    private Integer parseTimeTracking(JsonNode timeTracking, String field) {
        if (timeTracking == null || timeTracking.isNull()) {
            return null;
        }
        JsonNode value = timeTracking.get(field);
        return value != null ? value.asInt() : null;
    }
} 