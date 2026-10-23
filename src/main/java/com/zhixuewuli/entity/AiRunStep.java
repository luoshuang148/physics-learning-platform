package com.zhixuewuli.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity @Table(name="ai_run_steps",uniqueConstraints=@UniqueConstraint(name="uk_ai_run_step_order",columnNames={"run_id","step_order"}))
@Data @EqualsAndHashCode(callSuper=false)
public class AiRunStep extends BaseTimeEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="run_id",nullable=false) private Long runId;
    @Column(name="step_order",nullable=false) private Integer stepOrder;
    @Column(name="step_name",nullable=false,length=40) private String stepName;
    @Column(nullable=false,length=20) private String status;
    @Column(name="duration_ms") private Long durationMs;
    @Column(name="details_json",columnDefinition="json") private String detailsJson;
}
