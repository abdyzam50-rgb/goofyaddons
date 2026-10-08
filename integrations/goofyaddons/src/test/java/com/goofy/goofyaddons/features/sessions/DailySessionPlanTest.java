package com.goofy.goofyaddons.features.sessions;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DailySessionPlanTest {
    private RestScheduleSettings settings(String a,String b,String c,String d) {
        var s=new RestScheduleSettings();s.windows=List.of(new RestScheduleSettings.Window(a,b,c,d));return s;
    }
    @Test void dailyDrawsStayInClockRangesAndSurviveReconstructionWithoutChangingEachTick() {
        var s=new RestScheduleSettings();LocalDate date=LocalDate.of(2026,10,5);
        var first=DailySessionPlan.day(s,123,date);assertEquals(first,DailySessionPlan.day(new RestScheduleSettings(),123,date));
        for(int seed=0;seed<200;seed++)for(var w:DailySessionPlan.day(s,seed,date)) {
            int start=w.login().atZone(s.zone()).getHour()*60+w.login().atZone(s.zone()).getMinute();
            int end=w.logout().atZone(s.zone()).getHour()*60+w.logout().atZone(s.zone()).getMinute();
            assertTrue(start>=480 && start<=540 || start>=960 && start<=1020);
            assertTrue(end>=720 && end<=780 || end>=1320 && end<=1380);
            assertTrue(w.logout().isAfter(w.login()));
        }
        var draws=new java.util.HashSet<LocalTime>();
        for(int offset=0;offset<30;offset++)draws.add(DailySessionPlan.day(s,123,date.plusDays(offset)).getFirst().login().atZone(s.zone()).toLocalTime());
        assertTrue(draws.size()>5,"Daily login times actually vary rather than using a fixed offset");
        assertNotEquals(first,DailySessionPlan.day(s,123,date.plusDays(1)));
        Instant start=first.getFirst().login();assertTrue(DailySessionPlan.at(s,123,start).online());
        assertEquals(first.getFirst().logout(),DailySessionPlan.at(s,123,start.plusSeconds(20)).nextChange());
        assertFalse(DailySessionPlan.at(s,123,first.getFirst().logout()).online());
    }
    @Test void overnightSessionsUsePreviousDaysDrawAndNextLocalDayLogout() {
        var s=settings("22:00","22:00","02:00","02:00");LocalDate date=LocalDate.of(2026,10,5);
        var p=DailySessionPlan.day(s,3,date).getFirst();assertEquals(date.plusDays(1),p.logout().atZone(s.zone()).toLocalDate());
        assertTrue(DailySessionPlan.at(s,3,date.plusDays(1).atTime(1,0).atZone(s.zone()).toInstant()).online());
        var rest=DailySessionPlan.at(s,3,p.logout());assertFalse(rest.online());
        assertEquals(date.plusDays(1).atTime(22,0).atZone(s.zone()).toInstant(),rest.nextChange());
    }
    @Test void regionalClockTracksDaylightSavingAndShortEasternNamesUseRegionalTime() {
        var s=settings("08:00","08:00","12:00","12:00");s.timeZone="EST";assertEquals(ZoneId.of("America/New_York"),s.zone());
        assertEquals(13,DailySessionPlan.day(s,1,LocalDate.of(2026,1,5)).getFirst().login().atZone(ZoneOffset.UTC).getHour());
        assertEquals(12,DailySessionPlan.day(s,1,LocalDate.of(2026,7,5)).getFirst().login().atZone(ZoneOffset.UTC).getHour());
        var spring=settings("01:00","01:00","04:00","04:00");
        var springSession=DailySessionPlan.day(spring,1,LocalDate.of(2026,3,8)).getFirst();assertEquals(2,Duration.between(springSession.login(),springSession.logout()).toHours());
        var autumnSession=DailySessionPlan.day(spring,1,LocalDate.of(2026,11,1)).getFirst();assertEquals(4,Duration.between(autumnSession.login(),autumnSession.logout()).toHours());
    }
    @Test void missingSpringHourUsesFirstValidTimeAndCollapsedWindowIsSkipped() {
        var gap=settings("02:10","02:20","03:10","03:20");
        var p=DailySessionPlan.day(gap,1,LocalDate.of(2026,3,8)).getFirst();assertEquals(LocalTime.of(3,0),p.login().atZone(gap.zone()).toLocalTime());
        var collapsed=settings("02:10","02:20","02:30","02:40");assertTrue(DailySessionPlan.day(collapsed,1,LocalDate.of(2026,3,8)).isEmpty());
        assertFalse(DailySessionPlan.at(collapsed,1,Instant.parse("2026-03-08T08:00:00Z")).online());
    }
    @Test void rejectAmbiguousOverlappingAndMalformedSchedulesIncludingOvernightOverlap() {
        assertThrows(IllegalArgumentException.class,()->settings("8:00","09:00","12:00","13:00").validate());
        assertThrows(IllegalArgumentException.class,()->settings("08:00","09:00","08:30","10:00").validate());
        assertThrows(IllegalArgumentException.class,()->settings("23:00","01:00","03:00","04:00").validate());
        var s=settings("22:00","23:00","02:00","03:00");s.windows=List.of(s.windows.getFirst(),new RestScheduleSettings.Window("01:00","01:30","04:00","05:00"));
        assertThrows(IllegalArgumentException.class,s::validate);
        s=new RestScheduleSettings();s.timeZone="Made/Up";assertThrows(IllegalArgumentException.class,s::validate);
        s=new RestScheduleSettings();s.resumeCommand="/is";assertThrows(IllegalArgumentException.class,s::validate);
    }
}
