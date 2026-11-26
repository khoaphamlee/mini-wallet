package com.khoaphamlee.wallet.auth.application;

import static com.khoaphamlee.wallet.auth.AuthTestData.randomEmail;
import static com.khoaphamlee.wallet.auth.AuthTestData.randomPhone;
import static org.assertj.core.api.Assertions.assertThat;

import com.khoaphamlee.wallet.TestcontainersConfiguration;
import com.khoaphamlee.wallet.auth.domain.User;
import com.khoaphamlee.wallet.common.error.BusinessException;
import com.khoaphamlee.wallet.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
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
class TokenRefreshServiceTests {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private LoginService loginService;

    @Autowired
    private TokenRefreshService tokenRefreshService;

    /** Two refresh calls with the same token at the same moment: exactly one may get a new pair. */
    @Test
    void concurrentRefreshWithTheSameTokenSucceedsOnlyOnce() throws Exception {
        User user = registrationService.register(randomEmail(), randomPhone(), "Racer", "Password1");
        String refreshToken = loginService.login(user.getEmail(), "Password1").refreshToken();

        CountDownLatch startTogether = new CountDownLatch(1);
        List<Future<LoginResult>> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                results.add(executor.submit(() -> {
                    startTogether.await();
                    return tokenRefreshService.refresh(refreshToken);
                }));
            }
            startTogether.countDown();

            int succeeded = 0;
            int rejected = 0;
            for (Future<LoginResult> result : results) {
                try {
                    result.get();
                    succeeded++;
                } catch (ExecutionException ex) {
                    assertThat(ex.getCause()).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) ex.getCause()).errorCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
                    rejected++;
                }
            }
            assertThat(succeeded).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
        }
    }
}
