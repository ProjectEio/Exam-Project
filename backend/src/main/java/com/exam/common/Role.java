package com.exam.common;

public enum Role {
    ADMIN("管理员"),
    TEACHER("教师"),
    STUDENT("考生");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static Role of(String name) {
        if (name == null) return null;
        try {
            return Role.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
}
