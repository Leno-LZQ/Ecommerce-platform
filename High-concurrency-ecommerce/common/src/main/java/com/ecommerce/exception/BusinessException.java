package com.ecommerce.exception;

import com.ecommerce.constant.ErrorCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException{
    private final int code;

    public BusinessException(int code,String message){
        super(message);
        this.code = code;
    }

    public BusinessException(String message){
        this(500,message);
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }
}
