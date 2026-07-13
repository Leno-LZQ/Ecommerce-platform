package com.ecommerce.exception;

import com.ecommerce.result.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    //业务异常
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleBusinessException(BusinessException e){
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    // 参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidationException(MethodArgumentNotValidException e){
        List<String> errors = e.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(ex -> ex.getField() + ": " + ex.getDefaultMessage())
            .toList();
        log.warn("参数校验失败: {}", errors);
        return Result.error(400, "参数校验失败"+errors);
    }

    //约束违反异常（@Validated 在类上/参数上的校验）
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> errors = ex.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .toList();
        return Result.error(400, "参数校验失败"+errors);
    }

    // 资源未找到
    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<?> handleNotFound(NoSuchElementException ex) {
        return Result.error(404, ex.getMessage());
    }

    // 方法参数类型不匹配（如 /users/abc，id 应为 Long）
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String msg = String.format("参数 '%s' 的值 '%s' 类型不匹配，期望类型: %s",
            ex.getName(), ex.getValue(),
            ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        return Result.error(400, msg);
    }

    // 空指针异常
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleNullPointer(NullPointerException ex) {
        log.error("空指针异常", ex);
        return Result.error(500, "系统内部错误");
    }

    // 兜底异常
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception ex) {
        log.error("未知异常", ex);
        return Result.error(500, "服务器内部错误，请联系管理员");
    }
}
