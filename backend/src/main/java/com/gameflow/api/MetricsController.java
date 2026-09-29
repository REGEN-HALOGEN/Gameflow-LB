package com.gameflow.api;

import com.gameflow.history.HistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final HistoryService history;

    public MetricsController(HistoryService history) {
        this.history = history;
    }

    @GetMapping("/history")
    public HistoryService.HistoryResponse history(
            @RequestParam(defaultValue = "300") int windowSec) {
        int clamped = Math.min(600, Math.max(60, windowSec));
        return history.getHistory(clamped);
    }
}
