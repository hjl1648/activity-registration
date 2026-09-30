package com.exam.activity.service;

import com.exam.activity.domain.Activity;
import com.exam.activity.domain.Registration;
import com.exam.activity.repo.ActivityRepository;
import com.exam.activity.repo.RegistrationRepository;
import com.exam.activity.web.error.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RegistrationService {

    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final ActivityRepository activityRepository;
    private final RegistrationRepository registrationRepository;
    private final TransactionTemplate transactionTemplate;

    public RegistrationService(ActivityRepository activityRepository,
                               RegistrationRepository registrationRepository,
                               TransactionTemplate transactionTemplate) {
        this.activityRepository = activityRepository;
        this.registrationRepository = registrationRepository;
        this.transactionTemplate = transactionTemplate;
    }

    public record RegisterResult(Registration registration, boolean created) {}

    /**
     * 对外入口：事务失败回滚后，在事务外重新判定（避免 RR 快照看不见已提交的并发插入）。
     */
    public RegisterResult register(long userId, long activityId, String requestId) {
        validateRequestId(requestId);

        try {
            RegisterResult result = transactionTemplate.execute(status ->
                    registerInTransaction(userId, activityId, requestId));
            if (result == null) {
                throw new BusinessException(500, "INTERNAL_ERROR", "报名事务未返回结果");
            }
            return result;
        } catch (ConcurrentConflictException ex) {
            return resolveAfterConcurrentConflict(userId, activityId, requestId);
        }
    }

    private RegisterResult resolveAfterConcurrentConflict(long userId, long activityId, String requestId) {
        for (int i = 0; i < 3; i++) {
            var byRequest = registrationRepository.findByUserAndRequest(userId, requestId);
            if (byRequest.isPresent()) {
                Registration existing = byRequest.get();
                if (!existing.getActivityId().equals(activityId)) {
                    throw new BusinessException(409, "IDEMPOTENCY_CONFLICT", "同一请求键已绑定其他活动");
                }
                return new RegisterResult(existing, false);
            }
            var byActivity = registrationRepository.findByUserAndActivity(userId, activityId);
            if (byActivity.isPresent()) {
                throw new BusinessException(409, "ALREADY_REGISTERED", "同一用户对该活动已报名");
            }
            try {
                Thread.sleep(20L * (i + 1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new BusinessException(409, "ALREADY_REGISTERED", "报名冲突，请稍后重试");
    }

    private RegisterResult registerInTransaction(long userId, long activityId, String requestId) {
        // 1) 已成功绑定同一 requestId：重放或参数冲突（优先于满额/关闭）
        var byRequest = registrationRepository.findByUserAndRequest(userId, requestId);
        if (byRequest.isPresent()) {
            Registration existing = byRequest.get();
            if (!existing.getActivityId().equals(activityId)) {
                throw new BusinessException(409, "IDEMPOTENCY_CONFLICT",
                        "同一请求键已绑定其他活动");
            }
            return new RegisterResult(existing, false);
        }

        // 2) 同一用户同一活动已报名：换键重复
        var byActivity = registrationRepository.findByUserAndActivity(userId, activityId);
        if (byActivity.isPresent()) {
            throw new BusinessException(409, "ALREADY_REGISTERED", "同一用户对该活动已报名");
        }

        // 3) 活动存在性 + 行锁 + 状态/名额判定
        Activity activity = activityRepository.findByIdForUpdate(activityId)
                .orElseThrow(() -> new BusinessException(404, "ACTIVITY_NOT_FOUND", "活动不存在"));

        if ("CLOSED".equalsIgnoreCase(activity.getStatus())) {
            throw new BusinessException(409, "ACTIVITY_CLOSED", "活动已关闭");
        }
        if (activity.getRemainingQuota() == null || activity.getRemainingQuota() <= 0) {
            throw new BusinessException(409, "SOLD_OUT", "名额已满");
        }

        int updated = activityRepository.decreaseRemaining(activityId);
        if (updated != 1) {
            throw new BusinessException(409, "SOLD_OUT", "名额已满");
        }

        Registration reg = new Registration();
        reg.setId("r_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        reg.setActivityId(activityId);
        reg.setUserId(userId);
        reg.setRequestId(requestId);
        reg.setStatus("REGISTERED");

        try {
            registrationRepository.insert(reg);
        } catch (DataIntegrityViolationException ex) {
            // 必须回滚本事务（含已扣名额）；事务外再解释，避免 RR 快照读不到对手已提交行
            throw new ConcurrentConflictException();
        }

        return new RegisterResult(reg, true);
    }

    /** 唯一键并发冲突：触发事务回滚后由外层解释 */
    static class ConcurrentConflictException extends RuntimeException {
        ConcurrentConflictException() {
            super("concurrent registration conflict");
        }
    }

    public Map<String, Object> listMine(long userId, int page, int size) {
        ActivityService.validatePage(page, size);
        long total = registrationRepository.countByUser(userId);
        int offset = (page - 1) * size;
        List<Map<String, Object>> items = registrationRepository.findPageByUser(userId, offset, size)
                .stream().map(this::toView).collect(Collectors.toList());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    public Map<String, Object> getMine(long userId, String id) {
        Registration reg = registrationRepository.findByIdAndUser(id, userId)
                .orElseThrow(() -> new BusinessException(404, "REGISTRATION_NOT_FOUND", "报名记录不存在"));
        return toView(reg);
    }

    public Map<String, Object> toView(Registration r) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", r.getId());
        view.put("activityId", r.getActivityId());
        view.put("userId", r.getUserId());
        view.put("requestId", r.getRequestId());
        view.put("status", r.getStatus());
        return view;
    }

    private void validateRequestId(String requestId) {
        if (requestId == null || !REQUEST_ID.matcher(requestId).matches()) {
            throw new BusinessException(400, "INVALID_ARGUMENT",
                    "requestId 须匹配 [A-Za-z0-9_-]{1,64}");
        }
    }
}
