package com.petcare.grooming;

import com.petcare.grooming.booking.SchedulingRules;
import org.junit.jupiter.api.Test;
import java.time.LocalTime;
import static org.junit.jupiter.api.Assertions.*;

class SchedulingRulesTest {
    @Test void cleaningBufferBlocksAdjacentBookings() {
        assertTrue(SchedulingRules.conflicts(LocalTime.of(10,0), LocalTime.of(11,0), LocalTime.of(11,10), LocalTime.of(12,0), 15));
        assertFalse(SchedulingRules.conflicts(LocalTime.of(10,0), LocalTime.of(11,0), LocalTime.of(11,15), LocalTime.of(12,0), 15));
    }
    @Test void overlapIsSymmetricAndEarlierBookingsNeedBufferToo() {
        assertTrue(SchedulingRules.conflicts(LocalTime.of(11,10), LocalTime.of(12,0), LocalTime.of(10,0), LocalTime.of(11,0), 15));
        assertFalse(SchedulingRules.conflicts(LocalTime.of(11,15), LocalTime.of(12,0), LocalTime.of(10,0), LocalTime.of(11,0), 15));
    }
}
