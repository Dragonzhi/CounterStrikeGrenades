package club.pisquad.minecraft.csgrenades.init

// import club.pisquad.minecraft.csgrenades.CounterStrikeGrenades.Companion.MOD_ID
import club.pisquad.minecraft.csgrenades.registry.ModItems
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.RegistryObject
import net.minecraftforge.versions.forge.ForgeVersion.MOD_ID

object ModCreativeTabs {

    val TABS: DeferredRegister<CreativeModeTab> = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID)

    val CSGRENADES_TAB: RegistryObject<CreativeModeTab> = TABS.register("csgrenades_tab") {
        CreativeModeTab.builder()
            .icon { ModItems.HEGRENADE_ITEM.get().defaultInstance }
            .title(Component.translatable("itemGroup.csgrenades"))
            .displayItems { _, output ->
                output.accept(ModItems.HEGRENADE_ITEM.get())
                output.accept(ModItems.SMOKE_GRENADE_ITEM.get())
                output.accept(ModItems.FLASH_BANG_ITEM.get())
                output.accept(ModItems.DECOY_GRENADE_ITEM.get())
                output.accept(ModItems.MOLOTOV_ITEM.get())
                output.accept(ModItems.INCENDIARY_ITEM.get())
            }
            .build()
    }
}
