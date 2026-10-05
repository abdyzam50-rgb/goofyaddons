package com.goofy.goofyaddons.features.sessions;

import java.time.*;
import java.util.List;
import java.util.Map;

/** Local clock ranges, not elapsed hours. Disabled for existing installations. */
public final class RestScheduleSettings {
    public boolean enabled = false;
    public String timeZone = "America/New_York";
    public List<Window> windows = List.of(
            new Window("08:00", "09:00", "12:00", "13:00"),
            new Window("16:00", "17:00", "22:00", "23:00"));
    public int reconnectAttempts = 3;
    public int reconnectDelaySeconds = 60;
    public int connectionTimeoutSeconds = 90;
    public int transactionWaitSeconds = 180;
    public String joinCommand = "skyblock";
    public String resumeCommand = "is";

    public static final class Window {
        public String loginFrom, loginUntil, logoutFrom, logoutUntil;
        public Window() {}
        public Window(String a,String b,String c,String d) {loginFrom=a;loginUntil=b;logoutFrom=c;logoutUntil=d;}
    }
    public ZoneId zone() {
        if(timeZone == null) throw new IllegalArgumentException("restSchedule.timeZone is required");
        String name=Map.of("EST","America/New_York","EDT","America/New_York","CST","America/Chicago",
                "CDT","America/Chicago","MST","America/Denver","MDT","America/Denver",
                "PST","America/Los_Angeles","PDT","America/Los_Angeles").getOrDefault(timeZone.toUpperCase(java.util.Locale.ROOT),timeZone);
        try {return ZoneId.of(name);} catch(DateTimeException error) {
            throw new IllegalArgumentException("Unknown restSchedule.timeZone; use a region such as America/New_York");
        }
    }
    static int minute(String s) {
        if(s==null || !s.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))
            throw new IllegalArgumentException("Schedule times must use 24-hour HH:mm");
        return LocalTime.parse(s).getHour()*60+LocalTime.parse(s).getMinute();
    }
    static int[] bounds(Window w) {
        if(w==null)throw new IllegalArgumentException("Schedule window cannot be null");
        int a=minute(w.loginFrom),b=minute(w.loginUntil),c=minute(w.logoutFrom),d=minute(w.logoutUntil);
        if(b<a || d<c)throw new IllegalArgumentException("Individual login/logout ranges cannot cross midnight");
        if(d<=a){c+=1440;d+=1440;} // The session may end on the following local day.
        if(c<=b || d-a>=1440)throw new IllegalArgumentException("Logout range must follow the entire login range within one day");
        return new int[]{a,b,c,d};
    }
    public void validate() {
        zone();
        if(windows==null || windows.isEmpty() || windows.size()>4)throw new IllegalArgumentException("Use one to four schedule windows");
        if(reconnectAttempts<1 || reconnectAttempts>5 || reconnectDelaySeconds<30 || reconnectDelaySeconds>600
                || connectionTimeoutSeconds<30 || connectionTimeoutSeconds>300 || transactionWaitSeconds<30 || transactionWaitSeconds>600)
            throw new IllegalArgumentException("Invalid bounded schedule retry/timeout settings");
        for(String command:List.of(joinCommand==null?"":joinCommand,resumeCommand==null?"":resumeCommand))
            if(!command.matches("[A-Za-z][A-Za-z0-9 ]{0,63}"))throw new IllegalArgumentException("Schedule commands must omit / and contain letters, digits and spaces");
        for(int i=0;i<windows.size();i++) {
            int[] a=bounds(windows.get(i));
            for(int j=0;j<i;j++) {
                int[] b=bounds(windows.get(j));
                for(int shift:new int[]{-1440,0,1440})if(a[0]<=b[3]+shift && b[0]+shift<=a[3])
                    throw new IllegalArgumentException("Schedule windows overlap, including overnight ranges");
            }
        }
    }
    public String fingerprint() {
        StringBuilder s=new StringBuilder(zone().getId());
        for(Window w:windows)s.append('|').append(w.loginFrom).append('-').append(w.loginUntil).append(':').append(w.logoutFrom).append('-').append(w.logoutUntil);
        return s.append('|').append(reconnectAttempts).append('|').append(reconnectDelaySeconds).append('|').append(connectionTimeoutSeconds)
                .append('|').append(transactionWaitSeconds).append('|').append(joinCommand).append('|').append(resumeCommand).toString();
    }
}
