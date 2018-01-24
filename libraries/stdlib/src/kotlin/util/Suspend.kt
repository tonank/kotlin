/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package kotlin

@kotlin.internal.InlineOnly
public inline fun <R> suspend(noinline block: suspend () -> R): suspend () -> R = block
