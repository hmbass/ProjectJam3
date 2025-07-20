package com.projectjam.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JiraTask {
    private String id;
    private String key;
    private String summary;
    private String description;
    private String status;
    private String assignee;
    private String priority;
    private LocalDateTime created;
    private LocalDateTime updated;
    private LocalDateTime dueDate;
    private LocalDateTime wbsStartDate; // WBSGantt 시작일 (cf10332)
    private LocalDateTime wbsFinishDate; // WBSGantt 완료일 (cf10333)
    private Integer originalEstimate; // seconds
    private Integer timeSpent; // seconds
    private Integer remainingEstimate; // seconds
    private String epicLink;
    private String sprint;
    private String issueType;
} 