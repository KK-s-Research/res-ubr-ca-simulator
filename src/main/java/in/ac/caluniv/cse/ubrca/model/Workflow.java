package in.ac.caluniv.cse.ubrca.model;

import java.util.ArrayList;
import java.util.List;

public final class Workflow {
    public final int id;
    public final String benchmark;
    public final double arrivalTime;
    public final double deadline;
    public final List<Task> tasks;

    public Workflow(int id, String benchmark, double arrivalTime,
                    double deadline, List<Task> tasks) {
        this.id = id;
        this.benchmark = benchmark;
        this.arrivalTime = arrivalTime;
        this.deadline = deadline;
        this.tasks = List.copyOf(tasks);
    }

    public Workflow copy(double priorVariance) {
        List<Task> cloned = new ArrayList<>(tasks.size());
        for (Task task : tasks) cloned.add(task.copy(priorVariance));
        return new Workflow(id, benchmark, arrivalTime, deadline, cloned);
    }

    public boolean finished() {
        return tasks.stream().allMatch(t -> t.state == Task.State.FINISHED);
    }

    public double completionTime() {
        return tasks.stream().mapToDouble(t -> t.completionTime).max().orElse(arrivalTime);
    }
}
