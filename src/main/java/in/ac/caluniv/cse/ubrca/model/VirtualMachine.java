package in.ac.caluniv.cse.ubrca.model;

import java.util.ArrayList;
import java.util.List;

public final class VirtualMachine {
    public record Type(String name, double baseline, double capacity,
                       double pricePerHour, double initialCredits,
                       double maximumCredits, double accrualPerMinute) {}

    public final int id;
    public final Type type;
    public final List<Task> tasks = new ArrayList<>();
    public double credits;
    public double activeSeconds;
    public boolean exhaustedLastInterval;
    public double lastMigrationTime = Double.NEGATIVE_INFINITY;
    public boolean retired;
    public int idleIntervals;

    public VirtualMachine(int id, Type type) {
        this.id = id;
        this.type = type;
        this.credits = type.initialCredits();
    }

    public boolean active() {
        return !retired;
    }
}
