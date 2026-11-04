package com.example.balanceclick;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class BalanceClickExample implements ClientModInitializer {
    private static final String COMMAND_TEMPLATE = "say Balance: <balance>";
    private static final String PLACEHOLDER = "<balance>";

    private final BalanceTracker tracker = new BalanceTracker();
    private boolean enabled = true;

    @Override
    public void onInitializeClient() {
        // Right-click trigger removed. This is now a clean base for custom UI logic.
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void triggerManualCommand() {
        if (!enabled) {
            return;
        }

        String balance = tracker.getCurrentBalance();
        if (balance == null || balance.isEmpty() || "0".equals(balance)) {
            return;
        }

        String command = buildCommand(balance);
        sendCommand(command);
    }

    private String buildCommand(String balance) {
        return COMMAND_TEMPLATE.replace(PLACEHOLDER, balance);
    }

    private void sendCommand(String command) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        String chatCommand = command.startsWith("/") ? command.substring(1) : command;
        client.player.networkHandler.sendChatCommand(chatCommand);
        client.player.sendMessage(Text.of("Executed: " + command), false);
    }

    public static class BalanceTracker {
        private String currentBalance = "0";

        public String getCurrentBalance() {
            return currentBalance;
        }

        public void updateFromChat(String message) {
            if (message == null || message.isEmpty()) {
                return;
            }

            String parsed = extractNumber(message);
            if (!parsed.isEmpty()) {
                currentBalance = parsed;
            }
        }

        private String extractNumber(String input) {
            StringBuilder digits = new StringBuilder();
            for (char c : input.toCharArray()) {
                if (Character.isDigit(c) || c == '.') {
                    digits.append(c);
                }
            }
            return digits.length() == 0 ? "0" : digits.toString();
        }
    }
}
