package com.ezcall.onetoone;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.webrtc.RTCStats;
import org.webrtc.RTCStatsReport;

import java.util.HashMap;
import java.util.Map;

public class RemoteAudioLevelMeterTest {
    @Test
    public void readsDirectInboundAudioLevel() {
        RemoteAudioLevelMeter meter = new RemoteAudioLevelMeter();

        assertEquals(0.35f, meter.read(report(stats(
                "inbound-rtp",
                "audio",
                mapOf("audioLevel", 0.35d)
        ))), 0.0001f);
    }

    @Test
    public void ignoresVideoAndOutboundLevels() {
        RemoteAudioLevelMeter meter = new RemoteAudioLevelMeter();
        Map<String, RTCStats> stats = new HashMap<>();
        stats.put("video", stats(
                "inbound-rtp",
                "video",
                mapOf("audioLevel", 0.8d)
        ));
        stats.put("outbound", stats(
                "outbound-rtp",
                "audio",
                mapOf("audioLevel", 0.7d)
        ));

        assertEquals(0f, meter.read(new RTCStatsReport(0L, stats)), 0.0001f);
    }

    @Test
    public void calculatesLevelFromEnergyDeltaWhenDirectLevelIsMissing() {
        RemoteAudioLevelMeter meter = new RemoteAudioLevelMeter();
        meter.read(report(stats(
                "inbound-rtp",
                "audio",
                mapOf("totalAudioEnergy", 1d, "totalSamplesDuration", 10d)
        )));

        float level = meter.read(report(stats(
                "inbound-rtp",
                "audio",
                mapOf("totalAudioEnergy", 1.04d, "totalSamplesDuration", 11d)
        )));

        assertEquals(0.2f, level, 0.0001f);
    }

    private RTCStatsReport report(RTCStats stats) {
        Map<String, RTCStats> report = new HashMap<>();
        report.put(stats.getId(), stats);
        return new RTCStatsReport(0L, report);
    }

    private RTCStats stats(String type, String kind, Map<String, Object> values) {
        values.put("kind", kind);
        return new RTCStats(0L, type, "stats-id", values);
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new HashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return result;
    }
}
