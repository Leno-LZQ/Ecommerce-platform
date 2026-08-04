package com.ecommerce.dto.merchant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商家摘要信息（供 Feign 内部返回，精简版 MerchantVO）。
 * <p>
 * 与 merchant-service 的 MerchantVO 相比，去掉 UI 字段
 * （qualifications、auditLogs、subAccounts），仅保留 Feign 调用方需要的核心字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantSummaryDTO {

    private Long id;
    private Long userId;
    private String shopName;
    private String shopLogo;
    private String contactName;

    /** 联系电话脱敏：138****8888 */
    private String contactPhone;

    /** 0=待审核 1=正常 2=拒绝 3=冻结 4=注销 */
    private Integer status;

    private String auditRemark;

    // ===== 结算账户（脱敏） =====
    private String bankName;
    private String bankAccountMasked;
    private String accountHolder;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
