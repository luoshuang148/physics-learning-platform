package com.zhixuewuli.common.mapstruct;

import com.zhixuewuli.dto.response.UserResponse;
import com.zhixuewuli.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 用户映射器
 * 
 * 用于User实体类与UserResponse DTO之间的转换。
 * 使用MapStruct自动生成映射实现，提高开发效率和类型安全性。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see User 用户实体类
 * @see UserResponse 用户响应DTO
 */
@Mapper
public interface UserMapper {

    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    /**
     * 将User实体转换为UserResponse DTO
     * 
     * @param user 用户实体
     * @return UserResponse 用户响应DTO
     */
    @Mapping(source = "userType", target = "userType")
    @Mapping(source = "createdAt", target = "createTime")
    UserResponse toResponse(User user);

    /**
     * 将User实体列表转换为UserResponse DTO列表
     * 
     * @param users 用户实体列表
     * @return List<UserResponse> 用户响应DTO列表
     */
    List<UserResponse> toResponseList(List<User> users);
}