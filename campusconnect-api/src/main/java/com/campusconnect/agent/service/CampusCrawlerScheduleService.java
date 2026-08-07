package com.campusconnect.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 校园通知定时爬取 — 珠海科技学院
 *
 * 每天早 8 点、晚 8 点自动爬取官网通知公告
 * 旧通知 URL 去重自动跳过，仅导入新通知
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampusCrawlerScheduleService {

    private final CampusCrawlerService campusCrawlerService;

    @Scheduled(cron = "0 0 8,20 * * ?", zone = "Asia/Shanghai")
    public void autoImportZcstNotices() {
        log.info("【珠科爬虫】开始执行珠海科技学院官网通知自动导入");

        try {
            Map<String, Object> result = campusCrawlerService.crawlZcstNotices();
            log.info("【珠科爬虫】导入完成：total={}, success={}, fail={}",
                    result.get("totalScanned"), result.get("successCount"), result.get("failCount"));
        } catch (Exception e) {
            log.error("【珠科爬虫】导入失败", e);
        }
    }
}
