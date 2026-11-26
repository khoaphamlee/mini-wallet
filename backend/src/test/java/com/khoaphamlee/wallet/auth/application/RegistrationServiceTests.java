package com.khoaphamlee.wallet.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.khoaphamlee.wallet.TestcontainersConfiguration;
import com.khoaphamlee.wallet.common.error.BusinessException;
import com.khoaphamlee.wallet.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RegistrationServiceTests {

    @Autowired
    private RegistrationService registrationService;

    /**
     * Two registrations with the same email start at the same moment. Both may pass the
     * {@code existsByEmail} check; the UNIQUE constraint must still let exactly one through.
     */
    @Test
    void concurrentRegistrationsWithSameEmailCreateOnlyOneUser() throws Exception {
        String email = "race-" + UUID.randomUUID() + "@example.com";
        CountDownLatch startTogether = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (String phone : List.of("0911111111", "0922222222")) {
                String uniquePhone = phone.substring(0, 4) + String.format("%06d", (int) (Math.random() * 1_000_000));
                results.add(executor.submit(() -> {
                    startTogether.await();
                    return registrationService.register(email, uniquePhone, "Racer", "Password1");
                }));
            }
            startTogether.countDown();

            int created = 0;
            int rejected = 0;
            for (Future<?> result : results) {
                try {
                    result.get();
                    created++;
                } catch (ExecutionException ex) {
                    assertThat(ex.getCause()).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) ex.getCause()).errorCode())
                            .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
                    rejected++;
                }
            }
            assertThat(created).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
        }
    }
}
