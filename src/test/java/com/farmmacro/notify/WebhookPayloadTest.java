package com.farmmacro.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WebhookPayloadTest {
    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyz0123456789";

    @Test
    void acceptsOnlyHttpsDiscordWebhookHosts() {
        String ok = "https://discord.com/api/webhooks/123456789012345678/" + TOKEN;
        assertTrue(Webhook.validUrl(ok));
        assertTrue(Webhook.validUrl(ok.replace("discord.com", "ptb.discord.com")));
        assertTrue(Webhook.validUrl(ok.replace("/api/", "/api/v10/")));
        assertFalse(Webhook.validUrl(ok.replace("https", "http")));
        assertFalse(Webhook.validUrl(ok.replace("discord.com", "discord.com.evil.example")));
        assertFalse(Webhook.validUrl("https://evil.example/https://discord.com/api/webhooks/123456/" + TOKEN));
        assertFalse(Webhook.validUrl(ok + "?wait=true"));
        assertFalse(Webhook.validUrl(ok + "\n"));
        assertFalse(Webhook.validUrl("https://user@discord.com/api/webhooks/123456789012345678/" + TOKEN));
    }

    @Test
    void payloadEscapesTextAndSuppressesMentions() {
        String text = "@everyone @here \"кавычки\" \\ слэш\nвторая строка\u0007\u0000 end";
        var o = JsonParser.parseString(Webhook.payload(text)).getAsJsonObject();
        String content = o.get("content").getAsString();
        assertTrue(content.contains("\"кавычки\"") && content.contains("\\ слэш"), content);
        assertTrue(content.contains("\nвторая"), "перевод строки сохраняется");
        assertFalse(content.contains("\u0007") || content.contains("\u0000"), "управляющие символы вычищены");
        assertEquals(
                0, o.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size(), "никого не пингуем");
        assertEquals("Kaelix", o.get("username").getAsString());
    }

    @Test
    void payloadNeverCarriesTheWebhookToken() {
        PrivateText.secret("https://discord.com/api/webhooks/123456789012345678/" + TOKEN);
        String json = Webhook.payload("ошибка " + TOKEN + " https://discord.com/api/webhooks/1/" + TOKEN);
        assertFalse(json.contains(TOKEN), json);
    }

    @Test
    void longTextIsCutWithoutSplittingSurrogatePairs() {
        String text = "a".repeat(1897) + "😀😀😀";
        String out = PrivateText.outgoing(text);
        assertTrue(out.length() <= 1900, "len=" + out.length());
        assertFalse(Character.isHighSurrogate(out.charAt(out.length() - 2)), "оборванная суррогатная пара");
    }

    @Test
    void multipartKeepsBoundaryAndSanitizesFileName() {
        byte[] png = {(byte) 137, 80, 78, 71};
        String body = new String(
                Webhook.multipart("BND", "{\"content\":\"x\"}", "pa nic/../\"x\".png", png),
                StandardCharsets.ISO_8859_1);
        assertTrue(body.startsWith("--BND\r\n"));
        assertTrue(body.endsWith("\r\n--BND--\r\n"));
        assertTrue(body.contains("filename=\"pa_nic_.._x_.png\""), body);
        assertEquals(1, body.split("name=\"files\\[0\\]\"", -1).length - 1);
    }
}
