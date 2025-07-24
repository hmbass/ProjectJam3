package com.projectjam.controller;

import com.projectjam.model.JiraTask;
import com.projectjam.model.ProjectInfo;
import com.projectjam.model.SimulationResult;
import com.projectjam.service.JiraService;
import com.projectjam.service.MonteCarloService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/risk-analysis")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RiskAnalysisController {
    
    private final JiraService jiraService;
    private final MonteCarloService monteCarloService;
    
    @GetMapping("/projects/search")
    public ResponseEntity<List<ProjectInfo>> searchProjects(@RequestParam String query) {
        try {
            List<ProjectInfo> projects = jiraService.searchProjects(query);
            return ResponseEntity.ok(projects);
        } catch (Exception e) {
            log.error("Error searching projects", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/projects")
    public ResponseEntity<List<String>> getAvailableProjects() {
        try {
            List<String> projects = jiraService.getAvailableProjects();
            return ResponseEntity.ok(projects);
        } catch (Exception e) {
            log.error("Error fetching available projects", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/projects/{projectKey}/tasks")
    public ResponseEntity<List<JiraTask>> getProjectTasks(@PathVariable String projectKey) {
        try {
            List<JiraTask> tasks = jiraService.getProjectTasks(projectKey);
            return ResponseEntity.ok(tasks);
        } catch (Exception e) {
            log.error("Error fetching tasks for project: {}", projectKey, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/projects/{projectKey}/tasks/lightweight")
    public ResponseEntity<List<JiraTask>> getProjectTasksLightweight(@PathVariable String projectKey) {
        try {
            List<JiraTask> tasks = jiraService.getProjectTasksLightweight(projectKey);
            return ResponseEntity.ok(tasks);
        } catch (Exception e) {
            log.error("Error fetching lightweight tasks for project: {}", projectKey, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/projects/{projectKey}/simulate")
    public ResponseEntity<SimulationResult> runSimulation(
            @PathVariable String projectKey,
            @RequestBody(required = false) Map<String, Object> request) {
        
        try {
            // 시뮬레이션 횟수 설정 (기본값: 10000)
            int numSimulations = 10000;
            if (request != null && request.containsKey("numSimulations")) {
                numSimulations = (Integer) request.get("numSimulations");
            }
            
            // 프로젝트 태스크 가져오기
            List<JiraTask> tasks = jiraService.getProjectTasks(projectKey);
            
            if (tasks.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            // Monte Carlo 시뮬레이션 실행
            SimulationResult result = monteCarloService.runSimulation(tasks, numSimulations);
            

            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error running simulation for project: {}", projectKey, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "Project Risk Analyzer"));
    }
} 