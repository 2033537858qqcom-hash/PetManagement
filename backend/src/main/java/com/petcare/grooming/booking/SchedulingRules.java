package com.petcare.grooming.booking;
import java.time.LocalTime;
public final class SchedulingRules {
    private SchedulingRules() {}
    public static boolean conflicts(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd, int bufferMinutes) {
        return aStart.isBefore(bEnd.plusMinutes(bufferMinutes)) && bStart.isBefore(aEnd.plusMinutes(bufferMinutes));
    }
}
