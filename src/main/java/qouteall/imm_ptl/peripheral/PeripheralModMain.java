package qouteall.imm_ptl.peripheral;

import com.mojang.serialization.MapCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.peripheral.dim_stack.DimStackManagement;
import qouteall.imm_ptl.peripheral.portal_generation.IntrinsicPortalGeneration;
import qouteall.imm_ptl.peripheral.wand.ClientPortalWandPortalDrag;
import qouteall.imm_ptl.peripheral.wand.PortalWandInteraction;
import qouteall.imm_ptl.peripheral.wand.PortalWandItem;

import java.util.function.BiConsumer;

public class PeripheralModMain {
    
    public static final Block portalHelperBlock =
        new Block(
            net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                .setId(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.BLOCK,
                    Identifier.fromNamespaceAndPath("immersive_portals", "portal_helper")
                ))
                .noOcclusion()
                .isRedstoneConductor((a, b, c) -> false)
        );
    
    public static final BlockItem portalHelperBlockItem =
        new PortalHelperItem(
            PeripheralModMain.portalHelperBlock,
            new Item.Properties()
                .setId(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.ITEM,
                    Identifier.fromNamespaceAndPath("immersive_portals", "portal_helper")
                ))
                .useBlockDescriptionPrefix()
        );
    
    public static final CreativeModeTab TAB =
        net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab.builder()
            .icon(() -> new ItemStack(PortalWandItem.instance))
            .title(Component.translatable("imm_ptl.item_group"))
            .displayItems((enabledFeatures, entries) -> {
                PortalWandItem.addIntoCreativeTag(entries);
                
                CommandStickItem.addIntoCreativeTag(entries);
                
                entries.accept(PeripheralModMain.portalHelperBlockItem);
            })
            .build();
    
    @Environment(EnvType.CLIENT)
    public static void initClient() {
        IPOuterClientMisc.initClient();
        
        PortalWandItem.initClient();
        
        ClientPortalWandPortalDrag.init();
    }
    
    public static void init() {
        IntrinsicPortalGeneration.init();
        
        DimStackManagement.init();
        
        DimensionAPI.suppressExperimentalWarningForNamespace("immersive_portals");
        
        PortalWandItem.init();
        
        CommandStickItem.init();
        
        PortalWandInteraction.init();
        
        CommandStickItem.registerCommandStickTypes();
        
    }
    
    public static void registerItems(BiConsumer<Identifier, Item> regFunc) {
        regFunc.accept(
            McHelper.newResourceLocation("immersive_portals", "portal_helper"),
            portalHelperBlockItem
        );
        
        regFunc.accept(
            McHelper.newResourceLocation("immersive_portals:command_stick"),
            CommandStickItem.instance
        );
        
        regFunc.accept(
            McHelper.newResourceLocation("immersive_portals:portal_wand"),
            PortalWandItem.instance
        );
    }
    
    public static void registerBlocks(BiConsumer<Identifier, Block> regFunc) {
        regFunc.accept(
            McHelper.newResourceLocation("immersive_portals", "portal_helper"),
            portalHelperBlock
        );
    }
    
    public static void registerChunkGenerators(
        BiConsumer<Identifier, MapCodec<? extends ChunkGenerator>> regFunc
    ) {
        // alternate dimension generators are not ported yet
    }
    
    public static void registerBiomeSources(
        BiConsumer<Identifier, MapCodec<? extends BiomeSource>> regFunc
    ) {
        // alternate dimension biome sources are not ported yet
    }
    
    public static void registerCreativeTabs(
        BiConsumer<Identifier, CreativeModeTab> regFunc
    ) {
        regFunc.accept(
            McHelper.newResourceLocation("immersive_portals", "general"),
            TAB
        );
    }
}
