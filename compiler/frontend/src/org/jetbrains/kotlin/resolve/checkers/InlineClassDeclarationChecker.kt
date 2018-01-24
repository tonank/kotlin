/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.checkers

import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.diagnostics.DiagnosticSink
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.incremental.components.ExpectActualTracker
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.psiUtil.modalityModifier
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils

object InlineClassDeclarationChecker : DeclarationChecker {
    override fun check(
        declaration: KtDeclaration,
        descriptor: DeclarationDescriptor,
        diagnosticHolder: DiagnosticSink,
        bindingContext: BindingContext,
        languageVersionSettings: LanguageVersionSettings,
        expectActualTracker: ExpectActualTracker
    ) {
        if (!languageVersionSettings.supportsFeature(LanguageFeature.InlineClasses)) return

        if (declaration !is KtClass) return
        if (descriptor !is ClassDescriptor || !descriptor.isInline) return

        if (!DescriptorUtils.isTopLevelDeclaration(descriptor)) {
            diagnosticHolder.report(Errors.INLINE_CLASS_NOT_TOP_LEVEL.on(declaration))
        }

        val modalityModifier = declaration.modalityModifier()
        if (modalityModifier != null && descriptor.modality != Modality.FINAL) {
            diagnosticHolder.report(Errors.INLINE_CLASS_NOT_FINAL.on(modalityModifier))
        }

        val primaryConstructor = declaration.primaryConstructor
        if (primaryConstructor == null) {
            diagnosticHolder.report(Errors.ABSENCE_OF_PRIMARY_CONSTRUCTOR_FOR_INLINE_CLASS.on(declaration))
        }

        val parameters = primaryConstructor?.valueParameters ?: emptyList()
        if (parameters.size != 1) {
            (primaryConstructor?.valueParameterList ?: declaration).let {
                diagnosticHolder.report(Errors.INLINE_CLASS_CONSTRUCTOR_WRONG_PARAMETERS_SIZE.on(it))
            }
        }

        for (parameter in parameters) {
            if (!parameter.hasValOrVar() || parameter.isMutable || parameter.isVarArg) {
                diagnosticHolder.report(Errors.INLINE_CLASS_CONSTRUCTOR_NOT_READ_ONLY_PARAMETER.on(parameter))
            }
        }
    }

}