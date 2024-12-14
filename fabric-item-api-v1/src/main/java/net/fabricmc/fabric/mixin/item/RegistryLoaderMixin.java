/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.mixin.item;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Decoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.fabricmc.fabric.impl.item.EnchantmentUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RegistryDataLoader.class)
abstract class RegistryLoaderMixin {
    private static Resource _fabricCapturedResource;

    @Inject(
            method = "loadElementFromResource",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Optional;ifPresentOrElse(Ljava/util/function/Consumer;Ljava/lang/Runnable;)V"
            )
    )
    private static <E> void captureArgs(WritableRegistry<E> arg, Decoder<E> decoder2, RegistryOps<JsonElement> arg2, ResourceKey<E> arg3, Resource arg4, RegistrationInfo arg5, CallbackInfo ci) {
        _fabricCapturedResource = arg4;
    }

    @WrapOperation(
            method = "lambda$loadElementFromResource$13",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/WritableRegistry;register(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;"
            )
    )
    @SuppressWarnings("unchecked")
    private static <T> Holder.Reference<T> enchantmentKey(
            WritableRegistry<T> instance,
            ResourceKey<T> objectKey,
            Object object,
            RegistrationInfo registryEntryInfo,
            Operation<Holder.Reference<T>> original
    ) {
        if (object instanceof Enchantment enchantment) {
            object = EnchantmentUtil.modify((ResourceKey<Enchantment>) objectKey, enchantment, EnchantmentUtil.determineSource(_fabricCapturedResource));
        }

        return original.call(instance, objectKey, object, registryEntryInfo);
    }
}
