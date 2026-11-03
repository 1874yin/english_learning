package com.xiaojie.englishlearning.admin;

import com.xiaojie.englishlearning.translation.TranslationStatus;
import com.xiaojie.englishlearning.translation.TranslationStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only diagnostics for the automatic Chinese translation pipeline. The
 * admin auth interceptor protects every /api/admin/** route.
 */
@RestController
@RequestMapping("/api/admin/translation")
public class TranslationStatusController {

    private final TranslationStatusService translationStatusService;

    public TranslationStatusController(TranslationStatusService translationStatusService) {
        this.translationStatusService = translationStatusService;
    }

    @GetMapping("/status")
    public TranslationStatus status() {
        return translationStatusService.getStatus();
    }
}
