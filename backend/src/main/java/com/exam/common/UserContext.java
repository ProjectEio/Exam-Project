package com.exam.common;

public final class UserContext {

    private static final ThreadLocal<LoginUser> CURRENT = new ThreadLocal<>();

    private UserContext() {}

    public static void set(LoginUser user) {
        CURRENT.set(user);
    }

    public static LoginUser get() {
        return CURRENT.get();
    }

    public static Long userId() {
        LoginUser u = CURRENT.get();
        if (u == null) throw new BizException(401, "未登录");
        return u.getUserId();
    }

    public static String role() {
        LoginUser u = CURRENT.get();
        return u == null ? null : u.getRole();
    }

    public static boolean isAdmin() {
        return Role.ADMIN.name().equals(role());
    }

    public static boolean isTeacher() {
        return Role.TEACHER.name().equals(role());
    }

    public static boolean isStudent() {
        return Role.STUDENT.name().equals(role());
    }

    public static void clear() {
        CURRENT.remove();
    }
}
