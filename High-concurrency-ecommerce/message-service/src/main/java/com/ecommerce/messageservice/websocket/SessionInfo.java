package com.ecommerce.messageservice.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SessionInfo {
    private String role;
    private Long id;
}
