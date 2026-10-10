package com.ees.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserSaveRequest {

    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64)
    private String username;

    /** 新增时必填；修改时留空表示不修改密码 */
    @Size(max = 128)
    private String password;

    @Size(max = 64)
    private String realName;

    @NotBlank(message = "角色不能为空")
    private String roleCode;

    @Size(max = 128)
    private String orgName;

    @Size(max = 128)
    private String email;

    @Size(max = 32)
    private String phone;

    private String status = "ENABLED";

    @Size(max = 500)
    private String remark;
}
