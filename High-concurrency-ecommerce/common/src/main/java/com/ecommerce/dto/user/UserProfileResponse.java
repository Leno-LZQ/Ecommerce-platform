package com.ecommerce.dto.user;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileResponse {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String maskedPhone;
    private String email;
    private Integer status;
}
