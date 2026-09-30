package com.exam.activity.repo;

import com.exam.activity.domain.Activity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ActivityRepository {

    private static final RowMapper<Activity> MAPPER = (rs, rowNum) -> {
        Activity a = new Activity();
        a.setId(rs.getLong("id"));
        a.setTitle(rs.getString("title"));
        a.setStatus(rs.getString("status"));
        a.setTotalQuota(rs.getInt("total_quota"));
        a.setRemainingQuota(rs.getInt("remaining_quota"));
        return a;
    };

    private final JdbcTemplate jdbc;

    public ActivityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long countAll() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM activities", Long.class);
        return n == null ? 0L : n;
    }

    public List<Activity> findPage(int offset, int size) {
        return jdbc.query(
                "SELECT id, title, status, total_quota, remaining_quota FROM activities ORDER BY id ASC LIMIT ? OFFSET ?",
                MAPPER, size, offset);
    }

    public Optional<Activity> findById(long id) {
        List<Activity> list = jdbc.query(
                "SELECT id, title, status, total_quota, remaining_quota FROM activities WHERE id = ?",
                MAPPER, id);
        return list.stream().findFirst();
    }

    /** 行锁读取，用于报名事务 */
    public Optional<Activity> findByIdForUpdate(long id) {
        List<Activity> list = jdbc.query(
                "SELECT id, title, status, total_quota, remaining_quota FROM activities WHERE id = ? FOR UPDATE",
                MAPPER, id);
        return list.stream().findFirst();
    }

    public int decreaseRemaining(long id) {
        return jdbc.update(
                "UPDATE activities SET remaining_quota = remaining_quota - 1, updated_at = CURRENT_TIMESTAMP(3) " +
                        "WHERE id = ? AND remaining_quota > 0 AND status = 'OPEN'",
                id);
    }
}
