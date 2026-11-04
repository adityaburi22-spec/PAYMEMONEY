```java
package com.example.balanceclick;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

public class BalanceClickExample implements ClientModInitializer {

    private static final String BALANCE_COMMAND = "balance";
    private static final String PAY_TARGET = "Slogc";

    private static final long RESERVED_BALANCE = 1000L;
    private static final long BALANCE_TIMEOUT = 5000L;

    // Extracts numeric sequences, including comma-separated numbers.
    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("\\d+(?:,\\d{3})*(?:\\.\\d+)?");

    private static final int RIGHT_SHIFT_KEY =
            InputUtil.fromTranslationKey(
                    "key.keyboard.right.shift"
            ).getCode();

    private volatile boolean lastShiftPressed = false;
    private volatile boolean waitingForBalance = false;

    private volatile long balanceRequestTime = 0L;

    @Override
    public void onInitializeClient() {

        // Listen for normal server/system messages.
        ClientReceiveMessageEvents.GAME.register(
                (message, overlay) -> handleIncomingBalance(message)
        );

        // Listen for player chat messages too.
        ClientReceiveMessageEvents.CHAT.register(
                (message, signedMessage, sender, params, timestamp) ->
                        handleIncomingBalance(message)
        );

        startRightShiftListener();

    }

    // Extract the largest number from a message.
    public static String parseBalanceResponse(String rawResponse) {

        if (rawResponse == null || rawResponse.isBlank()) {
            return "0";
        }

        Matcher matcher = NUMBER_PATTERN.matcher(rawResponse);

        long bestNumber = -1L;

        while (matcher.find()) {

            String candidate = matcher.group().replace(",", "");

            try {

                long value = Long.parseLong(candidate);

                if (value > bestNumber) {
                    bestNumber = value;
                }

            } catch (NumberFormatException ignored) {
            }
        }

        return bestNumber < 0L
                ? "0"
                : Long.toString(bestNumber);
    }

    // Called whenever a chat message arrives.
    private void handleIncomingBalance(Text message) {

        if (!waitingForBalance || message == null) {
            return;
        }

        String rawMessage = message.getString();

        String normalized = rawMessage.toLowerCase(Locale.ROOT);

        // Only process the actual balance response.
        if (!normalized.contains("your balance is")) {
            return;
        }

        String parsedBalance = parseBalanceResponse(rawMessage);

        if ("0".equals(parsedBalance)) {
            return;
        }

        waitingForBalance = false;

        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null || client.player == null
                || client.getNetworkHandler() == null) {
            return;
        }

        try {

            long balance = Long.parseLong(parsedBalance);

            if (balance <= RESERVED_BALANCE) {

                client.player.sendMessage(
                        Text.of(
                                "[BalanceClick] Insufficient balance. Current: $"
                                        + parsedBalance
                        ),
                        false
                );

                return;
            }

            long paymentAmount = balance - RESERVED_BALANCE;

            String paymentCommand =
                    "pay " + PAY_TARGET + " " + paymentAmount;

            client.player.sendMessage(
                    Text.of(
                            "[BalanceClick] Balance: $" + parsedBalance
                                    + " | Payment: $" + paymentAmount
                    ),
                    false
            );

            // Execute on Minecraft's client thread.
            client.getNetworkHandler().sendChatCommand(paymentCommand);

        } catch (NumberFormatException ignored) {

            client.player.sendMessage(
                    Text.of("[BalanceClick] Invalid balance number."),
                    false
            );

        }
    }

    // Trigger balance request.
    private void triggerBalanceCommand() {

        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null || client.player == null
                || client.getNetworkHandler() == null) {
            return;
        }

        if (waitingForBalance) {
            return;
        }

        waitingForBalance = true;
        balanceRequestTime = System.currentTimeMillis();

        client.player.sendMessage(
                Text.of("[BalanceClick] Requesting balance..."),
                false
        );

        client.getNetworkHandler().sendChatCommand(BALANCE_COMMAND);

    }

    // Listen for Right Shift.
    private void startRightShiftListener() {

        Thread thread = new Thread(() -> {

            while (!Thread.currentThread().isInterrupted()) {

                MinecraftClient client = MinecraftClient.getInstance();

                if (client != null && client.getWindow() != null) {

                    long windowHandle =
                            client.getWindow().getHandle();

                    boolean isShiftPressed =
                            GLFW.glfwGetKey(
                                    windowHandle,
                                    RIGHT_SHIFT_KEY
                            ) == GLFW.GLFW_PRESS;

                    if (isShiftPressed && !lastShiftPressed) {

                        client.execute(this::triggerBalanceCommand);

                    }

                    lastShiftPressed = isShiftPressed;

                } else {

                    lastShiftPressed = false;

                }

                // Timeout protection.
                if (waitingForBalance
                        && System.currentTimeMillis() - balanceRequestTime
                        > BALANCE_TIMEOUT) {

                    waitingForBalance = false;

                    if (client != null) {

                        client.execute(() -> {

                            if (client.player != null) {

                                client.player.sendMessage(
                                        Text.of(
                                                "[BalanceClick] Balance request timed out."
                                        ),
                                        false
                                );

                            }

                        });

                    }
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

}
```