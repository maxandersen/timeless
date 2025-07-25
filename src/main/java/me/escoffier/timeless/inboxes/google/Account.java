package me.escoffier.timeless.inboxes.google;

import com.google.api.services.calendar.Calendar;
import com.google.api.services.drive.Drive;
import com.google.api.services.gmail.Gmail;

public class Account {

    private final String name;
    private final Gmail gmail;
    private final Calendar calendar;
    private final Drive drive;
    private final String email;
    private final int inboxid;

    Account(String name, String email, Gmail gmail, Calendar calendar, Drive drive, int inboxid) {
        this.name = name;
        this.email = email;
        this.gmail = gmail;
        this.calendar = calendar;
        this.drive = drive;
        this.inboxid = inboxid;
    }

    public String name() {
        return name;
    }

    public Gmail gmail() {
        return gmail;
    }

    public Calendar calendar() {
        return calendar;
    }

    public Drive drive() {
        return drive;
    }

    public String email() {
        return email;
    }

    public int inboxid() {
        return inboxid;
    }
}
