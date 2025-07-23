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
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

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
            
            // 필요한 필드들을 요청 (커스텀 필드 포함)
            String fieldsParam = "summary,description,status,assignee,priority,created,updated,duedate,timetracking,issuetype,epic,fixVersions,sprint,customfield_10332,customfield_10333";
            String apiUrl = jiraUrl + "/rest/api/2/search?jql=" + jql + "&fields=" + fieldsParam + "&maxResults=1000";
            log.info("🔗 Calling Jira API: {}", apiUrl);
            log.info("📋 JQL Query: {}", jql);
            log.info("📝 Requested fields: {}", fieldsParam);
            
            String response = webClient.get()
                    .uri(apiUrl)
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            JsonNode root = objectMapper.readTree(response);
            JsonNode issues = root.get("issues");
            
            log.info("✅ Jira API response received. Total issues: {}", issues.size());
            
            List<JiraTask> tasks = new ArrayList<>();
            for (JsonNode issue : issues) {
                JiraTask task = convertToJiraTask(issue);
                if (task != null) {
                    tasks.add(task);
                } else {
                    log.warn("⚠️ Skipping null task from issue: {}", issue.get("key"));
                }
            }
            
            log.info("🎯 Successfully converted {} tasks for project: {}", tasks.size(), projectKey);
            return tasks;
        } catch (Exception e) {
            log.error("❌ Error fetching tasks from Jira for project: {}", projectKey, e);
            throw new RuntimeException("Failed to fetch tasks from Jira", e);
        }
    }
    
    public List<JiraTask> getProjectTasksLightweight(String projectKey) {
        try {
            String jql = String.format("project = %s AND status != Closed ORDER BY created DESC", projectKey);
            String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            
            // 필요한 필드만 요청하여 응답 크기 줄이기
            String fieldsParam = "key,summary";
            String apiUrl = jiraUrl + "/rest/api/2/search?jql=" + jql + "&fields=" + fieldsParam + "&maxResults=1000";
            
            log.info("🔗 Calling Jira Lightweight API: {}", apiUrl);
            log.info("📋 JQL Query: {}", jql);
            log.info("📝 Requested fields: {}", fieldsParam);
            
            String response = webClient.get()
                    .uri(apiUrl)
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            JsonNode root = objectMapper.readTree(response);
            JsonNode issues = root.get("issues");
            
            log.info("✅ Jira Lightweight API response received. Total issues: {}", issues.size());
            
            List<JiraTask> tasks = new ArrayList<>();
            for (JsonNode issue : issues) {
                JsonNode fields = issue.get("fields");
                tasks.add(JiraTask.builder()
                        .key(issue.get("key").asText())
                        .summary(fields.get("summary") != null ? fields.get("summary").asText() : null)
                        .build());
            }
            
            log.info("🎯 Successfully fetched {} lightweight tasks for project: {}", tasks.size(), projectKey);
            return tasks;
        } catch (Exception e) {
            log.error("❌ Error fetching lightweight tasks from Jira for project: {}", projectKey, e);
            throw new RuntimeException("Failed to fetch lightweight tasks from Jira", e);
        }
    }
    
    public List<ProjectInfo> searchProjects(String searchTerm) {
        try {
            String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            
            // 검색어가 없으면 빈 리스트 반환
            if (searchTerm == null || searchTerm.trim().isEmpty()) {
                log.info("🔍 Empty search term provided, returning empty list");
                return new ArrayList<>();
            }
            
            String apiUrl = jiraUrl + "/rest/api/2/project?maxResults=50";
            log.info("🔗 Calling Jira Projects API: {}", apiUrl);
            log.info("🔍 Searching for projects with term: '{}'", searchTerm);
            
            String response = webClient.get()
                    .uri(apiUrl)
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            JsonNode projects = objectMapper.readTree(response);
            log.info("✅ Jira Projects API response received. Total projects: {}", projects.size());
            
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
            
            log.info("🎯 Found {} projects matching '{}'", matchingProjects.size(), searchTerm);
            return matchingProjects;
        } catch (Exception e) {
            log.error("❌ Error searching projects from Jira", e);
            throw new RuntimeException("Failed to search projects from Jira", e);
        }
    }
    
    public List<String> getAvailableProjects() {
        try {
            String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            
            String apiUrl = jiraUrl + "/rest/api/2/project?maxResults=1000";
            log.info("🔗 Calling Jira Projects API: {}", apiUrl);
            
            String response = webClient.get()
                    .uri(apiUrl)
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            JsonNode projects = objectMapper.readTree(response);
            log.info("✅ Jira Projects API response received. Total projects: {}", projects.size());
            
            List<String> projectKeys = new ArrayList<>();
            
            for (JsonNode project : projects) {
                projectKeys.add(project.get("key").asText());
            }
            
            log.info("🎯 Successfully fetched {} projects from Jira", projectKeys.size());
            return projectKeys;
        } catch (Exception e) {
            log.error("❌ Error fetching projects from Jira", e);
            throw new RuntimeException("Failed to fetch projects from Jira", e);
        }
    }
    
    private JiraTask convertToJiraTask(JsonNode issue) {
        try {
            JsonNode fields = issue.get("fields");
            if (fields == null) {
                log.warn("⚠️ Fields node is null for issue: {}", issue);
                return null;
            }
            
            JsonNode keyNode = issue.get("key");
            JsonNode idNode = issue.get("id");
            
            if (keyNode == null) {
                log.warn("⚠️ Key node is null for issue: {}", issue);
                return null;
            }
            
            String taskKey = keyNode.asText();
            String taskId = idNode != null ? idNode.asText() : taskKey;
            
            log.debug("🔄 Converting Jira issue to JiraTask: {}", taskKey);
            
            // 각 필드별 상세 로그
            String summary = getFieldText(fields, "summary");
            String description = getFieldText(fields, "description");
            String status = getNestedFieldText(fields, "status", "name");
            String assignee = getNestedFieldText(fields, "assignee", "name");
            String priority = getNestedFieldText(fields, "priority", "name");
            LocalDateTime created = parseDateTime(fields.get("created"));
            LocalDateTime updated = parseDateTime(fields.get("updated"));
            LocalDateTime dueDate = parseDateTime(fields.get("duedate"));
            Integer originalEstimate = parseTimeTracking(fields.get("timetracking"), "originalEstimateSeconds");
            Integer timeSpent = parseTimeTracking(fields.get("timetracking"), "timeSpentSeconds");
            Integer remainingEstimate = parseTimeTracking(fields.get("timetracking"), "remainingEstimateSeconds");
            String issueType = getNestedFieldText(fields, "issuetype", "name");
            
            // 커스텀 필드들 가져오기
            String cf10332 = getCustomFieldValue(fields, "customfield_10332");
            String cf10333 = getCustomFieldValue(fields, "customfield_10333");
            
            // 필드별 로그 출력
            log.debug("📋 Task Fields for {}: ", taskKey);
            log.debug("   - ID: {}", taskId);
            log.debug("   - Summary: {}", summary != null ? summary.substring(0, Math.min(50, summary.length())) + "..." : "null");
            log.debug("   - Description: {}", description != null ? "present" : "null");
            log.debug("   - Status: {}", status);
            log.debug("   - Assignee: {}", assignee);
            log.debug("   - Priority: {}", priority);
            log.debug("   - Created: {}", created);
            log.debug("   - Updated: {}", updated);
            log.debug("   - Due Date: {}", dueDate);
            log.debug("   - Original Estimate: {} seconds ({} hours)", originalEstimate, originalEstimate != null ? originalEstimate / 3600.0 : "null");
            log.debug("   - Time Spent: {} seconds ({} hours)", timeSpent, timeSpent != null ? timeSpent / 3600.0 : "null");
            log.debug("   - Remaining Estimate: {} seconds ({} hours)", remainingEstimate, remainingEstimate != null ? remainingEstimate / 3600.0 : "null");
            log.debug("   - Issue Type: {}", issueType);
            log.debug("   - CF10332: {}", cf10332);
            log.debug("   - CF10333: {}", cf10333);
            
            JiraTask task = JiraTask.builder()
                    .id(taskId)
                    .key(taskKey)
                    .summary(summary)
                    .description(description)
                    .status(status)
                    .assignee(assignee)
                    .priority(priority)
                    .created(created)
                    .updated(updated)
                    .dueDate(dueDate)
                    .originalEstimate(originalEstimate)
                    .timeSpent(timeSpent)
                    .remainingEstimate(remainingEstimate)
                    .issueType(issueType)
                    .cf10332(cf10332)
                    .cf10333(cf10333)
                    .build();
            
            log.debug("✅ Successfully converted task: {} (Status: {}, Assignee: {}, Priority: {})", 
                    taskKey, 
                    task.getStatus(), 
                    task.getAssignee(), 
                    task.getPriority());
            
            return task;
        } catch (Exception e) {
            log.error("❌ Error converting Jira issue to JiraTask: {}", issue, e);
            return null;
        }
    }
    
    private String getFieldText(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode != null && !fieldNode.isNull() ? fieldNode.asText() : null;
    }
    
    private String getNestedFieldText(JsonNode node, String fieldName, String nestedField) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode != null && !fieldNode.isNull()) {
            JsonNode nestedNode = fieldNode.get(nestedField);
            return nestedNode != null && !nestedNode.isNull() ? nestedNode.asText() : null;
        }
        return null;
    }
    
    private String getCustomFieldValue(JsonNode fields, String customFieldName) {
        JsonNode customField = fields.get(customFieldName);
        if (customField == null || customField.isNull()) {
            return null;
        }
        
        // 커스텀 필드는 다양한 형태로 올 수 있음 (문자열, 객체, 배열 등)
        if (customField.isTextual()) {
            return customField.asText();
        } else if (customField.isObject()) {
            // 객체인 경우 value 필드를 확인
            JsonNode valueNode = customField.get("value");
            if (valueNode != null && !valueNode.isNull()) {
                return valueNode.asText();
            }
            // value가 없으면 name 필드를 확인
            JsonNode nameNode = customField.get("name");
            if (nameNode != null && !nameNode.isNull()) {
                return nameNode.asText();
            }
        }
        
        return customField.toString();
    }
    
    private LocalDateTime parseDateTime(JsonNode dateNode) {
        if (dateNode == null || dateNode.isNull()) {
            return null;
        }
        String dateStr = dateNode.asText();
        
        log.debug("📅 Parsing date: {}", dateStr);
        
        // 타임존 정보가 포함된 경우 제거 (예: 2025-07-16T10:56:00.000+0900 -> 2025-07-16T10:56:00.000)
        if (dateStr.contains("+") || dateStr.contains("Z")) {
            // + 또는 Z 이전까지만 사용
            int timezoneIndex = dateStr.indexOf("+");
            if (timezoneIndex == -1) {
                timezoneIndex = dateStr.indexOf("Z");
            }
            if (timezoneIndex != -1) {
                dateStr = dateStr.substring(0, timezoneIndex);
                log.debug("🕐 Removed timezone info, parsed date: {}", dateStr);
            }
        }
        
        try {
            LocalDateTime parsedDate = LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            log.debug("✅ Successfully parsed date: {}", parsedDate);
            return parsedDate;
        } catch (Exception e) {
            log.error("❌ Failed to parse date: {}", dateStr, e);
            return null;
        }
    }
    
    private Integer parseTimeTracking(JsonNode timeTracking, String field) {
        if (timeTracking == null || timeTracking.isNull()) {
            log.debug("⏰ Time tracking is null for field: {}", field);
            return null;
        }
        JsonNode value = timeTracking.get(field);
        Integer result = value != null ? value.asInt() : null;
        log.debug("⏰ Time tracking field '{}': {} seconds ({} hours)", 
                field, result, result != null ? result / 3600.0 : "null");
        return result;
    }

    private double calculateScheduleRisk(List<Double> projectDurations) {
        DescriptiveStatistics stats = new DescriptiveStatistics(projectDurations.stream().mapToDouble(d -> d).toArray());
        double mean = stats.getMean();
        double p80 = stats.getPercentile(80);
        
        // P80이 평균보다 20% 이상 클 때 리스크로 판단
        return Math.min(1.0, Math.max(0.0, (p80 - mean) / mean));
    }

    private double calculateResourceRisk(List<JiraTask> tasks) {
        // 할당되지 않은 태스크 비율
        long unassignedCount = tasks.stream().filter(t -> t.getAssignee() == null).count();
        return (double) unassignedCount / tasks.size();
    }

    private double calculateScopeRisk(List<JiraTask> tasks) {
        // 추정치가 없는 태스크 비율
        long noEstimateCount = tasks.stream().filter(t -> t.getOriginalEstimate() == null).count();
        return (double) noEstimateCount / tasks.size();
    }
} 