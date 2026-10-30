package com.futspring.backend;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(VirtualThreadsTest.Probes.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class VirtualThreadsTest {

    @Autowired private TestRestTemplate http;
    @Autowired private AsyncProbe asyncProbe;
    @Autowired private ScheduledProbe scheduledProbe;
    @Autowired private HikariDataSource dataSource;
    @Autowired @Qualifier("applicationTaskExecutor") private AsyncTaskExecutor asyncExecutor;
    @Autowired @Qualifier("taskScheduler") private TaskScheduler scheduler;
    @Autowired @Qualifier("clientInboundChannelExecutor") private Executor inbound;
    @Autowired @Qualifier("clientOutboundChannelExecutor") private Executor outbound;

    @Test
    void httpRequest_runsOnAVirtualThread() {
        // A real socket request: MockMvc executes on the test thread and cannot verify Tomcat's executor.
        var response = http.getForEntity("/api/v1/files/__thread-probe", Boolean.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isTrue();
    }

    @Test
    void asyncProxy_usesBootsVirtualThreadExecutor() throws Exception {
        assertThat(asyncExecutor).isInstanceOf(SimpleAsyncTaskExecutor.class);
        assertThat(((SimpleAsyncTaskExecutor) asyncExecutor).getConcurrencyLimit())
                .isEqualTo(SimpleAsyncTaskExecutor.UNBOUNDED_CONCURRENCY);
        assertThat(asyncProbe.isVirtual().get(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void scheduledTask_usesBootsVirtualThreadScheduler() throws Exception {
        assertThat(scheduler).isInstanceOf(SimpleAsyncTaskScheduler.class);
        assertThat(scheduledProbe.result.get(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void stompChannels_useBootsApplicationExecutorOnVirtualThreads() throws Exception {
        for (Executor executor : new Executor[] {inbound, outbound}) {
            assertThat(executor).isSameAs(asyncExecutor);
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            executor.execute(() -> result.complete(Thread.currentThread().isVirtual()));
            assertThat(result.get(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void hikari_keepsTenConnectionsAndAFiveSecondTimeout() {
        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(10);
        assertThat(dataSource.getConnectionTimeout()).isEqualTo(5000);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Probes {
        @Bean
        HttpProbe httpProbe() {
            return new HttpProbe();
        }

        @Bean
        AsyncProbe asyncProbe() {
            return new AsyncProbe();
        }

        @Bean
        ScheduledProbe scheduledProbe() {
            return new ScheduledProbe();
        }
    }

    // Imported exclusively by this test; never exposes a production endpoint or replaces an executor bean.
    @RestController
    @TestComponent
    static class HttpProbe {
        @GetMapping("/api/v1/files/__thread-probe")
        boolean isVirtual() {
            return Thread.currentThread().isVirtual();
        }
    }

    static class AsyncProbe {
        @Async
        public CompletableFuture<Boolean> isVirtual() {
            return CompletableFuture.completedFuture(Thread.currentThread().isVirtual());
        }
    }

    static class ScheduledProbe {
        private final CompletableFuture<Boolean> result = new CompletableFuture<>();

        @Scheduled(initialDelay = 0)
        public void recordThread() {
            result.complete(Thread.currentThread().isVirtual());
        }
    }
}
