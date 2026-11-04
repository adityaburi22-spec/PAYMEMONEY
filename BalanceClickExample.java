package com.example.balanceclick;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class BalanceClickExample implements ClientModInitializer {
    private static final String BALANCE_COMMAND = "balance";
    private static final long WAIT_TIMEOUT_MS = 60000L;
    private static final Pattern BALANCE_PATTERN = Pattern.compile(
            "(?i)(?:your\\s+balance\\s+is\\s+|balance\\s*(?:is)?\\s*)(?:\\$)?\\s*([\\d,]+(?:\\.\\d+)?)"
    );

    private volatile boolean balanceRequested = false;

    @Override
    public void onInitializeClient() {
        startBalanceRequestLoop();
    }

    public void requestBalanceNow() {
        triggerBalanceCommand();
    }

    public static String parseBalanceResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            return "0";
        }

        String normalized = rawResponse.toLowerCase(Locale.ROOT);
        Matcher matcher = BALANCE_PATTERN.matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1).replace(",", "");
        }

        String digitsOnly = normalized.replaceAll("[^0-9.]", "");
        if (digitsOnly.isEmpty()) {
            return "0";
        }

        return digitsOnly;
    }

    public void handleBalanceResponse(String rawResponse) {
        String parsedBalance = parseBalanceResponse(rawResponse);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }

        client.player.sendMessage(Text.of("[BalanceClick] Parsed balance: $" + parsedBalance), false);
    }

    private void startBalanceRequestLoop() {
        Thread thread = new Thread(() -> {
            long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS;

            while (!balanceRequested && System.currentTimeMillis() < deadline) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.player != null && client.player.networkHandler != null) {
                    triggerBalanceCommand();
                    return;
                }

                try {
                    Thread.sleep(250L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null && client.player.networkHandler != null) {
                triggerBalanceCommand();
            } else {
                System.out.println("[BalanceClickExample] Mod loaded. Waiting for a player/world so /balance can be sent.");
            }
        }, "balanceclick-requester");

        thread.setDaemon(true);
        thread.start();
    }

    private void triggerBalanceCommand() {
        if (balanceRequested) {
            return;
        }

        balanceRequested = true;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.player.networkHandler == null) {
            return;
        }

        client.player.networkHandler.sendChatCommand(BALANCE_COMMAND);
        client.player.sendMessage(Text.of("[BalanceClick] Sent /balance to request the current balance."), false);

        String demoResponse = "your balance is $123,456,789";
        handleBalanceResponse(demoResponse);
    }
}
