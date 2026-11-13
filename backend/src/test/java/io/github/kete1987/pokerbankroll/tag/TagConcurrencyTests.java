package io.github.kete1987.pokerbankroll.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class TagConcurrencyTests extends ApiIntegrationTest {

    @Autowired
    TagService tags;

    @MockitoSpyBean
    TagRepository repository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void colorUpdateAndMaintenanceRenameOrDeleteCompleteWithoutDeadlock(boolean delete) throws Exception {
        long id = jdbc.queryForObject("insert into tag (name) values ('Series') returning id", Long.class);
        var rowLocked = new CountDownLatch(1);
        var continueColor = new CountDownLatch(1);
        var maintenanceLocked = new CountDownLatch(1);
        var firstLock = new AtomicBoolean(true);
        doAnswer(invocation -> {
            // Match the repository's real lock on the transaction-bound connection, then pause
            // before the UPDATE acquires its implicit ROW EXCLUSIVE table lock.
            jdbc.queryForObject("select id from tag where id = ? for update", Long.class, id);
            var found = repository.findById(id);
            if (firstLock.getAndSet(false)) {
                rowLocked.countDown();
                if (!continueColor.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("color latch timeout");
            }
            return found;
        }).when(repository).findForUpdateById(id);

        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var color = pool.submit(() -> tags.setColor(id, new TagColorRequest(TagColor.BLUE)));
            try {
                assertThat(rowLocked.await(15, TimeUnit.SECONDS)).isTrue();
                var maintenance = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                    // The exact table locks used by McpTagMaintenance.inspect before its write.
                    jdbc.execute("lock table tag, game_tag in share row exclusive mode");
                    maintenanceLocked.countDown();
                    if (delete) tags.delete(id);
                    else tags.rename(id, new TagRequest("Renamed"));
                    return true;
                }));
                // Let maintenance either acquire its locks (the broken order) or actually wait
                // on the color transaction's table lock (the corrected order).
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                boolean waiting = false;
                while (maintenanceLocked.getCount() != 0 && !waiting && System.nanoTime() < deadline) {
                    waiting = jdbc.queryForObject("""
                            select count(*) > 0 from pg_stat_activity
                            where datname = current_database() and wait_event_type = 'Lock'
                              and lower(query) like 'lock table tag, game_tag%'
                            """, Boolean.class);
                    if (!waiting) Thread.sleep(25);
                }
                assertThat(waiting || maintenanceLocked.getCount() == 0).isTrue();
                continueColor.countDown();
                assertThat(color.get(30, TimeUnit.SECONDS).color()).isEqualTo(TagColor.BLUE);
                assertThat(maintenance.get(30, TimeUnit.SECONDS)).isTrue();
            } finally {
                continueColor.countDown();
            }
        }
        if (delete) {
            assertThat(repository.findById(id)).isEmpty();
        } else {
            assertThat(tags.get(id)).isEqualTo(new TagResponse(id, "Renamed", 0, TagColor.BLUE));
        }
    }
}
