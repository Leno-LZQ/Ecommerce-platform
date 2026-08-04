package com.ecommerce.promotionservice.controller;

import com.ecommerce.promotionservice.dto.SeckillActivityVO;
import com.ecommerce.promotionservice.service.SeckillService;
import com.ecommerce.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端秒杀接口。
 */
@RestController
@RequestMapping("/api/promotion")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    /**
     * 秒杀场次列表（进行中 + 即将开始）。
     */
    @GetMapping("/seckill/sessions")
    public Result<List<SeckillActivityVO>> getSessions() {
        return Result.success(seckillService.getSessions());
    }
}
