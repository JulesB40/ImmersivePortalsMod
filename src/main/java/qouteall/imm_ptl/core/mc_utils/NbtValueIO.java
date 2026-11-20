package qouteall.imm_ptl.core.mc_utils;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.stream.Stream;

/**
 * Bridges ImmPtl's CompoundTag-based entity serialization with the
 * {@link ValueInput}/{@link ValueOutput} API introduced in newer Minecraft versions.
 * <p>
 * The portal data fields are stored inline (at the same level as vanilla entity fields),
 * so existing saves stay compatible.
 */
public class NbtValueIO {
    /**
     * A map codec that reads all fields of the map into a compound tag,
     * and writes all entries of a compound tag inline.
     */
    public static final MapCodec<CompoundTag> INLINE_COMPOUND = new MapCodec<>() {
        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.empty();
        }

        @Override
        public <T> DataResult<CompoundTag> decode(DynamicOps<T> ops, MapLike<T> input) {
            T map = ops.createMap(input.entries());
            Tag tag = ops.convertTo(NbtOps.INSTANCE, map);
            if (tag instanceof CompoundTag compoundTag) {
                return DataResult.success(compoundTag);
            }
            return DataResult.error(() -> "Not a compound tag: " + tag);
        }

        @Override
        public <T> RecordBuilder<T> encode(CompoundTag input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            for (String key : input.keySet()) {
                Tag value = input.get(key);
                if (value != null) {
                    prefix.add(key, NbtOps.INSTANCE.convertTo(ops, value));
                }
            }
            return prefix;
        }
    };

    public static CompoundTag readAll(ValueInput input) {
        return input.read(INLINE_COMPOUND).orElseGet(CompoundTag::new);
    }

    public static void writeAll(ValueOutput output, CompoundTag tag) {
        output.store(INLINE_COMPOUND, tag);
    }

    public static CompoundTag saveEntityWithoutId(Entity entity) {
        TagValueOutput output = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING, entity.registryAccess()
        );
        entity.saveWithoutId(output);
        return output.buildResult();
    }

    public static void loadEntity(Entity entity, CompoundTag tag) {
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, entity.registryAccess(), tag));
    }
}
