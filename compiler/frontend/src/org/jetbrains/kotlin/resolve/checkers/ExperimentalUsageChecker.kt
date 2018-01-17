/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.resolve.checkers

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.config.AnalysisFlag
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.resolve.calls.checkers.CallChecker
import org.jetbrains.kotlin.resolve.calls.checkers.CallCheckerContext
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.EnumValue
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.utils.SmartSet
import org.jetbrains.kotlin.utils.addIfNotNull

object ExperimentalUsageChecker : CallChecker {
    private val EXPERIMENTAL_FQ_NAME = FqName("kotlin.Experimental")
    private val USE_EXPERIMENTAL_FQ_NAME = FqName("kotlin.UseExperimental")
    private val USE_EXPERIMENTAL_ANNOTATION_CLASS = Name.identifier("annotationClass")

    private val LEVEL = Name.identifier("level")
    private val WARNING_LEVEL = Name.identifier("WARNING")
    private val ERROR_LEVEL = Name.identifier("ERROR")

    private val IMPACT = Name.identifier("changesMayBreak")
    private val COMPILATION_IMPACT = Name.identifier("COMPILATION")
    private val LINKAGE_IMPACT = Name.identifier("LINKAGE")
    private val RUNTIME_IMPACT = Name.identifier("RUNTIME")

    private data class Experimentality(
        val descriptor: ClassDescriptor,
        val annotationFqName: FqName,
        val severity: Severity,
        val impact: List<Impact>
    ) {
        enum class Severity { WARNING, ERROR }
        enum class Impact { COMPILATION, LINKAGE_OR_RUNTIME }

        companion object {
            val DEFAULT_SEVERITY = Severity.ERROR
            val DEFAULT_IMPACT = listOf(Impact.COMPILATION, Impact.LINKAGE_OR_RUNTIME)
        }
    }

    override fun check(resolvedCall: ResolvedCall<*>, reportOn: PsiElement, context: CallCheckerContext) {
        checkExperimental(resolvedCall.resultingDescriptor, reportOn, context)
    }

    private fun checkExperimental(descriptor: DeclarationDescriptor, element: PsiElement, context: CheckerContext) {
        val experimentalities = descriptor.loadExperimentalities()
        if (experimentalities.isNotEmpty()) {
            checkExperimental(
                experimentalities, element, context.trace.bindingContext, context.languageVersionSettings,
                context.moduleDescriptor
            ) { annotationFqName, severity, isBodyUsageOfSourceOnlyExperimentality ->
                val diagnostic = when (severity) {
                    Experimentality.Severity.WARNING -> Errors.EXPERIMENTAL_API_USAGE
                    Experimentality.Severity.ERROR -> Errors.EXPERIMENTAL_API_USAGE_ERROR
                }
                context.trace.report(diagnostic.on(element, annotationFqName, isBodyUsageOfSourceOnlyExperimentality))
            }
        }
    }

    private fun checkExperimental(
        experimentalities: Collection<Experimentality>,
        element: PsiElement,
        bindingContext: BindingContext,
        languageVersionSettings: LanguageVersionSettings,
        module: ModuleDescriptor,
        report: (annotationFqName: FqName, severity: Experimentality.Severity, isBodyUsageOfCompilationExperimentality: Boolean) -> Unit
    ) {
        val isBodyUsageExceptInline = element.isBodyUsage(allowInline = false)
        val isBodyUsage = isBodyUsageExceptInline || element.isBodyUsage(allowInline = true)

        for ((annotationClassDescriptor, annotationFqName, severity, impact) in experimentalities) {
            val isBodyUsageOfCompilationExperimentality =
                impact.all(Experimentality.Impact.COMPILATION::equals) && isBodyUsage

            val isBodyUsageInSameModule =
                annotationClassDescriptor.module == module && isBodyUsageExceptInline

            val isExperimentalityAccepted =
                    isBodyUsageInSameModule ||
                    (isBodyUsageOfCompilationExperimentality &&
                     element.hasContainerAnnotatedWithUseExperimental(annotationFqName, bindingContext, languageVersionSettings)) ||
                    element.propagates(annotationFqName, bindingContext, languageVersionSettings)

            if (!isExperimentalityAccepted) {
                report(annotationFqName, severity, isBodyUsageOfCompilationExperimentality)
            }
        }
    }

    private fun DeclarationDescriptor.loadExperimentalities(): Set<Experimentality> {
        val result = SmartSet.create<Experimentality>()

        for (annotation in annotations) {
            result.addIfNotNull(annotation.annotationClass?.loadExperimentalityForMarkerAnnotation())
        }

        val container = containingDeclaration
        if (container is ClassDescriptor && this !is ConstructorDescriptor) {
            for (annotation in container.annotations) {
                result.addIfNotNull(annotation.annotationClass?.loadExperimentalityForMarkerAnnotation())
            }
        }

        return result
    }

    private fun ClassDescriptor.loadExperimentalityForMarkerAnnotation(): Experimentality? {
        val experimental = annotations.findAnnotation(EXPERIMENTAL_FQ_NAME) ?: return null

        val severity = when ((experimental.allValueArguments[LEVEL] as? EnumValue)?.enumEntryName) {
            WARNING_LEVEL -> Experimentality.Severity.WARNING
            ERROR_LEVEL -> Experimentality.Severity.ERROR
            else -> Experimentality.DEFAULT_SEVERITY
        }

        val impact = (experimental.allValueArguments[IMPACT] as? ArrayValue)?.value?.mapNotNull { impact ->
            when ((impact as? EnumValue)?.enumEntryName) {
                COMPILATION_IMPACT -> Experimentality.Impact.COMPILATION
                LINKAGE_IMPACT, RUNTIME_IMPACT -> Experimentality.Impact.LINKAGE_OR_RUNTIME
                else -> null
            }
        } ?: Experimentality.DEFAULT_IMPACT

        return Experimentality(this, fqNameSafe, severity, impact)
    }

    // Returns true if this element appears in the body of some function and is not visible in any non-local declaration signature.
    // If that's the case, one can opt-in to using the corresponding experimental API by annotating this element (or any of its
    // enclosing declarations) with @UseExperimental(X::class), not requiring propagation of the experimental annotation to the call sites.
    // (Note that this is allowed only if X's impact is [COMPILATION].)
    private fun PsiElement.isBodyUsage(allowInline: Boolean): Boolean {
        var element = this
        while (true) {
            val parent = element.parent ?: return false

            if (element == (parent as? KtDeclarationWithBody)?.bodyExpression?.takeIf { allowInline || !parent.isInline } ||
                element == (parent as? KtDeclarationWithInitializer)?.initializer ||
                element == (parent as? KtClassInitializer)?.body ||
                element == (parent as? KtParameter)?.defaultValue ||
                element == (parent as? KtSuperTypeCallEntry)?.valueArgumentList ||
                element == (parent as? KtDelegatedSuperTypeEntry)?.delegateExpression ||
                element == (parent as? KtPropertyDelegate)?.expression) return true

            if (element is KtFile) return false
            element = parent
        }
    }

    private val PsiElement.isInline: Boolean
        get() = when (this) {
            is KtFunction -> hasModifier(KtTokens.INLINE_KEYWORD)
            is KtPropertyAccessor -> hasModifier(KtTokens.INLINE_KEYWORD) || property.hasModifier(KtTokens.INLINE_KEYWORD)
            else -> false
        }

    // Checks whether any of the non-local enclosing declarations is annotated with annotationFqName, effectively requiring
    // propagation for the experimental annotation to the call sites
    private fun PsiElement.propagates(
        annotationFqName: FqName,
        bindingContext: BindingContext,
        languageVersionSettings: LanguageVersionSettings
    ): Boolean {
        var element = this
        while (true) {
            if (element is KtDeclaration) {
                val descriptor = bindingContext.get(BindingContext.DECLARATION_TO_DESCRIPTOR, element)
                if (descriptor != null && !DescriptorUtils.isLocal(descriptor) &&
                    descriptor.annotations.hasAnnotation(annotationFqName)) return true
            }

            if (element is KtFile) break
            element = element.parent ?: break
        }

        return annotationFqName.asString() in languageVersionSettings.getFlag(AnalysisFlag.experimental)
    }

    // Checks whether there's an element lexically above the tree, that is annotated with `@UseExperimental(X::class)`
    // where annotationFqName is the FQ name of X
    private fun PsiElement.hasContainerAnnotatedWithUseExperimental(
        annotationFqName: FqName,
        bindingContext: BindingContext,
        languageVersionSettings: LanguageVersionSettings
    ): Boolean {
        var element = this
        while (true) {
            if (element is KtAnnotated && element.annotationEntries.any { entry ->
                bindingContext.get(BindingContext.ANNOTATION, entry)?.isUseExperimental(annotationFqName) == true
            }) return true

            if (element is KtFile) break
            element = element.parent ?: break
        }

        return annotationFqName.asString() in languageVersionSettings.getFlag(AnalysisFlag.useExperimental)
    }

    private fun AnnotationDescriptor.isUseExperimental(annotationFqName: FqName): Boolean {
        if (fqName != USE_EXPERIMENTAL_FQ_NAME) return false

        val annotationClasses = allValueArguments[USE_EXPERIMENTAL_ANNOTATION_CLASS]
        return annotationClasses is ArrayValue && annotationClasses.value.any { annotationClass ->
            (annotationClass as? KClassValue)?.value?.constructor?.declarationDescriptor?.fqNameSafe == annotationFqName
        }
    }

    fun checkCompilerArguments(module: ModuleDescriptor, languageVersionSettings: LanguageVersionSettings, reportError: (String) -> Unit) {
        fun checkAnnotation(fqName: String, allowNonCompilationImpact: Boolean): Boolean {
            val descriptor = module.resolveClassByFqName(FqName(fqName), NoLookupLocation.FOR_NON_TRACKED_SCOPE)
            val experimentality = descriptor?.loadExperimentalityForMarkerAnnotation()
            val message = when {
                descriptor == null ->
                    "Experimental API marker $fqName is unresolved. " +
                    "Please make sure it's present in the module dependencies"
                experimentality == null ->
                    "Class $fqName is not an experimental API marker annotation"
                !allowNonCompilationImpact && !experimentality.impact.all(Experimentality.Impact.COMPILATION::equals) ->
                    "Experimental API marker $fqName has impact other than COMPILATION, therefore it can't be used with -Xuse-experimental"
                else -> return true
            }
            reportError(message)
            return false
        }

        val validExperimental =
            languageVersionSettings.getFlag(AnalysisFlag.experimental).filter { checkAnnotation(it, allowNonCompilationImpact = true) }
        val validUseExperimental =
            languageVersionSettings.getFlag(AnalysisFlag.useExperimental).filter { checkAnnotation(it, allowNonCompilationImpact = false) }

        for (fqName in validExperimental.intersect(validUseExperimental)) {
            reportError("'-Xuse-experimental=$fqName' has no effect because '-Xexperimental=$fqName' is used")
        }
    }

    object ClassifierUsage : ClassifierUsageChecker {
        override fun check(targetDescriptor: ClassifierDescriptor, element: PsiElement, context: ClassifierUsageCheckerContext) {
            checkExperimental(targetDescriptor, element, context)
        }
    }

    object Overrides : DeclarationChecker {
        override fun check(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
            if (descriptor !is CallableMemberDescriptor) return

            val experimentalOverridden = descriptor.overriddenDescriptors.flatMap { member ->
                member.loadExperimentalities().map { experimentality -> experimentality to member }
            }.toMap()

            val module = descriptor.module

            for ((experimentality, member) in experimentalOverridden) {
                checkExperimental(
                    listOf(experimentality), declaration, context.trace.bindingContext,
                    context.languageVersionSettings, module
                ) { annotationFqName, severity, _ ->
                    val diagnostic = when (severity) {
                        Experimentality.Severity.WARNING -> Errors.EXPERIMENTAL_OVERRIDE
                        Experimentality.Severity.ERROR -> Errors.EXPERIMENTAL_OVERRIDE_ERROR
                    }
                    val reportOn = (declaration as? KtNamedDeclaration)?.nameIdentifier ?: declaration
                    context.trace.report(diagnostic.on(reportOn, annotationFqName, member.containingDeclaration))
                }
            }
        }
    }

    object ExperimentalDeclarationChecker : AdditionalAnnotationChecker {
        private val wrongTargetsForExperimentalAnnotations = setOf(KotlinTarget.EXPRESSION, KotlinTarget.FILE)

        override fun checkEntries(entries: List<KtAnnotationEntry>, actualTargets: List<KotlinTarget>, trace: BindingTrace) {
            var isAnnotatedWithExperimental = false

            for (entry in entries) {
                val annotation = trace.bindingContext.get(BindingContext.ANNOTATION, entry)
                if (annotation?.fqName == USE_EXPERIMENTAL_FQ_NAME) {
                    val annotationClasses =
                        (annotation.allValueArguments[USE_EXPERIMENTAL_ANNOTATION_CLASS] as? ArrayValue)?.value ?: continue
                    if (annotationClasses.isEmpty()) {
                        trace.report(Errors.USE_EXPERIMENTAL_WITHOUT_ARGUMENTS.on(entry))
                        continue
                    }
                    for (annotationClass in annotationClasses) {
                        val classDescriptor =
                            (annotationClass as? KClassValue)?.value?.constructor?.declarationDescriptor as? ClassDescriptor ?: continue
                        val experimentality = classDescriptor.loadExperimentalityForMarkerAnnotation()
                        if (experimentality == null) {
                            trace.report(Errors.USE_EXPERIMENTAL_ARGUMENT_IS_NOT_MARKER.on(entry, classDescriptor.fqNameSafe))
                        } else if (!experimentality.impact.all(Experimentality.Impact.COMPILATION::equals)) {
                            trace.report(Errors.USE_EXPERIMENTAL_ARGUMENT_HAS_NON_COMPILATION_IMPACT.on(entry, experimentality.annotationFqName))
                        }
                    }
                }

                if (annotation?.fqName == EXPERIMENTAL_FQ_NAME) {
                    if ((annotation.allValueArguments[IMPACT] as? ArrayValue)?.value?.isEmpty() == true) {
                        trace.report(Errors.EXPERIMENTAL_ANNOTATION_WITH_NO_IMPACT.on(entry))
                    }
                    isAnnotatedWithExperimental = true
                }
            }

            if (isAnnotatedWithExperimental) {
                val resolvedEntries = entries.associate { entry -> entry to trace.bindingContext.get(BindingContext.ANNOTATION, entry) }
                for ((entry, descriptor) in resolvedEntries) {
                    if (descriptor != null && descriptor.fqName == KotlinBuiltIns.FQ_NAMES.target) {
                        val allowedTargets = AnnotationChecker.loadAnnotationTargets(descriptor) ?: continue
                        val wrongTargets = allowedTargets.intersect(wrongTargetsForExperimentalAnnotations)
                        if (wrongTargets.isNotEmpty()) {
                            trace.report(
                                Errors.EXPERIMENTAL_ANNOTATION_WITH_WRONG_TARGET.on(
                                    entry, wrongTargets.joinToString(transform = KotlinTarget::description)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
