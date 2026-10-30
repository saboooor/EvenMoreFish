package com.oheers.fish.api.addons;

import com.oheers.fish.api.plugin.EMFPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AddonManagerTest {
    @TempDir Path directory;

    private EMFPlugin plugin() {
        EMFPlugin plugin = mock(EMFPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        return plugin;
    }

    @Test void installedAddonsAndTheirDataSurviveStartupShutdownAndRestart() throws Exception {
        Path addons = Files.createDirectory(directory.resolve("addons"));
        Path jar = addons.resolve("discord-updates.jar");
        byte[] original = {1, 2, 3, 4};
        Files.write(jar, original);
        Path config = Files.createDirectories(addons.resolve("custom-data")).resolve("config.yml");
        Files.writeString(config, "preserve: true");

        AddonManager manager = new AddonManager(plugin());
        assertArrayEquals(original, Files.readAllBytes(jar));
        manager.unload();
        assertArrayEquals(original, Files.readAllBytes(jar));
        new AddonManager(plugin());
        assertArrayEquals(original, Files.readAllBytes(jar));
        assertEquals("preserve: true", Files.readString(config));
    }

    @Test void createsMissingDirectoryAndRejectsAFileInItsPlace() throws Exception {
        new AddonManager(plugin());
        Path addons = directory.resolve("addons");
        assertTrue(Files.isDirectory(addons));
        Files.delete(addons);
        Files.writeString(addons, "keep this file");
        assertThrows(IllegalStateException.class, () -> new AddonManager(plugin()));
        assertEquals("keep this file", Files.readString(addons));
    }

    @SuppressWarnings("unchecked")
    @Test void shutdownUnloadsEveryAddonEvenIfOneFailsAndDoesNotUnloadTwice() throws Exception {
        AddonManager manager = new AddonManager(plugin());
        AddonLoader failing = mock(AddonLoader.class);
        AddonLoader healthy = mock(AddonLoader.class);
        doThrow(new IllegalStateException("addon failure")).when(failing).unload();
        var field = AddonManager.class.getDeclaredField("loadedAddons");
        field.setAccessible(true);
        List<AddonLoader> loaded = (List<AddonLoader>) field.get(manager);
        loaded.add(failing);
        loaded.add(healthy);

        assertDoesNotThrow(manager::unload);
        manager.unload();
        verify(failing, times(1)).unload();
        verify(healthy, times(1)).unload();
        assertTrue(loaded.isEmpty());
        assertTrue(Files.isDirectory(directory.resolve("addons")));
    }
}
