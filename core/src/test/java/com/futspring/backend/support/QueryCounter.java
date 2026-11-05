package com.futspring.backend.support;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Component;

// Counts what a block sends to the database through Hibernate Statistics (test config only).
// Compare a small and a large batch instead of asserting absolute numbers; statistics are never cleared,
// so other tests are not affected.
@Component
public class QueryCounter {

    private final Statistics statistics;

    public QueryCounter(EntityManagerFactory entityManagerFactory) {
        this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    // JPQL/derived queries + entity loads + lazy entity/collection fetches
    public long reads(Runnable block) {
        long before = readCount();
        block.run();
        return readCount() - before;
    }

    // Every prepared statement, inserts included
    public long statements(Runnable block) {
        long before = statistics.getPrepareStatementCount();
        block.run();
        return statistics.getPrepareStatementCount() - before;
    }

    private long readCount() {
        return statistics.getQueryExecutionCount() + statistics.getEntityLoadCount()
                + statistics.getEntityFetchCount() + statistics.getCollectionFetchCount();
    }
}
