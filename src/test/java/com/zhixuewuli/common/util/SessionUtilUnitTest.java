package com.zhixuewuli.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("SessionUtil 单元测试")
class SessionUtilUnitTest {

    @Test
    void generateSecureSessionId_shouldMatchNewFormat() {
        String sessionId = SessionUtil.generateSecureSessionId();

        assertThat(sessionId).hasSize(31);
        assertThat(sessionId.charAt(14)).isEqualTo('_');
        assertThat(sessionId).matches("^\\d{14}_[a-zA-Z0-9]{16}$");
    }

    @Test
    void extractTimestamp_shouldParseWhenValidNewFormat() {
        String sessionId = "20260421005700_abcdefghijkl1234";

        LocalDateTime timestamp = SessionUtil.extractTimestamp(sessionId);

        assertThat(timestamp).isEqualTo(LocalDateTime.of(2026, 4, 21, 0, 57, 0));
    }

    @Test
    void extractTimestamp_shouldReturnMinWhenInputIsNull() {
        assertThat(SessionUtil.extractTimestamp(null)).isEqualTo(LocalDateTime.MIN);
    }

    @Test
    void extractTimestamp_shouldReturnMinWhenLegacyFormatProvided() {
        String legacySessionId = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6";

        assertThat(SessionUtil.extractTimestamp(legacySessionId)).isEqualTo(LocalDateTime.MIN);
    }

    @Test
    void extractTimestamp_shouldReturnMinWhenTimestampInvalid() {
        String invalidTimestampSessionId = "20261321005700_abcdefghijkl1234";

        assertThat(SessionUtil.extractTimestamp(invalidTimestampSessionId)).isEqualTo(LocalDateTime.MIN);
    }

    @Test
    void getSessionTimeDisplay_shouldReturnFormattedTimeWhenValid() {
        String sessionId = "20260421005700_abcdefghijkl1234";

        assertThat(SessionUtil.getSessionTimeDisplay(sessionId)).isEqualTo("2026-04-21 00:57:00");
    }

    @Test
    void getSessionTimeDisplay_shouldReturnUnknownWhenInvalid() {
        assertThat(SessionUtil.getSessionTimeDisplay("invalid")).isEqualTo("未知时间");
    }

    @Test
    void isValidSessionId_shouldReturnTrueForValidNewFormat() {
        assertThat(SessionUtil.isValidSessionId("20260421005700_abcdefghijkl1234")).isTrue();
    }

    @Test
    void isValidSessionId_shouldReturnTrueForLegacyFormat() {
        assertThat(SessionUtil.isValidSessionId("a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6")).isTrue();
    }

    @Test
    void isValidSessionId_shouldReturnFalseForInvalidLength() {
        assertThat(SessionUtil.isValidSessionId("20260421005700_abcdefghijklmno")).isFalse();
    }

    @Test
    void sanitizeSessionId_shouldRemoveUnsafeCharactersAndKeepUnderscore() {
        String dirtySessionId = "2026-04-21_<script>'abc\\def\"";

        assertThat(SessionUtil.sanitizeSessionId(dirtySessionId)).isEqualTo("20260421_scriptabcdef");
    }

    @Test
    void sanitizeSessionId_shouldReturnNullWhenInputNull() {
        assertThat(SessionUtil.sanitizeSessionId(null)).isNull();
    }
}
