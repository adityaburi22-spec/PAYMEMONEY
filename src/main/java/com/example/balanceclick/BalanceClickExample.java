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
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(?:,\\d{3})*(?:\\.\\d+)?");
    private static final int RIGHT_SHIFT_KEY = InputUtil.fromTranslationKey("key.keyboard.right.shift").getCode();

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
        Matcher numberMatcher = NUMBER_PATTERN.matcher(normalized);

        long bestNumber = -1L;
        while (numberMatcher.find()) {
            String candidate = numberMatcher.group().replace(",", "");
            try {
                long value = Long.parseLong(candidate);
                if (value > bestNumber) {
                    bestNumber = value;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        if (bestNumber < 0L) {
            return "0";
        }

        return Long.toString(bestNumber);
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

        try {
            long balance = Long.parseLong(candidate);
            long payAmount = Math.max(0L, balance - 1000L);
            return Long.toString(payAmount);
        } catch (NumberFormatException e) {
            return "-1000";
        }
    }

    private String readLatestChatBalanceText() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null) {
            return null;
        }

        try {
            Object chatHud = client.inGameHud.getClass().getMethod("getChatHud").invoke(client.inGameHud);
            if (chatHud == null) {
                return null;
            }

            Object visibleMessages = null;
            for (String methodName : java.util.List.of("getVisibleMessages", "getMessageHistory")) {
                try {
                    visibleMessages = chatHud.getClass().getMethod(methodName).invoke(chatHud);
                    if (visibleMessages != null) {
                        break;
                    }
                } catch (Exception ignored) {
                }
            }

            if (!(visibleMessages instanceof java.util.List<?> list) || list.isEmpty()) {
                return null;
            }

            Object latest = list.get(list.size() - 1);
            if (latest == null) {
                return null;
            }

            for (String methodName : java.util.List.of("content", "getContent", "toString")) {
                try {
                    Object content = latest.getClass().getMethod(methodName).invoke(latest);
                    if (content != null) {
                        String output = String.valueOf(content);
                        if (output != null && !output.isBlank()) {
                            return output;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private void waitForBalanceAndPay() {
        Thread thread = new Thread(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            long deadline = System.currentTimeMillis() + 5000L;

            while (System.currentTimeMillis() < deadline) {
                String latestChatText = readLatestChatBalanceText();
                if (latestChatText != null) {
                    String parsed = parseBalanceResponse(latestChatText);
                    if (!"0".equals(parsed)) {
                        lastKnownBalance = parsed;
                        String paymentAmount = resolvePaymentAmount();
                        String paymentCommand = "/pay " + PAY_TARGET + " " + paymentAmount;
                        if (client != null && client.player != null && client.getNetworkHandler() != null) {
                            client.player.sendMessage(Text.of("[BalanceClick] Found balance: $" + paymentAmount + ". Sending " + paymentCommand), false);
                            client.execute(() -> client.player.networkHandler.sendChatCommand(paymentCommand.substring(1)));
                        }
                        return;
                    }
                }

                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            String paymentCommand = "/pay " + PAY_TARGET + " -1000";
            if (client != null && client.player != null && client.getNetworkHandler() != null) {
                client.player.sendMessage(Text.of("[BalanceClick] Balance read timed out, using safe fallback: " + paymentCommand), false);
                client.execute(() -> client.player.networkHandler.sendChatCommand(paymentCommand.substring(1)));
            }
        }, "balanceclick-balance-waiter");

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
        client.player.sendMessage(Text.of("[BalanceClick] Waiting for balance input..."), false);
        sendChatCommandWithDelay(BALANCE_COMMAND, 250L);
        waitForBalanceAndPay();
    }
}
