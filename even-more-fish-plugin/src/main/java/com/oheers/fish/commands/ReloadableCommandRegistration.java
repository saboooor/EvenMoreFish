package com.oheers.fish.commands;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Consumer;

/** Tracks actual dispatcher nodes, including aliases, so a disabled plugin leaves no stale commands. */
@SuppressWarnings("UnstableApiUsage")
public final class ReloadableCommandRegistration {
    private final Function<Commands, Set<String>> registerCommands;
    private final Map<String, CommandNode<CommandSourceStack>> ownedNodes = new LinkedHashMap<>();
    private RootCommandNode<CommandSourceStack> root;
    private boolean registered;

    public ReloadableCommandRegistration(Function<Commands, Set<String>> registerCommands) {
        this.registerCommands = registerCommands;
    }

    public void register(Commands registrar) {
        unregister();
        root = registrar.getDispatcher().getRoot();
        Set<String> labels = registerCommands.apply(registrar);
        for (String label : labels) {
            CommandNode<CommandSourceStack> node = root.getChild(label);
            if (node != null) ownedNodes.put(label, node);
        }
        registered = true;
    }

    /** Paper does not replay the COMMANDS event for a plugin enabled after startup. */
    public void registerIfMissing(Consumer<Consumer<Commands>> accessRegistrar) {
        if (!registered) accessRegistrar.accept(this::register);
    }

    public void unregister() {
        try {
            if (root != null) {
                for (var entry : ownedNodes.entrySet()) {
                    if (root.getChild(entry.getKey()) == entry.getValue()) {
                        // Paper adds this method to Brigadier. Use reflection to compile against the public API.
                        root.getClass().getMethod("removeCommand", String.class).invoke(root, entry.getKey());
                    }
                }
            }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not remove EvenMoreFish command nodes", ex);
        } finally {
            ownedNodes.clear();
            root = null;
            registered = false;
        }
    }
}
