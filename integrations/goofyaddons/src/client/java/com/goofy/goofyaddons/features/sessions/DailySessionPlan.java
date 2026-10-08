package com.goofy.goofyaddons.features.sessions;

import java.time.*;
import java.util.*;

/** Stable daily draws from a persistent seed; day boundaries follow the selected region. */
public final class DailySessionPlan {
    public record Session(Instant login,Instant logout) {}
    public record Decision(boolean online,Instant nextChange) {}
    private DailySessionPlan() {}
    public static List<Session> day(RestScheduleSettings settings,long seed,LocalDate day) {
        settings.validate();
        var random=new SplittableRandom(seed ^ day.toEpochDay()*0x9e3779b97f4a7c15L ^ settings.fingerprint().hashCode());
        List<Session> result=new ArrayList<>();
        for(var window:settings.windows) {
            int[] b=RestScheduleSettings.bounds(window);
            Instant login=resolve(day.atStartOfDay().plusMinutes(random.nextInt(b[0],b[1]+1)),settings.zone());
            Instant logout=resolve(day.atStartOfDay().plusMinutes(random.nextInt(b[2],b[3]+1)),settings.zone());
            if(logout.isAfter(login))result.add(new Session(login,logout)); // A DST gap may collapse a short window.
        }
        result.sort(Comparator.comparing(Session::login));
        return List.copyOf(result);
    }
    private static Instant resolve(LocalDateTime local,ZoneId zone) {
        var transition=zone.getRules().getTransition(local);
        // Nonexistent spring-forward times use the first valid instant after the gap.
        if(transition!=null && transition.isGap())return transition.getInstant();
        return local.atZone(zone).toInstant(); // Consistently chooses the first occurrence during an autumn overlap.
    }
    public static Decision at(RestScheduleSettings settings,long seed,Instant now) {
        LocalDate day=now.atZone(settings.zone()).toLocalDate();
        List<Session> sessions=new ArrayList<>();
        for(int offset=-1;offset<=1;offset++)sessions.addAll(day(settings,seed,day.plusDays(offset)));
        sessions.sort(Comparator.comparing(Session::login));
        for(Session s:sessions)if(!now.isBefore(s.login()) && now.isBefore(s.logout()))return new Decision(true,s.logout());
        return new Decision(false,sessions.stream().map(Session::login).filter(t->t.isAfter(now)).min(Comparator.naturalOrder()).orElse(null));
    }
}
