package com.exam.activity.web;

import com.exam.activity.service.ActivityService;
import com.exam.activity.web.dto.ApiResponses;
import com.exam.activity.web.error.BusinessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponses.data(activityService.list(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable String id) {
        long activityId = parsePositiveLong(id, "活动 ID");
        return ResponseEntity.ok(ApiResponses.data(activityService.detail(activityId)));
    }

    static long parsePositiveLong(String raw, String label) {
        try {
            long v = Long.parseLong(raw);
            if (v <= 0) {
                throw new BusinessException(400, "INVALID_ARGUMENT", label + "必须为正整数");
            }
            return v;
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "INVALID_ARGUMENT", label + "必须为正整数");
        }
    }
}
