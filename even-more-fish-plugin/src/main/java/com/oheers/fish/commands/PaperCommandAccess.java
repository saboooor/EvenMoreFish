package com.oheers.fish.commands;

import io.papermc.paper.command.brigadier.Commands;
import java.util.function.Consumer;

/** Narrow compatibility bridge for late plugin loads, which have no public command registration event. */
@SuppressWarnings("UnstableApiUsage")
public final class PaperCommandAccess {
    private PaperCommandAccess() {}

    public static void withRegistrar(ClassLoader serverClassLoader, Consumer<Commands> action) {
        try {
            Class<?> type = Class.forName("io.papermc.paper.command.brigadier.PaperCommands", true, serverClassLoader);
            Commands registrar = (Commands) type.getField("INSTANCE").get(null);
            withRegistrar(registrar, () -> invoke(registrar, "setValid"),
                () -> invoke(registrar, "invalidate"), action);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("This Paper version does not expose the late command registration bridge", ex);
        }
    }
    static void withRegistrar(Commands registrar, Runnable activate, Runnable invalidate, Consumer<Commands> action) {
        boolean needsActivation = false;
        try { registrar.getDispatcher(); }
        catch (IllegalStateException ex) { needsActivation = true; }
        if (needsActivation) activate.run();
        try { action.accept(registrar); }
        finally { if (needsActivation) invalidate.run(); }
    }

    private static void invoke(Commands registrar, String method) {
        try { registrar.getClass().getMethod(method).invoke(registrar); }
        catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not change Paper command registrar validity", ex);
        }
    }
}
