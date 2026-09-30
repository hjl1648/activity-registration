package com.exam.activity.web.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ApiResponses {
    private ApiResponses() {}

    public static Map<String, Object> data(Object value) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", value);
        return body;
    }

    public static Map<String, Object> page(List<?> items, long total, int page, int size) {
        Map<String, Object> pageData = new LinkedHashMap<>();
        pageData.put("items", items);
        pageData.put("total", total);
        pageData.put("page", page);
        pageData.put("size", size);
        return data(pageData);
    }
}
