package com.ecommerce.merchantservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class MerchantRegisterRequest {

    @NotBlank(message = "店铺名称不能为空")
    @Size(max = 100)
    private String shopName;

    @Size(max = 500)
    private String shopLogo;

    @Size(max = 500)
    private String shopDesc;

    @NotBlank(message = "联系人不能为空")
    @Size(max = 50)
    private String contactName;

    @NotBlank(message = "联系电话不能为空")
    @Size(max = 20)
    private String contactPhone;

    // ===== 结算账户 =====

    @NotBlank(message = "开户银行不能为空")
    @Size(max = 100)
    private String bankName;

    @NotBlank(message = "银行账号不能为空")
    @Size(max = 50)
    private String bankAccount;

    @NotBlank(message = "开户人不能为空")
    @Size(max = 50)
    private String accountHolder;

    // ===== 资质文件 =====

    @Size(min = 1, message = "至少上传一份资质文件")
    private List<QualificationItem> qualifications;

    @Data
    public static class QualificationItem {
        @NotBlank
        private String qualType;       // BUSINESS_LICENSE / ID_CARD / FOOD_PERMIT

        @NotBlank
        private String qualFileUrl;    // OSS URL
    }
}
