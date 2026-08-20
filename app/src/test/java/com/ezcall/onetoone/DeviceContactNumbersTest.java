package com.ezcall.onetoone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DeviceContactNumbersTest {
    @Test
    public void containsMatchesExactNormalizedNumber() {
        Set<String> deviceNumbers = numbers("919620500072");

        assertTrue(DeviceContactNumbers.contains(deviceNumbers, "+91 96205 00072"));
    }

    @Test
    public void containsMatchesLocalNumberWithoutCountryCode() {
        Set<String> deviceNumbers = numbers("9620500072");

        assertTrue(DeviceContactNumbers.contains(deviceNumbers, "+91 96205 00072"));
    }

    @Test
    public void containsRejectsDifferentPhoneNumber() {
        Set<String> deviceNumbers = numbers("9620500072");

        assertFalse(DeviceContactNumbers.contains(deviceNumbers, "+91 96205 00073"));
    }

    @Test
    public void containsDoesNotSuffixMatchShortNumbers() {
        Set<String> deviceNumbers = numbers("5551234");

        assertFalse(DeviceContactNumbers.contains(deviceNumbers, "+1 555 1234"));
    }

    @Test
    public void displayNamePrefersExactSavedContactName() {
        Map<String, String> contactNames = names("919620500072", "Dr. Rao");

        assertEquals(
                "Dr. Rao",
                DeviceContactNumbers.displayName(
                        contactNames,
                        "+91 96205 00072",
                        "Rao Profile"
                )
        );
    }

    @Test
    public void displayNameMatchesLocalNumberWithoutCountryCode() {
        Map<String, String> contactNames = names("9620500072", "Family Doctor");

        assertEquals(
                "Family Doctor",
                DeviceContactNumbers.displayName(
                        contactNames,
                        "+91 96205 00072",
                        "Rao Profile"
                )
        );
    }

    @Test
    public void displayNameFallsBackToProfileName() {
        Map<String, String> contactNames = names("9620500072", "Family Doctor");

        assertEquals(
                "Rao Profile",
                DeviceContactNumbers.displayName(
                        contactNames,
                        "+91 96205 00073",
                        "Rao Profile"
                )
        );
    }

    private Set<String> numbers(String... values) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            result.add(value);
        }
        return result;
    }

    private Map<String, String> names(String phoneNumber, String displayName) {
        Map<String, String> result = new HashMap<>();
        result.put(phoneNumber, displayName);
        return result;
    }
}
