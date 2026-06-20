package com.restprovider.domain.security;

public class EnvPasscodeValidator implements PasscodeValidator {
    private final String expectedPasscode;

    public EnvPasscodeValidator() {
        this.expectedPasscode = System.getenv("RESTPROVIDER_PASSCODE");
    }

    @Override
    public boolean isValid(String passCode) {
        if (passCode == null || passCode.isBlank()) {
            return false;
        }
        if (expectedPasscode == null || expectedPasscode.isBlank()) {
            return true;
        }
        return expectedPasscode.equals(passCode);
    }
}
