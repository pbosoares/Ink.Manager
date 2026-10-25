package ink.manager.api.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/** Compact, stable path code for the existing public link. Legacy links remain valid. */
public final class PublicLinkCode {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private PublicLinkCode() {}

    public static String encode(String publicLink) {
        try {
            UUID id = UUID.fromString(publicLink);
            ByteBuffer bytes = ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits());
            return ENCODER.encodeToString(bytes.array());
        } catch (IllegalArgumentException notUuid) {
            return "mS" + ENCODER.encodeToString(publicLink.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static Optional<String> decode(String code) {
        if (code == null || code.length() < 3) return Optional.empty();
        try {
            if (code.startsWith("mU")) {
                byte[] bytes = DECODER.decode(code.substring(2));
                if (bytes.length != 16) return Optional.empty();
                ByteBuffer value = ByteBuffer.wrap(bytes);
                return Optional.of(new UUID(value.getLong(), value.getLong()).toString());
            }
            if (code.startsWith("mS")) {
                byte[] bytes = DECODER.decode(code.substring(2));
                if (bytes.length == 0 || bytes.length > 80) return Optional.empty();
                return Optional.of(new String(bytes, StandardCharsets.UTF_8));
            }
            if (code.length() == 22) {
                byte[] bytes = DECODER.decode(code);
                if (bytes.length != 16) return Optional.empty();
                ByteBuffer value = ByteBuffer.wrap(bytes);
                return Optional.of(new UUID(value.getLong(), value.getLong()).toString());
            }
            return Optional.empty();
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
