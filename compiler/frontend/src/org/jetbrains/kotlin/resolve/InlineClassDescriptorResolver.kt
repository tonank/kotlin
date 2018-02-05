/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.name.Name

object InlineClassDescriptorResolver {
    @JvmField
    val BOX_METHOD_NAME = Name.identifier("box")

    @JvmField
    val UNBOX_METHOD_NAME = Name.identifier("unbox")

    private val VALUE_PARAMETER_NAME = Name.identifier("v")

    fun createBoxFunctionDescrirptor(owner: ClassDescriptor): SimpleFunctionDescriptor? {
        return createConversionFunctionDescriptor(BOX_METHOD_NAME, owner)
    }

    fun createUnboxFunctionDescriptor(owner: ClassDescriptor): SimpleFunctionDescriptor? =
        createConversionFunctionDescriptor(UNBOX_METHOD_NAME, owner)

    private fun isBoxMethod(name: Name): Boolean = name == BOX_METHOD_NAME
    private fun isUnboxMethod(name: Name): Boolean = name == UNBOX_METHOD_NAME

    private fun createConversionFunctionDescriptor(
        name: Name,
        owner: ClassDescriptor
    ): SimpleFunctionDescriptor? {
        if (!isBoxMethod(name) && !isUnboxMethod(name)) return null

        val inlinedValue = owner.underlyingRepresentation() ?: return null

        val functionDescriptor = SimpleFunctionDescriptorImpl.create(
            owner,
            Annotations.EMPTY,
            name,
            CallableMemberDescriptor.Kind.SYNTHESIZED,
            SourceElement.NO_SOURCE
        )

        functionDescriptor.initialize(
            null,
            owner.thisAsReceiverParameter,
            emptyList<TypeParameterDescriptor>(),
            listOfNotNull(createValueParameter(functionDescriptor, inlinedValue)),
            if (isBoxMethod(name)) owner.defaultType else inlinedValue.returnType,
            Modality.FINAL,
            Visibilities.PUBLIC
        )

        return functionDescriptor
    }

    private fun createValueParameter(
        functionDescriptor: FunctionDescriptor,
        inlinedValue: ValueParameterDescriptor
    ): ValueParameterDescriptorImpl? {
        if (isUnboxMethod(functionDescriptor.name)) return null

        return ValueParameterDescriptorImpl(
            functionDescriptor,
            null,
            0,
            Annotations.EMPTY,
            VALUE_PARAMETER_NAME,
            inlinedValue.type,
            false, false, false, null, SourceElement.NO_SOURCE
        )
    }
}