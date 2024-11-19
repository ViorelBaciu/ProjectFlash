package net.xqhs.flash.core.testVio;



import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;



public class EntityScheduler {

    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor (1);

    public void scheduleEntity(Runnable task, long delay, TimeUnit unit) {
        scheduler.schedule(() -> {
            try {
                task.run();
            } catch (Exception e) {
                System.err.println("Failed to load entities: " + e.getMessage());
            } finally {
                scheduler.shutdown();
            }
        }, delay, unit);

    }
}
