package com.ecommerce.merchantservice.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class MerchantVO {

    private Long id;
    private Long userId;
    private String shopName;
    private String shopLogo;
    private String shopDesc;
    private String contactName;

    /** 联系电话脱敏：138****8888 */
    private String contactPhone;

    /** 0=待审核 1=正常 2=拒绝 3=冻结 4=注销 */
    private Integer status;

    private String auditRemark;

    // ===== 结算账户（脱敏） =====
    private String bankName;
    private String bankAccountMasked;   // 仅展示后4位：****5678
    private String accountHolder;

    // ===== 扩展信息 =====
    private List<QualificationVO> qualifications;
    private List<AuditLogVO> auditLogs;
    private List<SubAccountVO> subAccounts;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // ===== 内嵌 VO =====

    @Data
    @Builder
    public static class QualificationVO {
        private Long id;
        private String qualType;
        private String qualFileUrl;
        private Integer status;      // 0=待审 1=通过 2=拒绝
    }

    @Data
    @Builder
    public static class AuditLogVO {
        private Long id;
        private Long operatorId;
        private Integer fromStatus;
        private Integer toStatus;
        private String remark;
        private LocalDateTime createTime;
    }
}
