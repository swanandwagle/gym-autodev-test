package com.studio.booking.catalog.domain;

public enum DayOfWeek {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY;

    public java.time.DayOfWeek toJavaTime() {
        return java.time.DayOfWeek.of(ordinal() + 1);
    }

    public static DayOfWeek fromJavaTime(java.time.DayOfWeek dow) {
        return values()[dow.getValue() - 1];
    }
}
