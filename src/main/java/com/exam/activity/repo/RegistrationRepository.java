package com.exam.activity.repo;

import com.exam.activity.domain.Registration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class RegistrationRepository {

    private static final RowMapper<Registration> MAPPER = (rs, rowNum) -> {
        Registration r = new Registration();
        r.setId(rs.getString("id"));
        r.setActivityId(rs.getLong("activity_id"));
        r.setUserId(rs.getLong("user_id"));
        r.setRequestId(rs.getString("request_id"));
        r.setStatus(rs.getString("status"));
        r.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return r;
    };

    private final JdbcTemplate jdbc;

    public RegistrationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Registration r) {
        jdbc.update(
                "INSERT INTO registrations (id, activity_id, user_id, request_id, status, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3))",
                r.getId(), r.getActivityId(), r.getUserId(), r.getRequestId(), r.getStatus());
    }

    public Optional<Registration> findByUserAndRequest(long userId, String requestId) {
        List<Registration> list = jdbc.query(
                "SELECT id, activity_id, user_id, request_id, status, created_at " +
                        "FROM registrations WHERE user_id = ? AND request_id = ?",
                MAPPER, userId, requestId);
        return list.stream().findFirst();
    }

    public Optional<Registration> findByUserAndActivity(long userId, long activityId) {
        List<Registration> list = jdbc.query(
                "SELECT id, activity_id, user_id, request_id, status, created_at " +
                        "FROM registrations WHERE user_id = ? AND activity_id = ?",
                MAPPER, userId, activityId);
        return list.stream().findFirst();
    }

    public Optional<Registration> findByIdAndUser(String id, long userId) {
        List<Registration> list = jdbc.query(
                "SELECT id, activity_id, user_id, request_id, status, created_at " +
                        "FROM registrations WHERE id = ? AND user_id = ?",
                MAPPER, id, userId);
        return list.stream().findFirst();
    }

    public long countByUser(long userId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM registrations WHERE user_id = ?", Long.class, userId);
        return n == null ? 0L : n;
    }

    public List<Registration> findPageByUser(long userId, int offset, int size) {
        return jdbc.query(
                "SELECT id, activity_id, user_id, request_id, status, created_at " +
                        "FROM registrations WHERE user_id = ? " +
                        "ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                MAPPER, userId, size, offset);
    }
}
