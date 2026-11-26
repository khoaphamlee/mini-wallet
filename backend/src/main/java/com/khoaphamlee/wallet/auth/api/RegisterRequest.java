package com.khoaphamlee.wallet.auth.api;

import com.khoaphamlee.wallet.auth.domain.PhoneNumber;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/auth/register}. Invalid fields return 400 {@code VALIDATION_ERROR}.
 *
 * @param password 8 to 72 characters with at least one letter and one digit (BR-12). BCrypt only
 *     uses the first 72 bytes, so longer passwords are refused instead of silently cut.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,

        @NotBlank @Pattern(regexp = PhoneNumber.PATTERN, message = "must be a Vietnamese mobile number")
        String phone,

        @NotBlank @Size(max = 100) String fullName,

        @NotBlank
        @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain a letter and a digit")
        String password) {

    /** Records print every field by default; never let the password end up in a log. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", phone=" + phone + ", fullName=" + fullName + ", password=***]";
    }
}
