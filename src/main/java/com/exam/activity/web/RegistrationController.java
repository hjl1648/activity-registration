package com.exam.activity.web;

import com.exam.activity.service.RegistrationService;
import com.exam.activity.web.dto.ApiResponses;
import com.exam.activity.web.dto.RegisterRequest;
import com.exam.activity.web.error.BusinessException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping
    public ResponseEntity<?> register(@RequestHeader(value = "X-User-Id", required = false) String userHeader,
                                      @Valid @RequestBody RegisterRequest body) {
        long userId = parseUserId(userHeader);
        if (body.getActivityId() == null || body.getActivityId() <= 0) {
            throw new BusinessException(400, "INVALID_ARGUMENT", "activityId 必须为正整数");
        }
        RegistrationService.RegisterResult result =
                registrationService.register(userId, body.getActivityId(), body.getRequestId());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(ApiResponses.data(registrationService.toView(result.registration())));
    }

    @GetMapping
    public ResponseEntity<?> listMine(@RequestHeader(value = "X-User-Id", required = false) String userHeader,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        long userId = parseUserId(userHeader);
        return ResponseEntity.ok(ApiResponses.data(registrationService.listMine(userId, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getMine(@RequestHeader(value = "X-User-Id", required = false) String userHeader,
                                     @PathVariable String id) {
        long userId = parseUserId(userHeader);
        return ResponseEntity.ok(ApiResponses.data(registrationService.getMine(userId, id)));
    }

    static long parseUserId(String header) {
        if (header == null || header.isBlank()) {
            throw new BusinessException(400, "INVALID_ARGUMENT", "缺少 X-User-Id");
        }
        try {
            long v = Long.parseLong(header.trim());
            if (v <= 0) {
                throw new BusinessException(400, "INVALID_ARGUMENT", "X-User-Id 必须为正整数");
            }
            return v;
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "INVALID_ARGUMENT", "X-User-Id 必须为正整数");
        }
    }
}
