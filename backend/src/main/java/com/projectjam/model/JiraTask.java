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
    private Integer originalEstimate; // seconds
    private Integer timeSpent; // seconds
    private Integer remainingEstimate; // seconds
    private String epicLink;
    private String sprint;
    private String issueType;
    
    // 커스텀 필드들
    private String cf10332; // 커스텀 필드 10332
    private String cf10333; // 커스텀 필드 10333
} 