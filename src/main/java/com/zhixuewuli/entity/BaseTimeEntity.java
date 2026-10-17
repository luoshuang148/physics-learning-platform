package com.zhixuewuli.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 时间戳基类 - 纯数据层自动管理创建和更新时间
 * 
 * 通过JPA生命周期回调实现时间戳的自动设置，
 * 无需业务层任何干预，完全透明化处理。
 * 
 * <p>
 * 使用方式：
 * </p>
 * <pre>
 * {@code
 * @Entity
 * public class User extends BaseTimeEntity {
 *     // 实体字段...
 * }
 * }
 * </pre>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseTimeEntity {
    
    /** 记录创建时间 - 仅在首次持久化时设置 */
    @Column(name = "created_at")
    protected LocalDateTime createdAt;
    
    /** 记录最后更新时间 - 每次更新时自动刷新 */
    @Column(name = "updated_at")
    protected LocalDateTime updatedAt;
    
    /**
     * 实体持久化前回调 - 自动设置创建时间
     * 
     * 当实体首次保存到数据库时自动调用，
     * 确保createdAt字段被正确设置。
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
    
    /**
     * 实体更新前回调 - 自动更新时间戳
     * 
     * 当实体被修改并保存时自动调用，
     * 确保updatedAt字段反映最新修改时间。
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
