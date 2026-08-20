package com.ezcall.onetoone;

import org.webrtc.RTCStats;
import org.webrtc.RTCStatsReport;

import java.util.HashMap;
import java.util.Map;

final class RemoteAudioLevelMeter {
    private final Map<String, EnergySample> previousEnergySamples = new HashMap<>();

    float read(RTCStatsReport report) {
        if (report == null) {
            return 0f;
        }

        float highestLevel = 0f;
        for (RTCStats stats : report.getStatsMap().values()) {
            if (!isInboundAudio(stats)) {
                continue;
            }

            Map<String, Object> members = stats.getMembers();
            float directLevel = numberAsLevel(members.get("audioLevel"));
            if (directLevel > 0f) {
                highestLevel = Math.max(highestLevel, directLevel);
            } else {
                highestLevel = Math.max(
                        highestLevel,
                        levelFromEnergyDelta(stats.getId(), members)
                );
            }
        }
        return clamp(highestLevel);
    }

    private boolean isInboundAudio(RTCStats stats) {
        if (stats == null || !"inbound-rtp".equals(stats.getType())) {
            return false;
        }
        Object kind = stats.getMembers().get("kind");
        if (kind == null) {
            kind = stats.getMembers().get("mediaType");
        }
        return "audio".equals(kind);
    }

    private float numberAsLevel(Object value) {
        if (!(value instanceof Number)) {
            return 0f;
        }
        return clamp(((Number) value).floatValue());
    }

    private float levelFromEnergyDelta(String statsId, Map<String, Object> members) {
        Object energyValue = members.get("totalAudioEnergy");
        Object durationValue = members.get("totalSamplesDuration");
        if (!(energyValue instanceof Number) || !(durationValue instanceof Number)) {
            return 0f;
        }

        double energy = ((Number) energyValue).doubleValue();
        double duration = ((Number) durationValue).doubleValue();
        EnergySample previous = previousEnergySamples.put(
                statsId,
                new EnergySample(energy, duration)
        );
        if (previous == null) {
            return 0f;
        }

        double energyDelta = energy - previous.energy;
        double durationDelta = duration - previous.duration;
        if (energyDelta <= 0d || durationDelta <= 0d) {
            return 0f;
        }
        return clamp((float) Math.sqrt(energyDelta / durationDelta));
    }

    private float clamp(float level) {
        if (Float.isNaN(level) || Float.isInfinite(level)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, level));
    }

    private static final class EnergySample {
        final double energy;
        final double duration;

        EnergySample(double energy, double duration) {
            this.energy = energy;
            this.duration = duration;
        }
    }
}
