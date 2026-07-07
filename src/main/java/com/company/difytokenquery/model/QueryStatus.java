package com.company.difytokenquery.model;

public enum QueryStatus {
    SUCCESS("成功"),
    NOT_FOUND("未找到"),
    DB_ERROR("数据库错误"),
    INVALID_TOKEN("Token无效");

    private final String label;

    QueryStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
