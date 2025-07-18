package com.projectjam.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationResult {
    private String projectKey;
    private int totalSimulations;
    private double p50Duration; // 50% 확률 달성 기간
    private double p80Duration; // 80% 확률 달성 기간
    private double p90Duration; // 90% 확률 달성 기간
    private double meanDuration;
    private double standardDeviation;
    private double minDuration;
    private double maxDuration;
    private List<String> criticalPath;
    private Map<String, Double> taskCompletionProbabilities;
    private Map<String, TaskAnalysis> taskAnalyses; // 태스크별 상세 분석
    private List<Double> durationDistribution;
    private RiskAnalysis riskAnalysis;
    private String overallAssessment;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAnalysis {
        private double scheduleRisk; // 일정 리스크 점수 (0-1)
        private double resourceRisk; // 리소스 리스크 점수 (0-1)
        private double scopeRisk; // 범위 리스크 점수 (0-1)
        private List<String> highRiskTasks;
        private List<String> recommendations;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskAnalysis {
        private String taskKey;
        private double completionProbability; // 완료 확률 (0-1)
        private double estimatedDuration; // 예상 소요시간 (시간)
        private double optimisticDuration; // 낙관적 추정 (시간)
        private double pessimisticDuration; // 비관적 추정 (시간)
        private String riskLevel; // 리스크 레벨 (낮음/보통/높음)
        private double variability; // 변동성 (표준편차/평균)
        private String status; // 현재 상태
        private String assignee; // 담당자
        private String priority; // 우선순위
    }
} 