/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.Nullable;

public class KotlinPluginUtil {

    public static final PluginId KOTLIN_PLUGIN_ID = PluginId.getId("org.jetbrains.kotlin");

    @Nullable
    public static String getPluginVersion() {
        IdeaPluginDescriptor plugin = PluginManager.getPlugin(KOTLIN_PLUGIN_ID);
        return plugin == null ? null : plugin.getVersion();
    }

    public static boolean isSnapshotVersionOrBundled() {
        return PluginManager.getPlugin(KOTLIN_PLUGIN_ID) == null || "@snapshot@".equals(getPluginVersion());
    }
}
