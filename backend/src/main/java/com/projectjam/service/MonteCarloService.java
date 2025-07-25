package com.projectjam.service;

import com.projectjam.model.JiraTask;
import com.projectjam.model.SimulationResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.TriangularDistribution;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MonteCarloService {
    
    private static final int DEFAULT_SIMULATIONS = 10000;
    private static final Random random = new Random();
    
    public SimulationResult runSimulation(List<JiraTask> tasks, int numSimulations) {
        if (numSimulations <= 0) {
            numSimulations = DEFAULT_SIMULATIONS;
        }
        

        
        List<Double> projectDurations = new ArrayList<>();
        Map<String, List<Double>> taskDurations = new HashMap<>();
        
        // 각 태스크별 시뮬레이션 결과 초기화
        for (JiraTask task : tasks) {
            taskDurations.put(task.getKey(), new ArrayList<>());
        }
        
        // Monte Carlo 시뮬레이션 실행
        for (int i = 0; i < numSimulations; i++) {
            double totalDuration = 0;
            
            for (JiraTask task : tasks) {
                double taskDuration = simulateTaskDuration(task);
                taskDurations.get(task.getKey()).add(taskDuration);
                totalDuration += taskDuration;
            }
            
            projectDurations.add(totalDuration);
        }
        
        // 통계 분석
        DescriptiveStatistics projectStats = new DescriptiveStatistics(projectDurations.stream().mapToDouble(d -> d).toArray());
        
        // 백분위수 계산
        double p50Duration = projectStats.getPercentile(50);
        double p80Duration = projectStats.getPercentile(80);
        double p90Duration = projectStats.getPercentile(90);
        
        // 크리티컬 패스 분석
        List<String> criticalPath = identifyCriticalPath(tasks, taskDurations);
        
        // 태스크별 완료 확률 계산
        Map<String, Double> taskCompletionProbabilities = calculateTaskCompletionProbabilities(tasks, taskDurations);
        
        // 태스크별 상세 분석 생성
        Map<String, SimulationResult.TaskAnalysis> taskAnalyses = generateTaskAnalyses(tasks, taskDurations, taskCompletionProbabilities);
        
        // 태스크별 상관관계 계산
        Map<String, Map<String, Double>> taskCorrelations = calculateTaskCorrelations(taskDurations);
        
        // 리스크 분석
        SimulationResult.RiskAnalysis riskAnalysis = analyzeRisks(tasks, projectDurations, taskDurations);
        
        // 종합 의견 생성
        String overallAssessment = generateOverallAssessment(projectStats, riskAnalysis, tasks.size());
        
        return SimulationResult.builder()
                .projectKey(tasks.isEmpty() ? "UNKNOWN" : tasks.get(0).getKey().split("-")[0])
                .totalSimulations(numSimulations)
                .p50Duration(p50Duration)
                .p80Duration(p80Duration)
                .p90Duration(p90Duration)
                .meanDuration(projectStats.getMean())
                .standardDeviation(projectStats.getStandardDeviation())
                .minDuration(projectStats.getMin())
                .maxDuration(projectStats.getMax())
                .criticalPath(criticalPath)
                .taskCompletionProbabilities(taskCompletionProbabilities)
                .taskAnalyses(taskAnalyses)
                .durationDistribution(projectDurations)
                .taskCorrelations(taskCorrelations)
                .riskAnalysis(riskAnalysis)
                .overallAssessment(overallAssessment)
                .build();
    }
    
    private double simulateTaskDuration(JiraTask task) {
        // cf10332 (시작일)와 cf10333 (종료일)을 사용하여 기간 계산
        LocalDateTime startDate = parseCustomDateTime(task.getCf10332());
        LocalDateTime endDate = parseCustomDateTime(task.getCf10333());
        
        double estimatedDuration = 8.0; // 기본값
        
        if (startDate != null && endDate != null && endDate.isAfter(startDate)) {
            // 두 날짜 사이의 시간 차이를 시간 단위로 계산
            long hoursBetween = java.time.Duration.between(startDate, endDate).toHours();
            estimatedDuration = Math.max(1.0, hoursBetween); // 최소 1시간
        } else {
            // 커스텀 필드가 없으면 기존 로직 사용
            estimatedDuration = task.getOriginalEstimate() != null ? task.getOriginalEstimate() / 3600.0 : 8.0;
        }
        
        // 삼각분포 파라미터 설정 (최적, 최대, 최소)
        double optimistic = estimatedDuration * 0.7; // 30% 단축 가능
        double mostLikely = estimatedDuration;
        double pessimistic = estimatedDuration * 2.0; // 100% 초과 가능
        
        // 우선순위에 따른 리스크 조정
        if ("High".equals(task.getPriority())) {
            pessimistic *= 1.5; // 높은 우선순위는 더 많은 불확실성
        } else if ("Low".equals(task.getPriority())) {
            pessimistic *= 0.8; // 낮은 우선순위는 상대적으로 안정적
        }
        
        TriangularDistribution distribution = new TriangularDistribution(optimistic, mostLikely, pessimistic);
        return distribution.sample();
    }
    
    private LocalDateTime parseCustomDateTime(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            // 다양한 날짜 형식 시도
            String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd"
            };
            
            for (String pattern : patterns) {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                    if (pattern.contains("Z")) {
                        return LocalDateTime.parse(dateStr, formatter);
                    } else {
                        return LocalDateTime.parse(dateStr, formatter);
                    }
                } catch (Exception e) {
                    // 다음 패턴 시도
                    continue;
                }
            }
            
            // ISO 형식 시도
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }
    
    private List<String> identifyCriticalPath(List<JiraTask> tasks, Map<String, List<Double>> taskDurations) {
        // 간단한 크리티컬 패스 식별 (가장 긴 평균 소요시간을 가진 태스크들)
        return taskDurations.entrySet().stream()
                .sorted((e1, e2) -> {
                    double avg1 = e1.getValue().stream().mapToDouble(d -> d).average().orElse(0);
                    double avg2 = e2.getValue().stream().mapToDouble(d -> d).average().orElse(0);
                    return Double.compare(avg2, avg1); // 내림차순
                })
                .limit(Math.min(5, tasks.size())) // 상위 5개 태스크
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    
    private Map<String, Double> calculateTaskCompletionProbabilities(List<JiraTask> tasks, Map<String, List<Double>> taskDurations) {
        Map<String, Double> probabilities = new HashMap<>();
        
        for (JiraTask task : tasks) {
            List<Double> durations = taskDurations.get(task.getKey());
            if (durations != null && !durations.isEmpty()) {
                // cf10332와 cf10333을 사용하여 예상 기간 계산
                LocalDateTime startDate = parseCustomDateTime(task.getCf10332());
                LocalDateTime endDate = parseCustomDateTime(task.getCf10333());
                
                double expectedDuration = 8.0; // 기본값
                
                final double finalExpectedDuration;
                if (startDate != null && endDate != null && endDate.isAfter(startDate)) {
                    long hoursBetween = java.time.Duration.between(startDate, endDate).toHours();
                    finalExpectedDuration = Math.max(1.0, hoursBetween);
                } else {
                    // 커스텀 필드가 없으면 기존 로직 사용
                    finalExpectedDuration = task.getOriginalEstimate() != null ? task.getOriginalEstimate() / 3600.0 : 8.0;
                }
                
                long onTimeCount = durations.stream().filter(d -> d <= finalExpectedDuration).count();
                double probability = (double) onTimeCount / durations.size();
                probabilities.put(task.getKey(), probability);
            }
        }
        
        return probabilities;
    }
    
    private SimulationResult.RiskAnalysis analyzeRisks(List<JiraTask> tasks, List<Double> projectDurations, Map<String, List<Double>> taskDurations) {
        // 일정 리스크 계산
        double scheduleRisk = calculateScheduleRisk(projectDurations);
        
        // 리소스 리스크 계산
        double resourceRisk = calculateResourceRisk(tasks);
        
        // 범위 리스크 계산
        double scopeRisk = calculateScopeRisk(tasks);
        
        // 고위험 태스크 식별
        List<String> highRiskTasks = identifyHighRiskTasks(tasks, taskDurations);
        
        // 권장사항 생성
        List<String> recommendations = generateRecommendations(scheduleRisk, resourceRisk, scopeRisk, highRiskTasks);
        
        return SimulationResult.RiskAnalysis.builder()
                .scheduleRisk(scheduleRisk)
                .resourceRisk(resourceRisk)
                .scopeRisk(scopeRisk)
                .highRiskTasks(highRiskTasks)
                .recommendations(recommendations)
                .build();
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
    
    private List<String> identifyHighRiskTasks(List<JiraTask> tasks, Map<String, List<Double>> taskDurations) {
        return taskDurations.entrySet().stream()
                .filter(entry -> {
                    List<Double> durations = entry.getValue();
                    if (durations.isEmpty()) return false;
                    
                    DescriptiveStatistics stats = new DescriptiveStatistics(durations.stream().mapToDouble(d -> d).toArray());
                    double cv = stats.getStandardDeviation() / stats.getMean(); // 변동계수
                    
                    // 변동계수가 0.5 이상이거나 평균이 40시간 이상인 태스크를 고위험으로 분류
                    return cv > 0.5 || stats.getMean() > 40;
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    
    private List<String> generateRecommendations(double scheduleRisk, double resourceRisk, double scopeRisk, List<String> highRiskTasks) {
        List<String> recommendations = new ArrayList<>();
        
        if (scheduleRisk > 0.3) {
            recommendations.add("일정 리스크가 높습니다. 버퍼 시간을 추가하거나 병렬 작업을 고려하세요.");
        }
        
        if (resourceRisk > 0.2) {
            recommendations.add("할당되지 않은 태스크가 많습니다. 리소스 할당을 우선적으로 진행하세요.");
        }
        
        if (scopeRisk > 0.1) {
            recommendations.add("추정치가 없는 태스크가 있습니다. 모든 태스크에 대해 시간 추정을 완료하세요.");
        }
        
        if (!highRiskTasks.isEmpty()) {
            recommendations.add("고위험 태스크(" + highRiskTasks.size() + "개)에 대해 세부 분석이 필요합니다.");
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("현재 프로젝트 상태는 양호합니다. 정기적인 모니터링을 유지하세요.");
        }
        
        return recommendations;
    }
    
    private String generateOverallAssessment(DescriptiveStatistics projectStats, SimulationResult.RiskAnalysis riskAnalysis, int taskCount) {
        double meanDuration = projectStats.getMean();
        double cv = projectStats.getStandardDeviation() / meanDuration;
        
        StringBuilder assessment = new StringBuilder();
        assessment.append(String.format("프로젝트는 평균 %.1f일(%.1f시간) 소요될 것으로 예상됩니다. ", 
                meanDuration / 8.0, meanDuration));
        
        if (cv < 0.2) {
            assessment.append("일정 예측의 불확실성이 낮아 안정적인 프로젝트입니다. ");
        } else if (cv < 0.4) {
            assessment.append("일정 예측에 중간 정도의 불확실성이 있습니다. ");
        } else {
            assessment.append("일정 예측의 불확실성이 높아 주의가 필요합니다. ");
        }
        
        double totalRisk = (riskAnalysis.getScheduleRisk() + riskAnalysis.getResourceRisk() + riskAnalysis.getScopeRisk()) / 3.0;
        
        if (totalRisk < 0.2) {
            assessment.append("전반적인 리스크 수준은 낮습니다.");
        } else if (totalRisk < 0.5) {
            assessment.append("중간 정도의 리스크가 있어 모니터링이 필요합니다.");
        } else {
            assessment.append("높은 리스크 수준으로 즉각적인 조치가 필요합니다.");
        }
        
        return assessment.toString();
    }
    
    private Map<String, SimulationResult.TaskAnalysis> generateTaskAnalyses(List<JiraTask> tasks, Map<String, List<Double>> taskDurations, Map<String, Double> taskCompletionProbabilities) {
        Map<String, SimulationResult.TaskAnalysis> taskAnalyses = new HashMap<>();
        
        for (JiraTask task : tasks) {
            List<Double> durations = taskDurations.get(task.getKey());
            if (durations != null && !durations.isEmpty()) {
                DescriptiveStatistics stats = new DescriptiveStatistics(durations.stream().mapToDouble(d -> d).toArray());
                
                // 리스크 레벨 결정
                String riskLevel;
                double completionProb = taskCompletionProbabilities.getOrDefault(task.getKey(), 0.5);
                double variability = stats.getStandardDeviation() / stats.getMean();
                
                if (completionProb >= 0.8 && variability < 0.3) {
                    riskLevel = "낮음";
                } else if (completionProb >= 0.6 && variability < 0.5) {
                    riskLevel = "보통";
                } else {
                    riskLevel = "높음";
                }
                
                // cf10332와 cf10333을 사용하여 예상 기간 계산
                LocalDateTime startDate = parseCustomDateTime(task.getCf10332());
                LocalDateTime endDate = parseCustomDateTime(task.getCf10333());
                
                double estimatedDuration = 8.0; // 기본값
                if (startDate != null && endDate != null && endDate.isAfter(startDate)) {
                    long hoursBetween = java.time.Duration.between(startDate, endDate).toHours();
                    estimatedDuration = Math.max(1.0, hoursBetween);
                } else {
                    estimatedDuration = task.getOriginalEstimate() != null ? task.getOriginalEstimate() / 3600.0 : 8.0;
                }
                
                double optimisticDuration = estimatedDuration * 0.7;
                double pessimisticDuration = estimatedDuration * 2.0;
                
                taskAnalyses.put(task.getKey(), SimulationResult.TaskAnalysis.builder()
                        .taskKey(task.getKey())
                        .completionProbability(completionProb)
                        .estimatedDuration(stats.getMean())
                        .optimisticDuration(optimisticDuration)
                        .pessimisticDuration(pessimisticDuration)
                        .riskLevel(riskLevel)
                        .variability(variability)
                        .status(task.getStatus())
                        .assignee(task.getAssignee())
                        .priority(task.getPriority())
                        .build());
            }
        }
        
        return taskAnalyses;
    }
    
    private Map<String, Map<String, Double>> calculateTaskCorrelations(Map<String, List<Double>> taskDurations) {
        Map<String, Map<String, Double>> correlations = new HashMap<>();
        List<String> taskKeys = new ArrayList<>(taskDurations.keySet());
        
        for (int i = 0; i < taskKeys.size(); i++) {
            String task1 = taskKeys.get(i);
            correlations.put(task1, new HashMap<>());
            
            for (int j = 0; j < taskKeys.size(); j++) {
                String task2 = taskKeys.get(j);
                
                if (i == j) {
                    // 자기 자신과의 상관관계는 1.0
                    correlations.get(task1).put(task2, 1.0);
                } else {
                    // 두 태스크 간의 상관관계 계산
                    double correlation = calculateCorrelation(taskDurations.get(task1), taskDurations.get(task2));
                    correlations.get(task1).put(task2, correlation);
                }
            }
        }
        
        return correlations;
    }
    
    private double calculateCorrelation(List<Double> list1, List<Double> list2) {
        if (list1.size() != list2.size() || list1.isEmpty()) {
            return 0.0;
        }
        
        int n = list1.size();
        double sum1 = 0, sum2 = 0, sum1Sq = 0, sum2Sq = 0, pSum = 0;
        
        for (int i = 0; i < n; i++) {
            double x = list1.get(i);
            double y = list2.get(i);
            
            sum1 += x;
            sum2 += y;
            sum1Sq += x * x;
            sum2Sq += y * y;
            pSum += x * y;
        }
        
        double num = pSum - (sum1 * sum2 / n);
        double den = Math.sqrt((sum1Sq - sum1 * sum1 / n) * (sum2Sq - sum2 * sum2 / n));
        
        if (den == 0) {
            return 0.0;
        }
        
        return num / den;
    }
} 