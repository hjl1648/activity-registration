package com.exam.activity.domain;

public class Activity {
    private Long id;
    private String title;
    private String status;
    private Integer totalQuota;
    private Integer remainingQuota;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTotalQuota() { return totalQuota; }
    public void setTotalQuota(Integer totalQuota) { this.totalQuota = totalQuota; }
    public Integer getRemainingQuota() { return remainingQuota; }
    public void setRemainingQuota(Integer remainingQuota) { this.remainingQuota = remainingQuota; }
}
