package com.exam.activity.service;

import com.exam.activity.cache.ActivityCache;
import com.exam.activity.domain.Activity;
import com.exam.activity.repo.ActivityRepository;
import com.exam.activity.web.error.BusinessException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final ActivityCache activityCache;

    public ActivityService(ActivityRepository activityRepository, ActivityCache activityCache) {
        this.activityRepository = activityRepository;
        this.activityCache = activityCache;
    }

    public Map<String, Object> list(int page, int size) {
        validatePage(page, size);
        long total = activityRepository.countAll();
        int offset = (page - 1) * size;
        List<Map<String, Object>> items = activityRepository.findPage(offset, size).stream()
                .map(this::toView)
                .collect(Collectors.toList());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    public Map<String, Object> detail(long id) {
        // 基础信息优先走 Redis；剩余名额始终读 MySQL
        ActivityCache.CachedActivityBase cached = activityCache.get(id).orElse(null);
        Activity db = activityRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "ACTIVITY_NOT_FOUND", "活动不存在"));

        if (cached == null) {
            activityCache.put(db);
            return toView(db);
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", db.getId());
        view.put("title", cached.getTitle() != null ? cached.getTitle() : db.getTitle());
        view.put("status", cached.getStatus() != null ? cached.getStatus() : db.getStatus());
        view.put("totalQuota", cached.getTotalQuota() != null ? cached.getTotalQuota() : db.getTotalQuota());
        view.put("remainingQuota", db.getRemainingQuota());
        return view;
    }

    public static void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(400, "INVALID_ARGUMENT", "分页参数非法：page>=1 且 1<=size<=100");
        }
    }

    private Map<String, Object> toView(Activity a) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", a.getId());
        view.put("title", a.getTitle());
        view.put("status", a.getStatus());
        view.put("totalQuota", a.getTotalQuota());
        view.put("remainingQuota", a.getRemainingQuota());
        return view;
    }
}
