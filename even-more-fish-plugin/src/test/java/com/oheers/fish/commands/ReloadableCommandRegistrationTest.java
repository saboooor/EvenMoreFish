package com.oheers.fish.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("UnstableApiUsage")
class ReloadableCommandRegistrationTest {
    // Paper's real root has removeCommand(); the public Brigadier dependency does not.
    public static class PaperRoot extends RootCommandNode<CommandSourceStack> {
        private final Map<String, CommandNode<CommandSourceStack>> nodes = new LinkedHashMap<>();
        @Override public void addChild(CommandNode<CommandSourceStack> node) { nodes.put(node.getName(), node); }
        @Override public CommandNode<CommandSourceStack> getChild(String name) { return nodes.get(name); }
        @Override public Collection<CommandNode<CommandSourceStack>> getChildren() { return nodes.values(); }
        public void removeCommand(String name) { nodes.remove(name); }
    }

    private Commands registrar(PaperRoot root) {
        Commands registrar = mock(Commands.class);
        when(registrar.getDispatcher()).thenReturn(new CommandDispatcher<>(root));
        return registrar;
    }
    private ReloadableCommandRegistration registration(AtomicInteger count) {
        return new ReloadableCommandRegistration(registrar -> {
            int generation = count.incrementAndGet();
            Set<String> names = Set.of("emf", "fish", "evenmorefish:emf", "evenmorefish:fish", "emfadmin");
            for (String name : names) registrar.getDispatcher().getRoot().addChild(
                LiteralArgumentBuilder.<CommandSourceStack>literal(name).executes(ctx -> generation).build());
            return names;
        });
    }

    @Test void normalStartupDoesNotUseLateLoadFallbackOrRegisterTwice() {
        AtomicInteger count = new AtomicInteger();
        var registration = registration(count);
        registration.register(registrar(new PaperRoot()));
        registration.registerIfMissing(action -> fail("Startup already fired the command lifecycle event"));
        assertEquals(1, count.get());
    }

    @Test void disableAndLateEnableReplaceMainAliasesNamespaceAndAdminCommands() throws Exception {
        AtomicInteger count = new AtomicInteger();
        PaperRoot root = new PaperRoot();
        Commands registrar = registrar(root);
        var registration = registration(count);
        registration.register(registrar);
        registration.unregister();
        assertTrue(root.getChildren().isEmpty());
        registration.registerIfMissing(action -> action.accept(registrar));
        for (String name : List.of("emf", "fish", "evenmorefish:emf", "evenmorefish:fish", "emfadmin")) {
            assertEquals(2, root.getChild(name).getCommand().run(null), name + " must use the new handler");
        }
        registration.unregister();
        registration.unregister();
        assertTrue(root.getChildren().isEmpty());
    }

    @Test void shutdownPreservesCommandsReplacedByAnotherPlugin() {
        PaperRoot root = new PaperRoot();
        var registration = registration(new AtomicInteger());
        registration.register(registrar(root));
        var replacement = LiteralArgumentBuilder.<CommandSourceStack>literal("fish").build();
        root.addChild(replacement);
        registration.unregister();
        assertSame(replacement, root.getChild("fish"));
        assertEquals(1, root.getChildren().size());
    }

    @Test void datapackRebuildTracksTheNewDispatcher() {
        var registration = registration(new AtomicInteger());
        PaperRoot oldRoot = new PaperRoot();
        PaperRoot newRoot = new PaperRoot();
        registration.register(registrar(oldRoot));
        registration.register(registrar(newRoot));
        assertTrue(oldRoot.getChildren().isEmpty());
        assertNotNull(newRoot.getChild("emf"));
        registration.unregister();
        assertTrue(newRoot.getChildren().isEmpty());
    }

    @Test void lateRegistrarValidityIsRestoredEvenWhenRegistrationFails() {
        Commands registrar = mock(Commands.class);
        when(registrar.getDispatcher()).thenThrow(new IllegalStateException("outside lifecycle"));
        Runnable activate = mock(Runnable.class), invalidate = mock(Runnable.class);
        assertThrows(IllegalArgumentException.class, () -> PaperCommandAccess.withRegistrar(registrar, activate, invalidate,
            commands -> { throw new IllegalArgumentException("bad command"); }));
        verify(activate).run();
        verify(invalidate).run();
    }

    @Test void activeRegistrarIsLeftActive() {
        Runnable activate = mock(Runnable.class), invalidate = mock(Runnable.class);
        PaperCommandAccess.withRegistrar(registrar(new PaperRoot()), activate, invalidate, commands -> {});
        verifyNoInteractions(activate, invalidate);
    }
}
