-- 重置到固定初始状态（清空报名、恢复名额）
USE activity_reg;

DELETE FROM registrations;

UPDATE activities SET remaining_quota = total_quota,
                     status = CASE id WHEN 1004 THEN 'CLOSED' ELSE 'OPEN' END,
                     updated_at = CURRENT_TIMESTAMP(3);
