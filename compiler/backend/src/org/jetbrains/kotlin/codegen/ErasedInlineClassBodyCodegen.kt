/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen

import com.intellij.util.ArrayUtil
import org.jetbrains.kotlin.codegen.context.ClassContext
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ConstructorOfErasedInlineClassOrigin
import org.jetbrains.kotlin.resolve.jvm.jvmSignature.JvmMethodSignature
import org.jetbrains.org.objectweb.asm.Opcodes

class ErasedInlineClassBodyCodegen(
    aClass: KtClass,
    context: ClassContext,
    v: ClassBuilder,
    state: GenerationState,
    parentCodegen: MemberCodegen<*>?
) : ClassBodyCodegen(aClass, context, v, state, parentCodegen) {
    override fun generateDeclaration() {
        v.defineClass(
            myClass.psiOrParent, state.classFileVersion, Opcodes.ACC_FINAL or Opcodes.ACC_STATIC or Opcodes.ACC_PUBLIC,
            typeMapper.mapErasedInlineClass(descriptor).internalName,
            null, "java/lang/Object", ArrayUtil.EMPTY_STRING_ARRAY
        )
        v.visitSource(myClass.containingKtFile.name, null)
    }

    override fun generateConstructors() {
        val constructor = descriptor.unsubstitutedPrimaryConstructor ?: return
        functionCodegen.generateMethod(
            ConstructorOfErasedInlineClassOrigin(constructor), constructor, object : FunctionGenerationStrategy.CodegenBased(state) {
                override fun doGenerateBody(codegen: ExpressionCodegen, signature: JvmMethodSignature) {
                    val inlinedValueType = state.typeMapper.mapType(descriptor)
                    val iv = codegen.v
                    iv.load(0, inlinedValueType)
                    iv.areturn(inlinedValueType)
                }
            }

        )
    }

    override fun generateKotlinMetadataAnnotation() {
        writeSyntheticClassMetadata(v, state)
    }
}