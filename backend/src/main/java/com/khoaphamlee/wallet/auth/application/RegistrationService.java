package com.khoaphamlee.wallet.auth.application;

import com.khoaphamlee.wallet.auth.domain.PhoneNumber;
import com.khoaphamlee.wallet.auth.domain.User;
import com.khoaphamlee.wallet.auth.infra.UserRepository;
import com.khoaphamlee.wallet.common.error.BusinessException;
import com.khoaphamlee.wallet.common.error.ErrorCode;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates customer accounts (US-01). The wallet for the new user is added in issue #7. */
@Service
public class RegistrationService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new customer.
     *
     * @throws BusinessException {@code EMAIL_ALREADY_EXISTS} or {@code PHONE_ALREADY_EXISTS}
     */
    @Transactional
    public User register(String email, String phone, String fullName, String rawPassword) {
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        String normalizedPhone = PhoneNumber.normalize(phone);

        // Fast, friendly checks for the common case.
        if (users.existsByEmail(normalizedEmail)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (users.existsByPhone(normalizedPhone)) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        User user =
                User.register(normalizedEmail, normalizedPhone, fullName.strip(), passwordEncoder.encode(rawPassword));
        try {
            // saveAndFlush sends the INSERT now, so a duplicate fails here and not at commit time.
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Two requests with the same email can both pass the checks above at the same moment.
            // The UNIQUE constraints in the database stop the second one; report it the same way.
            throw duplicateError(ex);
        }
    }

    private static BusinessException duplicateError(DataIntegrityViolationException ex) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage());
        if (message.contains("users_phone_key")) {
            return new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS);
        }
        if (message.contains("users_email_key")) {
            return new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        throw ex;
    }
}
