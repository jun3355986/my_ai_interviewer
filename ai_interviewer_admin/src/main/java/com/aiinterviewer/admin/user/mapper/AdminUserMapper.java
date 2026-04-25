package com.aiinterviewer.admin.user.mapper;

import com.aiinterviewer.admin.user.AdminUserService;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface AdminUserMapper {

    Long countUsers(@Param("query") AdminUserService.AdminUserQuery query);

    List<AdminUserService.AdminUserListItem> selectUsers(
            @Param("query") AdminUserService.AdminUserQuery query,
            @Param("limit") long limit,
            @Param("offset") long offset);

    Integer countExistingUser(@Param("userId") Long userId);

    Integer countUserRole(
            @Param("userId") Long userId,
            @Param("roleCode") String roleCode);

    Integer countEnabledUsersByRoleCode(@Param("roleCode") String roleCode);

    int disableUser(@Param("userId") Long userId);

    int resetPassword(
            @Param("userId") Long userId,
            @Param("passwordHash") String passwordHash);
}
