package dev.foxgirl.cminus.mixin;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.foxgirl.cminus.CMinusKt;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MixinStringNbtReader adds extra error handling to Minecraft's default NBT
 * string decoder. This prevents a bug where evil clients can crash the server.
 */
@Mixin(StringNbtReader.class)
public abstract class MixinStringNbtReader {

    @Shadow @Final
    private StringReader reader;

    @Unique
    private boolean currentlyParsingCompound = false;
    @Unique
    private boolean currentlyParsingElement = false;

    @Inject(method = "parseCompound", at = @At("HEAD"), cancellable = true)
    private void injected$parseCompound(CallbackInfoReturnable<NbtCompound> info) throws CommandSyntaxException {
        if (!currentlyParsingCompound) {
            currentlyParsingCompound = true;
            try {
                info.setReturnValue(((StringNbtReader) (Object) this).parseCompound());
            } catch (CommandSyntaxException cause) {
                throw cause;
            } catch (Throwable cause) {
                String message = "StringNbtReader#parseCompound unexpected " + cause.getClass().getSimpleName() + ": " + cause.getMessage();
                CMinusKt.getLogger().warn(cause);
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException().createWithContext(reader, message);
            } finally {
                currentlyParsingCompound = false;
            }
        }
    }

    @Inject(method = "parseElement", at = @At("HEAD"), cancellable = true)
    private void injected$parseElement(CallbackInfoReturnable<NbtElement> info) throws CommandSyntaxException {
        if (!currentlyParsingElement) {
            currentlyParsingElement = true;
            try {
                info.setReturnValue(((StringNbtReader) (Object) this).parseElement());
            } catch (CommandSyntaxException cause) {
                throw cause;
            } catch (Throwable cause) {
                String message = "StringNbtReader#parseElement unexpected " + cause.getClass().getSimpleName() + ": " + cause.getMessage();
                CMinusKt.getLogger().warn(cause);
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException().createWithContext(reader, message);
            } finally {
                currentlyParsingElement = false;
            }
        }
    }

}

