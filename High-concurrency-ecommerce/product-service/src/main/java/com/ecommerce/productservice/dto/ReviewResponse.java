package com.ecommerce.productservice.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReviewResponse {
    private Long id;
    private Long userId;
    private String userNickname;       // 用户昵称（需从 user-service 获取）
    private String userAvatar;         // 用户头像
    private Integer score;             // 1-5 星
    private String content;            // 评价内容
    private List<String> images;       // 晒图
    private String replyContent;       // 商家回复（可空）
    private LocalDateTime createTime;
}
