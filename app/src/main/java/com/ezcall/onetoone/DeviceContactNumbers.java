package com.ezcall.onetoone;

import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.ContactsContract;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class DeviceContactNumbers {
    private DeviceContactNumbers() {
    }

    static Set<String> read(ContentResolver contentResolver) {
        return readSnapshot(contentResolver).phoneNumbers;
    }

    static Snapshot readSnapshot(ContentResolver contentResolver) {
        Set<String> phoneNumbers = new HashSet<>();
        Map<String, String> displayNamesByNumber = new HashMap<>();
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY
        };

        try (Cursor cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null
        )) {
            if (cursor == null) {
                return Snapshot.empty();
            }

            int numberColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            int normalizedColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER);
            int nameColumn = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY
            );
            while (cursor.moveToNext()) {
                String displayName = valueAt(cursor, nameColumn).trim();
                addContact(
                        phoneNumbers,
                        displayNamesByNumber,
                        valueAt(cursor, normalizedColumn),
                        displayName
                );
                addContact(
                        phoneNumbers,
                        displayNamesByNumber,
                        valueAt(cursor, numberColumn),
                        displayName
                );
            }
        } catch (SecurityException error) {
            return Snapshot.empty();
        }
        return new Snapshot(phoneNumbers, displayNamesByNumber);
    }

    static boolean contains(Set<String> deviceNumbers, String registeredNumber) {
        String normalizedRegistered = FirebaseCallRepository.normalizePhoneNumber(registeredNumber);
        if (normalizedRegistered.isEmpty() || deviceNumbers.isEmpty()) {
            return false;
        }
        if (deviceNumbers.contains(normalizedRegistered)) {
            return true;
        }

        String registeredSuffix = lastTenDigits(normalizedRegistered);
        if (registeredSuffix.isEmpty()) {
            return false;
        }
        for (String deviceNumber : deviceNumbers) {
            if (registeredSuffix.equals(lastTenDigits(deviceNumber))) {
                return true;
            }
        }
        return false;
    }

    static String displayName(
            Map<String, String> displayNamesByNumber,
            String registeredNumber,
            String fallback
    ) {
        String normalizedRegistered = FirebaseCallRepository.normalizePhoneNumber(registeredNumber);
        if (!normalizedRegistered.isEmpty() && displayNamesByNumber != null) {
            String exact = cleanName(displayNamesByNumber.get(normalizedRegistered));
            if (!exact.isEmpty()) {
                return exact;
            }

            String registeredSuffix = lastTenDigits(normalizedRegistered);
            if (!registeredSuffix.isEmpty()) {
                for (Map.Entry<String, String> entry : displayNamesByNumber.entrySet()) {
                    if (registeredSuffix.equals(lastTenDigits(entry.getKey()))) {
                        String suffixMatch = cleanName(entry.getValue());
                        if (!suffixMatch.isEmpty()) {
                            return suffixMatch;
                        }
                    }
                }
            }
        }
        return cleanName(fallback);
    }

    static String displayName(
            ContentResolver contentResolver,
            String registeredNumber,
            String fallback
    ) {
        return displayName(
                readSnapshot(contentResolver).displayNamesByNumber,
                registeredNumber,
                fallback
        );
    }

    private static void addContact(
            Set<String> phoneNumbers,
            Map<String, String> displayNamesByNumber,
            String phoneNumber,
            String displayName
    ) {
        String normalized = FirebaseCallRepository.normalizePhoneNumber(phoneNumber);
        if (!normalized.isEmpty()) {
            phoneNumbers.add(normalized);
            String cleanedName = cleanName(displayName);
            if (!cleanedName.isEmpty()) {
                displayNamesByNumber.putIfAbsent(normalized, cleanedName);
            }
        }
    }

    private static String valueAt(Cursor cursor, int column) {
        return column < 0 || cursor.isNull(column) ? "" : cursor.getString(column);
    }

    private static String lastTenDigits(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 10) {
            return "";
        }
        return phoneNumber.substring(phoneNumber.length() - 10);
    }

    private static String cleanName(String value) {
        return value == null ? "" : value.trim();
    }

    static final class Snapshot {
        final Set<String> phoneNumbers;
        final Map<String, String> displayNamesByNumber;

        Snapshot(Set<String> phoneNumbers, Map<String, String> displayNamesByNumber) {
            this.phoneNumbers = phoneNumbers == null
                    ? Collections.emptySet()
                    : new HashSet<>(phoneNumbers);
            this.displayNamesByNumber = displayNamesByNumber == null
                    ? Collections.emptyMap()
                    : new HashMap<>(displayNamesByNumber);
        }

        static Snapshot empty() {
            return new Snapshot(Collections.emptySet(), Collections.emptyMap());
        }
    }
}
