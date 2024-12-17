package net.fabricmc.fabric.mixin.entity.event;

import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.extensions.IItemStackExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IItemStackExtension.class)
public interface IItemStackExtensionMixin {

    @Inject(method = "canElytraFly", at = @At("HEAD"), cancellable = true)
    default void canElytraFly(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (EntityElytraEvents.CUSTOM.invoker().useCustomElytra(entity, false)) {
            cir.setReturnValue(true);
        }
    }
}
